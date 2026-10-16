package xyz.nativelaunch.core;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves public no-argument getters by name once per class, so a hook that runs for every player on every frame
 * never repeats {@code Class.getMethod} (which copies the method and throws {@code NoSuchMethodException} for the
 * spelling the running version does not have).
 */
public final class MethodCache {
	private static final Method[] NONE = new Method[0];
	private final String[] names;
	private final ClassValue<Method[]> found = new ClassValue<Method[]>() {
		@Override
		protected Method[] computeValue(Class<?> type) {
			List<Method> out = new ArrayList<Method>(2);
			for (String name : names) {
				try {
					out.add(type.getMethod(name));
				} catch (NoSuchMethodException | RuntimeException ignored) {
					// this version spells it differently
				}
			}
			return out.isEmpty() ? NONE : out.toArray(new Method[0]);
		}
	};

	/** @param names the spellings to try, in order of preference */
	public MethodCache(String... names) {
		this.names = names.clone();
	}

	/** Every spelling that exists on {@code type}, in the order given (never null). */
	public Method[] methods(Class<?> type) {
		return found.get(type);
	}

	/** Calls the first spelling that exists; {@code NoSuchMethodException} when none does. */
	public Object call(Object target) throws Exception {
		Method[] methods = found.get(target.getClass());
		if (methods.length == 0) {
			throw new NoSuchMethodException(names[0]);
		}
		return methods[0].invoke(target);
	}
}
