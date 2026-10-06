package xyz.nativelaunch.cosmetic;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Name-free reflection the per-version renderers share (field names differ between mappings, types do not). */
public final class Reflect {
	private Reflect() {
	}

	/** Every value of every Map field (and every direct field) of {@code owner}, so renderers are found by type. */
	public static List<Object> fieldValues(Object owner) {
		List<Object> out = new ArrayList<Object>();
		if (owner == null) {
			return out;
		}
		for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
			for (Field f : c.getDeclaredFields()) {
				if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) {
					continue;
				}
				try {
					f.setAccessible(true);
					Object value = f.get(owner);
					if (value instanceof Map) {
						out.addAll(((Map<?, ?>) value).values());
					} else if (value instanceof Collection) {
						out.addAll((Collection<?>) value);
					} else if (value != null) {
						out.add(value);
					}
				} catch (Throwable ignored) {
					// inaccessible field: skip
				}
			}
		}
		return out;
	}

	/** The first List field declared by {@code declaring} on {@code owner} (a renderer's feature list), or null. */
	@SuppressWarnings("unchecked")
	public static List<Object> listField(Object owner, Class<?> declaring) {
		for (Field f : declaring.getDeclaredFields()) {
			if (Modifier.isStatic(f.getModifiers()) || f.getType() != List.class) {
				continue;
			}
			try {
				f.setAccessible(true);
				return (List<Object>) f.get(owner);
			} catch (Throwable ignored) {
				return null;
			}
		}
		return null;
	}

	/** Adds {@code feature} unless the list already holds one of the same class. Returns true when added. */
	public static boolean ensure(List<Object> features, Class<?> type, java.util.function.Supplier<Object> factory) {
		if (features == null) {
			return false;
		}
		for (Object f : features) {
			if (f != null && f.getClass() == type) {
				return false;
			}
		}
		try {
			features.add(factory.get());
			return true;
		} catch (UnsupportedOperationException e) {
			return false;
		}
	}
}
