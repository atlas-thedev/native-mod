package xyz.nativelaunch.health;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Things the game tells us about, from the per-version hooks: who the player hit, who hit the player. */
public final class HealthEvents {
	/** Entities the player hit recently, in {@link #tick()} time. */
	public static final HitMemory HITS = new HitMemory();
	private static final Map<Class<?>, Object> ID_METHODS = new HashMap<Class<?>, Object>();
	private static final Object NONE = new Object();

	private HealthEvents() {
	}

	/** 20 per second, from the wall clock (so it also runs while the game is paused on a screen). */
	public static long tick() {
		return System.currentTimeMillis() / 50L;
	}

	/** The player attacked this entity (any game object that has an id). */
	public static void attacked(Object entity) {
		HealthSettings s = HealthSettings.cur;
		if (!s.enabled || entity == null) {
			return;
		}
		int id = idOf(entity);
		if (id >= 0) {
			attackedId(id);
		}
	}

	public static void attackedId(int id) {
		HealthSettings s = HealthSettings.cur;
		if (s.enabled) {
			HITS.hit(id, tick(), Math.max(1, s.timeAfterHit) * 20);
		}
	}

	/** The player was hurt by something standing at x, z. */
	public static void hurtBy(double x, double z) {
		if (HealthSettings.cur.enabled && HealthSettings.cur.arc) {
			DamageArc.mark(x, z, System.nanoTime());
		}
	}

	private static synchronized int idOf(Object entity) {
		Class<?> c = entity.getClass();
		Object m = ID_METHODS.get(c);
		if (m == null) {
			m = NONE;
			for (String name : new String[] {"method_5628", "getId"}) { // intermediary, official
				try {
					Method found = c.getMethod(name);
					if (found.getReturnType() == int.class) {
						found.setAccessible(true);
						m = found;
						break;
					}
				} catch (Throwable ignored) {
					// try the next name
				}
			}
			ID_METHODS.put(c, m);
		}
		try {
			return m == NONE ? -1 : (Integer) ((Method) m).invoke(entity);
		} catch (Throwable t) {
			return -1;
		}
	}
}
