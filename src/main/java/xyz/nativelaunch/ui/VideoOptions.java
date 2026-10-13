package xyz.nativelaunch.ui;

import java.lang.reflect.Field;

/** Reads / writes one game option by field name: a plain number, an enum, or an option object holding the value. */
public final class VideoOptions {
	private VideoOptions() {
	}

	private static Field find(Class<?> c, String name) {
		for (; c != null; c = c.getSuperclass()) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f;
			} catch (Throwable ignored) {
				// look in the superclass
			}
		}
		return null;
	}

	/** Enums read as their ordinal; NaN when the option can't be read. */
	public static double read(Object options, String field, String valueField) {
		try {
			Field f = options == null ? null : find(options.getClass(), field);
			if (f == null) {
				return Double.NaN;
			}
			Object v = f.get(options);
			if (!(v instanceof Number) && !(v instanceof Enum) && v != null) {
				Field inner = find(v.getClass(), valueField);
				v = inner == null ? null : inner.get(v);
			}
			if (v instanceof Number) {
				return ((Number) v).doubleValue();
			}
			return v instanceof Enum ? ((Enum<?>) v).ordinal() : Double.NaN;
		} catch (Throwable t) {
			return Double.NaN;
		}
	}

	public static void write(Object options, String field, String valueField, double value) {
		try {
			Field f = options == null ? null : find(options.getClass(), field);
			if (f == null) {
				return;
			}
			Class<?> t = f.getType();
			if (t == double.class) {
				f.setDouble(options, value);
			} else if (t == float.class) {
				f.setFloat(options, (float) value);
			} else if (t.isEnum()) {
				f.set(options, constant(t, value));
			} else {
				Object holder = f.get(options);
				Field inner = holder == null ? null : find(holder.getClass(), valueField);
				if (inner == null) {
					return;
				}
				Object old = inner.get(holder);
				if (old instanceof Enum) {
					inner.set(holder, constant(((Enum<?>) old).getDeclaringClass(), value));
				} else if (old instanceof Float) {
					inner.set(holder, Float.valueOf((float) value));
				} else if (old instanceof Double) {
					inner.set(holder, Double.valueOf(value));
				} else if (old instanceof Integer) {
					inner.set(holder, Integer.valueOf((int) Math.round(value)));
				}
			}
		} catch (Throwable ignored) {
			// leave the option alone
		}
	}

	private static Object constant(Class<?> type, double value) {
		Object[] all = type.getEnumConstants();
		return all[Math.max(0, Math.min(all.length - 1, (int) Math.round(value)))];
	}
}
