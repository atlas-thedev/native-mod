package xyz.nativelaunch.cosmetic;

import xyz.nativelaunch.core.Log;
import xyz.nativelaunch.core.MethodCache;
import xyz.nativelaunch.core.NativeState;
import xyz.nativelaunch.core.ProfileAccess;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * The Native logo in front of the name of every player who plays from Native, on name tags and in the tab list.
 *
 * The logo is a glyph (U+10FFFD) of the default and uniform fonts, added by this mod's resources (assets/minecraft/font/default.json),
 * so it works as plain text in every Minecraft version. The game's Text classes change between versions and are not
 * compiled against here, so the one Text this needs ("logo " + the original name) is built reflectively; any
 * failure just leaves the original name untouched.
 */
public final class NameTags {
	/**
	 * U+10FFFD: the last code point of the last private-use plane. Server resource packs put their own rank icons
	 * at U+E000 and up (and newer Minecraft fills that range too), which turned the logo into their icon or a box.
	 */
	public static final String GLYPH = new String(Character.toChars(0x10FFFD));
	private static final String PREFIX = GLYPH + " ";

	private static final String[] PLAYER_CLASSES = {"net.minecraft.class_742", "net.minecraft.client.player.AbstractClientPlayer"};
	private static final MethodCache GAME_PROFILE_GETTERS = new MethodCache("method_7334", "getGameProfile");
	private static final MethodCache ENTRY_PROFILE_GETTERS = new MethodCache("method_2966", "getProfile");

	private static final ClassValue<Boolean> IS_PLAYER = new ClassValue<Boolean>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			for (Class<?> c = type; c != null; c = c.getSuperclass()) {
				for (String name : PLAYER_CLASSES) {
					if (c.getName().equals(name)) {
						return Boolean.TRUE;
					}
				}
			}
			return Boolean.FALSE;
		}
	};

	/** The last Text built on this thread: a hook that runs twice on the same value (Entity and Player overrides) adds one logo. */
	private static final ThreadLocal<Object> LAST = new ThreadLocal<Object>();
	private static volatile boolean broken;
	private static volatile boolean resolved;
	private static Method literalFactory;
	private static Constructor<?> literalCtor;

	private NameTags() {
	}

	/** A player entity's name tag text, with the logo in front when that player plays from Native. */
	public static Object nameplate(Object entity, Object text) {
		if (broken || entity == null || text == null || text == LAST.get() || !IS_PLAYER.get(entity.getClass())) {
			return text;
		}
		try {
			Object profile = GAME_PROFILE_GETTERS.call(entity);
			return decorate(text, ProfileAccess.name(profile), ProfileAccess.id(profile));
		} catch (Throwable t) {
			fail(t);
			return text;
		}
	}

	/** A tab list row's name, with the logo in front when that player plays from Native. */
	public static Object tab(Object entry, Object text) {
		if (broken || entry == null || text == null || text == LAST.get()) {
			return text;
		}
		try {
			Object profile = ENTRY_PROFILE_GETTERS.call(entry);
			return decorate(text, ProfileAccess.name(profile), ProfileAccess.id(profile));
		} catch (Throwable t) {
			fail(t);
			return text;
		}
	}

	/**
	 * The logo glyph comes from this mod's font file, which only reaches the game when a resource loader adds mod
	 * assets (Fabric API / QSL). Without one the glyph would draw as a box, so the name is left as it is.
	 */
	private static volatile Boolean fontReady;

	private static boolean fontReady() {
		Boolean ready = fontReady;
		if (ready == null) {
			ready = Boolean.FALSE;
			try {
				Class<?> loader = Class.forName("net.fabricmc.loader.api.FabricLoader");
				Object instance = loader.getMethod("getInstance").invoke(null);
				Method loaded = loader.getMethod("isModLoaded", String.class);
				for (String id : new String[] {"fabric-resource-loader-v0", "fabric-api", "quilted_fabric_api", "quilt_resource_loader"}) {
					if (Boolean.TRUE.equals(loaded.invoke(instance, id))) {
						ready = Boolean.TRUE;
						break;
					}
				}
			} catch (Throwable ignored) {
				// not Fabric/Quilt: no way to know, keep the plain name
			}
			if (!ready) {
				Log.info("Native logo on name tags is off: Fabric API is not installed, so the logo font can't load.");
			}
			fontReady = ready;
		}
		return ready;
	}

	/** The Text class of the logo (always the same one in a running game); the append lookup below is made against it. */
	private static volatile Class<?> logoClass;
	private static final Method NO_APPEND;

	static {
		try {
			NO_APPEND = Object.class.getMethod("hashCode");
		} catch (NoSuchMethodException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	/** Text.append(Text) for a given kind of name text, found once per class instead of scanning every method per call. */
	private static final ClassValue<Method> APPEND = new ClassValue<Method>() {
		@Override
		protected Method computeValue(Class<?> textType) {
			Class<?> owner = logoClass;
			if (owner != null) {
				for (Method m : owner.getMethods()) {
					if (m.getParameterTypes().length == 1 && (m.getName().equals("method_10852") || m.getName().equals("append"))
							&& m.getParameterTypes()[0] != String.class && m.getParameterTypes()[0].isAssignableFrom(textType)) {
						return m;
					}
				}
			}
			return NO_APPEND; // none (ClassValue results are kept, so a miss is remembered as a sentinel)
		}
	};

	private static Object decorate(Object text, String name, UUID id) throws Exception {
		if (name == null || !fontReady() || !NativeState.get().directory().has(name, id)) {
			return text;
		}
		resolve();
		Object logo = literalFactory != null ? literalFactory.invoke(null, PREFIX) : literalCtor.newInstance(PREFIX);
		logoClass = logo.getClass();
		Method append = APPEND.get(text.getClass());
		if (append == null || append == NO_APPEND) {
			return text;
		}
		Object out = append.invoke(logo, text);
		LAST.set(out);
		return out;
	}

	private static synchronized void resolve() throws Exception {
		if (resolved) {
			return;
		}
		// 1.19+: Text.literal(String); 1.16 - 1.18: new LiteralText(String); 26.x: Component.literal(String)
		for (String holder : new String[] {"net.minecraft.class_2561", "net.minecraft.network.chat.Component"}) {
			for (String factory : new String[] {"method_43470", "literal"}) {
				try {
					literalFactory = Class.forName(holder).getMethod(factory, String.class);
					resolved = true;
					return;
				} catch (ReflectiveOperationException ignored) {
					// next candidate
				}
			}
		}
		literalCtor = Class.forName("net.minecraft.class_2585").getConstructor(String.class);
		resolved = true;
	}

	private static void fail(Throwable t) {
		broken = true;
		Log.warn("Native name tags are off ({}).", t.toString());
	}
}
