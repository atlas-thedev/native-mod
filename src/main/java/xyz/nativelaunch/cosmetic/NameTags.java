package xyz.nativelaunch.cosmetic;

import xyz.nativelaunch.core.Log;
import xyz.nativelaunch.core.NativeState;
import xyz.nativelaunch.core.ProfileAccess;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * The Native logo in front of the name of every player who plays from Native, on name tags and in the tab list.
 *
 * The logo is a glyph (U+E000) of the default font, added by this mod's resources (assets/minecraft/font/default.json),
 * so it works as plain text in every Minecraft version. The game's Text classes change between versions and are not
 * compiled against here, so the one Text this needs ("logo " + the original name) is built reflectively; any
 * failure just leaves the original name untouched.
 */
public final class NameTags {
	public static final String GLYPH = "\uE000";
	private static final String PREFIX = GLYPH + " ";

	private static final String[] PLAYER_CLASSES = {"net.minecraft.class_742", "net.minecraft.client.player.AbstractClientPlayer"};
	private static final String[] GAME_PROFILE_GETTERS = {"method_7334", "getGameProfile"};
	private static final String[] ENTRY_PROFILE_GETTERS = {"method_2966", "getProfile"};

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
			Object profile = call(entity, GAME_PROFILE_GETTERS);
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
			Object profile = call(entry, ENTRY_PROFILE_GETTERS);
			return decorate(text, ProfileAccess.name(profile), ProfileAccess.id(profile));
		} catch (Throwable t) {
			fail(t);
			return text;
		}
	}

	private static Object decorate(Object text, String name, UUID id) throws Exception {
		if (name == null || !NativeState.get().directory().has(name, id)) {
			return text;
		}
		resolve();
		Object logo = literalFactory != null ? literalFactory.invoke(null, PREFIX) : literalCtor.newInstance(PREFIX);
		Method append = null;
		for (Method m : logo.getClass().getMethods()) {
			if (m.getParameterTypes().length == 1 && (m.getName().equals("method_10852") || m.getName().equals("append"))
					&& m.getParameterTypes()[0] != String.class && m.getParameterTypes()[0].isAssignableFrom(text.getClass())) {
				append = m;
				break;
			}
		}
		if (append == null) {
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

	private static Object call(Object target, String[] names) throws Exception {
		for (String name : names) {
			try {
				return target.getClass().getMethod(name).invoke(target);
			} catch (NoSuchMethodException ignored) {
				// next spelling
			}
		}
		throw new NoSuchMethodException(names[0]);
	}

	private static void fail(Throwable t) {
		broken = true;
		Log.warn("Native name tags are off ({}).", t.toString());
	}
}
