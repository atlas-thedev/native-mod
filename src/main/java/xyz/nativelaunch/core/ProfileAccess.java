package xyz.nativelaunch.core;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * GameProfile and Property changed shape between Minecraft versions (classes with
 * getName()/getId()/getValue() became records with name()/id()/value()), so read
 * them reflectively instead of compiling against one particular shape.
 */
public final class ProfileAccess {
	private ProfileAccess() {
	}

	private static final MethodCache NAME = new MethodCache("name", "getName");
	private static final MethodCache ID = new MethodCache("id", "getId");
	private static final MethodCache VALUE = new MethodCache("value", "getValue");
	private static final MethodCache SIGNATURE = new MethodCache("signature", "getSignature");

	public static String name(Object profile) {
		Object value = call(profile, NAME);
		return value instanceof String ? (String) value : null;
	}

	public static UUID id(Object profile) {
		Object value = call(profile, ID);
		return value instanceof UUID ? (UUID) value : null;
	}

	public static String propertyValue(Object property) {
		Object value = call(property, VALUE);
		return value instanceof String ? (String) value : null;
	}

	/** The property's signature, or null when it is unsigned. */
	public static String propertySignature(Object property) {
		Object value = call(property, SIGNATURE);
		return value instanceof String && !((String) value).isEmpty() ? (String) value : null;
	}

	private static Object call(Object target, MethodCache getters) {
		if (target == null) {
			return null;
		}
		for (Method method : getters.methods(target.getClass())) {
			try {
				return method.invoke(target);
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				// try the other spelling
			}
		}
		return null;
	}
}
