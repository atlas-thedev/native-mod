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

	public static String name(Object profile) {
		Object value = call(profile, "name", "getName");
		return value instanceof String ? (String) value : null;
	}

	public static UUID id(Object profile) {
		Object value = call(profile, "id", "getId");
		return value instanceof UUID ? (UUID) value : null;
	}

	public static String propertyValue(Object property) {
		Object value = call(property, "value", "getValue");
		return value instanceof String ? (String) value : null;
	}

	/** The property's signature, or null when it is unsigned. */
	public static String propertySignature(Object property) {
		Object value = call(property, "signature", "getSignature");
		return value instanceof String && !((String) value).isEmpty() ? (String) value : null;
	}

	private static Object call(Object target, String first, String second) {
		if (target == null) {
			return null;
		}
		for (String methodName : new String[] {first, second}) {
			try {
				Method method = target.getClass().getMethod(methodName);
				return method.invoke(target);
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				// try the other spelling
			}
		}
		return null;
	}
}
