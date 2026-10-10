package xyz.nativelaunch.cosmetic;

import xyz.nativelaunch.core.Log;
import xyz.nativelaunch.core.ProfileAccess;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Hoods, helmets and masks ({@link CosmeticModel#coversHead}) replace what is normally drawn on the head: the
 * skin's hat layer and the vanilla helmet / head item would poke through them. The mixins ask here while a player
 * is being prepared for rendering; everything else (inventory, server, other entities) sees the real values.
 */
public final class HeadCover {
	private static final String[] PLAYER_CLASSES = {"net.minecraft.class_742", "net.minecraft.client.player.AbstractClientPlayer"};
	private static final String[] PROFILE_GETTERS = {"method_7334", "getGameProfile"};
	/** PlayerModelPart.HAT and EquipmentSlot.HEAD: same position in every version since 1.16. */
	private static final int HAT = 6;
	private static final int HEAD = 5;

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

	/** The player whose render state / model is being built on the render thread, or null. */
	private static volatile Object scope;
	private static volatile boolean broken;
	private static Object lastEntity;
	private static long lastAt;
	private static boolean lastCovers;
	private static volatile Object empty;

	private HeadCover() {
	}

	public static boolean any(List<CosmeticLibrary.Loaded> worn) {
		for (int i = 0; i < worn.size(); i++) {
			if (worn.get(i).model.coversHead) {
				return true;
			}
		}
		return false;
	}

	public static void enter(Object entity) {
		scope = entity;
	}

	public static void exit() {
		scope = null;
	}

	/** Player.isModelPartShown: false for the hat layer under a hood. */
	public static boolean hidesPart(Object entity, Object part) {
		return part instanceof Enum && ((Enum<?>) part).ordinal() == HAT && covered(entity);
	}

	/** LivingEntity.getItemBySlot: an empty head slot while that player's render state is built. */
	public static Object equipped(Object entity, Object slot, Object stack) {
		if (entity != scope || stack == null || !(slot instanceof Enum) || ((Enum<?>) slot).ordinal() != HEAD || !covered(entity)) {
			return stack;
		}
		Object none = empty(stack.getClass());
		return none != null ? none : stack;
	}

	private static boolean covered(Object entity) {
		if (broken || entity == null || !IS_PLAYER.get(entity.getClass())) {
			return false;
		}
		synchronized (HeadCover.class) {
			long now = System.nanoTime();
			if (entity == lastEntity && now - lastAt < 100_000_000L) {
				return lastCovers;
			}
			boolean covers = false;
			try {
				Object profile = profile(entity);
				String name = ProfileAccess.name(profile);
				if (name != null) {
					covers = any(CosmeticLibrary.worn(name, ProfileAccess.id(profile)));
				}
			} catch (Throwable t) {
				broken = true;
				Log.warn("Hood helmet hiding is off ({}).", t.toString());
			}
			lastEntity = entity;
			lastAt = now;
			lastCovers = covers;
			return covers;
		}
	}

	private static Object profile(Object entity) throws Exception {
		for (String name : PROFILE_GETTERS) {
			try {
				Method m = entity.getClass().getMethod(name);
				return m.invoke(entity);
			} catch (NoSuchMethodException ignored) {
				// next spelling
			}
		}
		throw new NoSuchMethodException(PROFILE_GETTERS[0]);
	}

	/** ItemStack.EMPTY (field_8037 / EMPTY), found from a stack's class. */
	private static Object empty(Class<?> type) {
		Object none = empty;
		if (none != null) {
			return none;
		}
		for (Class<?> c = type; c != null && none == null; c = c.getSuperclass()) {
			for (Field f : c.getDeclaredFields()) {
				if (Modifier.isStatic(f.getModifiers()) && f.getType() == c && (f.getName().equals("field_8037") || f.getName().equals("EMPTY"))) {
					try {
						f.setAccessible(true);
						none = f.get(null);
					} catch (Throwable ignored) {
						// keep looking
					}
					break;
				}
			}
		}
		empty = none;
		return none;
	}
}
