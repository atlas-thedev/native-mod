package xyz.nativelaunch.ui;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Builds a vanilla screen whose constructor changed shape between versions (Advancements, Statistics, Open to LAN):
 * picks the public constructor whose every parameter can be filled from the parent screen or from one of the given
 * sources (the player, its connection), found by type rather than by name so it works with any mappings.
 */
public final class ScreenFactory {
	private ScreenFactory() {
	}

	public static Object create(String className, Object parent, Class<?> screenType, Object... sources) {
		Class<?> cls;
		try {
			cls = Class.forName(className);
		} catch (Throwable t) {
			return null;
		}
		Constructor<?>[] ctors = cls.getConstructors();
		// most parameters first: newer versions take the parent screen as well
		java.util.Arrays.sort(ctors, (a, b) -> b.getParameterTypes().length - a.getParameterTypes().length);
		for (Constructor<?> ctor : ctors) {
			Class<?>[] types = ctor.getParameterTypes();
			Object[] args = new Object[types.length];
			boolean ok = true;
			for (int i = 0; i < types.length && ok; i++) {
				Class<?> t = types[i];
				if (screenType != null && t.isAssignableFrom(screenType)) {
					args[i] = parent; // may be null: vanilla accepts a null parent
				} else if (t == boolean.class) {
					args[i] = Boolean.FALSE;
				} else {
					args[i] = find(t, sources);
					ok = args[i] != null;
				}
			}
			if (!ok) {
				continue;
			}
			try {
				return ctor.newInstance(args);
			} catch (Throwable ignored) {
				// try the next shape
			}
		}
		return null;
	}

	/** An instance of {@code type}: one of the sources, a field of one, or what a no-argument getter returns. */
	static Object find(Class<?> type, Object... sources) {
		if (type.isPrimitive()) {
			return null;
		}
		for (Object s : sources) {
			if (type.isInstance(s)) {
				return s;
			}
		}
		for (Object s : sources) {
			if (s == null) {
				continue;
			}
			for (Class<?> c = s.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
				for (Method m : c.getDeclaredMethods()) {
					if (m.getParameterCount() == 0 && !Modifier.isStatic(m.getModifiers()) && type.isAssignableFrom(m.getReturnType())
							&& m.getReturnType() != Object.class) {
						try {
							m.setAccessible(true);
							Object v = m.invoke(s);
							if (v != null) {
								return v;
							}
						} catch (Throwable ignored) {
							// keep looking
						}
					}
				}
				for (Field f : c.getDeclaredFields()) {
					if (!Modifier.isStatic(f.getModifiers()) && type.isAssignableFrom(f.getType()) && f.getType() != Object.class) {
						try {
							f.setAccessible(true);
							Object v = f.get(s);
							if (v != null) {
								return v;
							}
						} catch (Throwable ignored) {
							// keep looking
						}
					}
				}
			}
		}
		return null;
	}

	/** Reads a field by name (intermediary names), walking up the class tree; null when missing. */
	public static Object field(Object target, String name) {
		if (target == null) {
			return null;
		}
		for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(target);
			} catch (NoSuchFieldException e) {
				// up
			} catch (Throwable t) {
				return null;
			}
		}
		return null;
	}
}
