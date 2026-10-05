package xyz.nativelaunch.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Makes skin and cape changes show up in a running game, on every supported version.
 *
 * The game remembers each player's skin in their tab-list entry (PlayerInfo) for as long as they are online,
 * so a directory change alone is not enough. When the directory changes, this resets the remembered skin of
 * the affected players on the render thread; the game then asks the (hooked) session service again.
 *
 * Everything is found by type, never by name, so the same code works on intermediary-named (1.16 - 1.21.x)
 * and Mojang-named (26.x) games:
 *   - the connection: the value of Minecraft's no-argument method whose return type holds a Map<UUID, X>
 *     where X has a GameProfile field (ClientPacketListener#playerInfoMap; X is PlayerInfo);
 *   - 1.21.9+: PlayerInfo's lazily created {@code Supplier} skin lookup is cleared;
 *   - 1.20.2 - 1.21.8: the final {@code Supplier} is replaced with a fresh one from PlayerInfo's static
 *     {@code (GameProfile) -> Supplier} factory;
 *   - 1.16 - 1.20.1: the texture map is cleared and the "pending" flag reset, so textures are fetched again.
 * The game's skin caches (Guava LoadingCaches in the skin manager) are invalidated as well.
 */
public final class SkinRefresh {
	private static final Object LOCK = new Object();
	private static final Set<String> pending = new HashSet<String>();
	private static boolean pendingAll;
	private static boolean scheduled;
	private static volatile boolean broken;

	private static Method connectionGetter;
	private static Field infoMapField;
	private static Class<?> infoClass;
	private static boolean resolved;

	private SkinRefresh() {
	}

	static void install(SkinDirectory directory) {
		directory.onChange(new java.util.function.Consumer<SkinEntry>() {
			@Override
			public void accept(SkinEntry entry) {
				request(entry.name);
			}
		});
		directory.onReset(new Runnable() {
			@Override
			public void run() {
				request(null);
			}
		});
	}

	/** @param name player whose look changed, or null for everyone */
	static void request(String name) {
		if (broken) {
			return;
		}
		Executor game = gameExecutor();
		if (game == null) {
			return; // no game yet (or a test): the first lookup will see the new directory anyway
		}
		synchronized (LOCK) {
			if (name == null) {
				pendingAll = true;
			} else {
				pending.add(name.toLowerCase(Locale.ROOT));
			}
			if (scheduled) {
				return;
			}
			scheduled = true;
		}
		try {
			game.execute(new Runnable() {
				@Override
				public void run() {
					runOnGameThread();
				}
			});
		} catch (RuntimeException e) {
			synchronized (LOCK) {
				scheduled = false;
			}
		}
	}

	private static Executor gameExecutor() {
		try {
			Object game = net.fabricmc.loader.api.FabricLoader.getInstance().getGameInstance();
			return game instanceof Executor ? (Executor) game : null;
		} catch (Throwable t) {
			return null;
		}
	}

	private static void runOnGameThread() {
		Set<String> names;
		boolean all;
		synchronized (LOCK) {
			names = new HashSet<String>(pending);
			all = pendingAll;
			pending.clear();
			pendingAll = false;
			scheduled = false;
		}
		try {
			Object game = net.fabricmc.loader.api.FabricLoader.getInstance().getGameInstance();
			if (game == null) {
				return;
			}
			int refreshed = refresh(game, all ? null : names);
			if (refreshed > 0) {
				Log.debug("Refreshed the skin of {} player(s)", refreshed);
			}
		} catch (Throwable t) {
			broken = true; // never retry something that throws every time
			Log.warn("Live skin refresh is unavailable on this version ({}): changes show after rejoining.", t.toString());
		}
	}

	/** Visible for tests. @param names lowercase names, or null for everyone. @return players refreshed */
	static int refresh(Object game, Set<String> names) throws ReflectiveOperationException {
		Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile", false, game.getClass().getClassLoader());
		return refreshWith(game, names, profileClass);
	}

	/** Visible for tests (a stand-in profile class). */
	static int refreshWith(Object game, Set<String> names, Class<?> profileClass) throws ReflectiveOperationException {
		invalidateSkinCaches(game);
		if (!resolve(game.getClass(), profileClass)) {
			return 0;
		}
		Object connection = connectionGetter.invoke(game);
		if (connection == null) {
			return 0;
		}
		Object map = infoMapField.get(connection);
		if (!(map instanceof Map)) {
			return 0;
		}
		List<Object> infos = new ArrayList<Object>(((Map<?, ?>) map).values());
		int count = 0;
		for (Object info : infos) {
			if (info == null || !infoClass.isInstance(info)) {
				continue;
			}
			Object profile = fieldOfType(info, profileClass);
			String name = ProfileAccess.name(profile);
			if (names != null && (name == null || !names.contains(name.toLowerCase(Locale.ROOT)))) {
				continue;
			}
			if (reset(info, profile, profileClass)) {
				count++;
			}
		}
		return count;
	}

	private static synchronized boolean resolve(Class<?> gameClass, Class<?> profileClass) {
		if (resolved && connectionGetter != null && connectionGetter.getDeclaringClass().isAssignableFrom(gameClass)) {
			return true;
		}
		if (resolved && connectionGetter == null) {
			return false;
		}
		connectionGetter = null;
		resolved = true;
		for (Method method : gameClass.getMethods()) {
			if (method.getParameterTypes().length != 0 || method.getReturnType().isPrimitive() || Modifier.isStatic(method.getModifiers())) {
				continue;
			}
			for (Field field : allFields(method.getReturnType())) {
				Class<?> value = uuidMapValue(field);
				if (value != null && hasFieldOfType(value, profileClass)) {
					field.setAccessible(true);
					connectionGetter = method;
					infoMapField = field;
					infoClass = value;
					return true;
				}
			}
		}
		Log.debug("Live skin refresh: no player list found");
		return false;
	}

	/** The X of a {@code Map<UUID, X>} field, or null. */
	private static Class<?> uuidMapValue(Field field) {
		if (!Map.class.isAssignableFrom(field.getType()) || !(field.getGenericType() instanceof ParameterizedType)) {
			return null;
		}
		Type[] args = ((ParameterizedType) field.getGenericType()).getActualTypeArguments();
		return args.length == 2 && args[0] == UUID.class && args[1] instanceof Class ? (Class<?>) args[1] : null;
	}

	/** Forget one player's remembered skin. Returns true when something was reset. */
	static boolean reset(Object info, Object profile, Class<?> profileClass) throws ReflectiveOperationException {
		Field lookup = null;
		for (Field field : allFields(info.getClass())) {
			if (field.getType() == Supplier.class && !Modifier.isStatic(field.getModifiers())) {
				lookup = field;
				break;
			}
		}
		if (lookup != null) {
			lookup.setAccessible(true);
			if (!Modifier.isFinal(lookup.getModifiers())) {
				lookup.set(info, null); // 1.21.9+: created again on the next getSkin()
				return true;
			}
			Method factory = null;
			for (Method method : info.getClass().getDeclaredMethods()) {
				Class<?>[] params = method.getParameterTypes();
				if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == Supplier.class
						&& params.length == 1 && params[0] == profileClass) {
					factory = method;
					break;
				}
			}
			if (factory == null || profile == null) {
				return false;
			}
			factory.setAccessible(true);
			lookup.set(info, factory.invoke(null, profile));
			return true;
		}
		// 1.16 - 1.20.1: Map<Type, ResourceLocation> textureLocations + boolean pendingTextures (+ String skinModel)
		synchronized (info) {
			boolean reset = false;
			Collection<Field> fields = allFields(info.getClass());
			for (Field field : fields) {
				if (Modifier.isStatic(field.getModifiers())) {
					continue;
				}
				field.setAccessible(true);
				if (Map.class.isAssignableFrom(field.getType())) {
					Object value = field.get(info);
					if (value instanceof Map) {
						((Map<?, ?>) value).clear();
						reset = true;
					}
				}
			}
			if (!reset) {
				return false;
			}
			for (Field field : fields) {
				if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
					continue;
				}
				if (field.getType() == boolean.class) {
					field.setBoolean(info, false);
				} else if (field.getType() == String.class) {
					field.set(info, null);
				}
			}
			return true;
		}
	}

	/** Drops the game's per-profile skin caches (skin manager LoadingCaches); downloaded textures stay. */
	private static void invalidateSkinCaches(Object game) {
		for (Field field : allFields(game.getClass())) {
			if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive() || field.getType().getName().startsWith("java.")) {
				continue;
			}
			boolean isSkinManager = false;
			for (Field inner : field.getType().getDeclaredFields()) {
				if (inner.getType().getName().equals("com.google.common.cache.LoadingCache")) {
					isSkinManager = true;
					break;
				}
			}
			if (!isSkinManager || !looksLikeSkinManager(field.getType())) {
				continue;
			}
			try {
				field.setAccessible(true);
				Object manager = field.get(game);
				if (manager == null) {
					continue;
				}
				for (Field inner : field.getType().getDeclaredFields()) {
					if (inner.getType().getName().equals("com.google.common.cache.LoadingCache") && !Modifier.isStatic(inner.getModifiers())) {
						inner.setAccessible(true);
						Object cache = inner.get(manager);
						if (cache != null) {
							cache.getClass().getMethod("invalidateAll").invoke(cache);
						}
					}
				}
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				// best effort: entries also expire on their own
			}
		}
	}

	/** The skin manager is the only game class with a LoadingCache that also talks to the session service. */
	private static boolean looksLikeSkinManager(Class<?> type) {
		for (Field field : type.getDeclaredFields()) {
			String name = field.getType().getName();
			if (name.equals("com.mojang.authlib.minecraft.MinecraftSessionService") || name.equals("com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService")) {
				return true;
			}
			// 26.x keeps the session service behind a Services record; its texture caches are inner classes
			for (Class<?> inner : type.getDeclaredClasses()) {
				for (Field f : inner.getDeclaredFields()) {
					if (f.getType().getName().equals("com.mojang.authlib.minecraft.MinecraftProfileTexture$Type")) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static Object fieldOfType(Object target, Class<?> type) throws IllegalAccessException {
		for (Field field : allFields(target.getClass())) {
			if (field.getType() == type && !Modifier.isStatic(field.getModifiers())) {
				field.setAccessible(true);
				return field.get(target);
			}
		}
		return null;
	}

	private static boolean hasFieldOfType(Class<?> owner, Class<?> type) {
		for (Field field : allFields(owner)) {
			if (field.getType() == type) {
				return true;
			}
		}
		return false;
	}

	private static Collection<Field> allFields(Class<?> type) {
		List<Field> out = new ArrayList<Field>();
		for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
			for (Field field : c.getDeclaredFields()) {
				out.add(field);
			}
		}
		return out;
	}
}
