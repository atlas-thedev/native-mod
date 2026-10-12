package xyz.nativelaunch.uimc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import xyz.nativelaunch.health.HealthScene;

/**
 * Fills the health scene on Minecraft 1.16 - 1.21.11. Everything goes through reflection with intermediary names that
 * were checked against the Fabric intermediary mappings of 1.16.5, 1.17.1, 1.18.2, 1.19.2, 1.19.4, 1.20.1, 1.20.4,
 * 1.20.6, 1.21.1, 1.21.4, 1.21.8 and 1.21.11 (the names exist in every one of them). Members are looked up once per
 * entity class, then called directly.
 */
final class HealthReaderMc {
	private static final long LOS_MS = 150;
	private final IntermediaryMc mc;

	private Class<?> living, player, mob, monster, entity;
	private final Map<Class<?>, Acc> accessors = new HashMap<Class<?>, Acc>();
	private final IdentityHashMap<Object, String> typeIds = new IdentityHashMap<Object, String>();
	private final Map<Integer, long[]> sight = new HashMap<Integer, long[]>();
	private long frame;
	private int lastHurt;
	private Method canSee;
	private Class<?> meClass;
	private Field hurtTime;

	HealthReaderMc(IntermediaryMc mc) {
		this.mc = mc;
	}

	/** The members of one entity class. */
	private static final class Acc {
		Method id, type, alive, spectator, invisibleTo, width, height, x, y, z, name, customName, customNameVisible;
		Method health, max, absorb, armor, effects, frozen;
		Field px, py, pz;
	}

	private void classes() throws Exception {
		if (living != null) {
			return;
		}
		entity = Class.forName("net.minecraft.class_1297");
		living = Class.forName("net.minecraft.class_1309");
		player = Class.forName("net.minecraft.class_1657");
		mob = Class.forName("net.minecraft.class_1308");
		monster = Class.forName("net.minecraft.class_1569");
	}

	private Acc acc(Class<?> c) {
		Acc a = accessors.get(c);
		if (a == null) {
			a = new Acc();
			a.id = mc.method(c, "method_5628");
			a.type = mc.method(c, "method_5864");
			a.alive = mc.method(c, "method_5805");
			a.spectator = mc.method(c, "method_7325");
			a.invisibleTo = mc.named(c, "method_5756", 1);
			a.width = mc.method(c, "method_17681");
			a.height = mc.method(c, "method_17682");
			a.x = mc.method(c, "method_23317");
			a.y = mc.method(c, "method_23318");
			a.z = mc.method(c, "method_23321");
			a.px = mc.field(c, "field_6014");
			a.py = mc.field(c, "field_6036");
			a.pz = mc.field(c, "field_5969");
			a.name = mc.method(c, "method_5477");
			a.customName = mc.method(c, "method_16914");
			a.customNameVisible = mc.method(c, "method_5807");
			a.health = mc.method(c, "method_6032");
			a.max = mc.method(c, "method_6063");
			a.absorb = mc.method(c, "method_6067");
			a.armor = mc.method(c, "method_6096");
			a.effects = mc.method(c, "method_6088");
			a.frozen = mc.method(c, "method_32314"); // 1.17+
			accessors.put(c, a);
		}
		return a;
	}

	private static Object call(Method m, Object o) {
		try {
			return m == null ? null : m.invoke(o);
		} catch (Throwable t) {
			return null;
		}
	}

	private static double num(Object v, double def) {
		return v instanceof Number ? ((Number) v).doubleValue() : def;
	}

	private static double fieldNum(Field f, Object o, double def) {
		try {
			return f == null ? def : ((Number) f.get(o)).doubleValue();
		} catch (Throwable t) {
			return def;
		}
	}

	private String typeId(Object type) {
		if (type == null) {
			return "";
		}
		String id = typeIds.get(type);
		if (id == null) {
			Object key = mc.call(type, "method_5882"); // "entity.minecraft.zombie"
			id = key instanceof String ? (String) key : "";
			if (id.startsWith("entity.")) {
				id = id.substring(7);
				int dot = id.indexOf('.');
				if (dot > 0) {
					id = id.substring(0, dot) + ":" + id.substring(dot + 1);
				}
			}
			typeIds.put(type, id);
		}
		return id;
	}

	boolean read(HealthScene out) throws Exception {
		classes();
		Object client = IntermediaryMc.client();
		Object world = mc.get(client, "field_1687");
		Object me = mc.get(client, "field_1724");
		if (world == null || me == null) {
			return false;
		}
		Object camera = mc.call(mc.get(client, "field_1773"), "method_19418");
		Object pos = mc.get(camera, "field_18712");
		if (camera == null || pos == null) {
			return false;
		}
		out.camX = num(mc.get(pos, "field_1352"), 0);
		out.camY = num(mc.get(pos, "field_1351"), 0);
		out.camZ = num(mc.get(pos, "field_1350"), 0);
		out.camYaw = (float) num(mc.get(camera, "field_18718"), 0);
		out.camPitch = (float) num(mc.get(camera, "field_18717"), 0);
		float pt = mc.partialTick();
		out.fov = mc.fov(camera, pt);
		Object props = mc.call(world, "method_28104");
		out.hardcore = Boolean.TRUE.equals(mc.call(props, "method_152"));

		if (meClass != me.getClass()) {
			meClass = me.getClass();
			canSee = mc.named(meClass, "method_6057", 1);
			hurtTime = mc.field(meClass, "field_6235");
		}
		int hurt = (int) fieldNum(hurtTime, me, 0);
		out.playerHurt = hurt > lastHurt && hurt > 0;
		lastHurt = hurt;

		Object entities = mc.call(world, "method_18112");
		if (!(entities instanceof Iterable)) {
			return false;
		}
		long nowMs = System.currentTimeMillis();
		frame++;
		double maxSq = HealthScene.MAX_DISTANCE * HealthScene.MAX_DISTANCE;
		Acc meAcc = acc(me.getClass());
		Object vehicle = mc.call(me, "method_5854");
		for (Object e : (Iterable<?>) entities) {
			if (e == null || !living.isInstance(e)) {
				continue;
			}
			Acc a = acc(e.getClass());
			double x = num(call(a.x, e), 0), y = num(call(a.y, e), 0), z = num(call(a.z, e), 0);
			double ex = fieldNum(a.px, e, x), ey = fieldNum(a.py, e, y), ez = fieldNum(a.pz, e, z);
			x = ex + (x - ex) * pt;
			y = ey + (y - ey) * pt;
			z = ez + (z - ez) * pt;
			double dx = x - out.camX, dy = y - out.camY, dz = z - out.camZ;
			if (dx * dx + dy * dy + dz * dz > maxSq) {
				continue;
			}
			HealthScene.Entry r = out.add();
			if (r == null) {
				break;
			}
			boolean self = e == me;
			r.id = (int) num(call(a.id, e), -1);
			r.x = x;
			r.y = y;
			r.z = z;
			r.width = (float) num(call(a.width, e), 0.6);
			r.height = (float) num(call(a.height, e), 1.8);
			r.health = (float) num(call(a.health, e), 20);
			r.max = (float) num(call(a.max, e), 20);
			r.absorb = (float) num(call(a.absorb, e), 0);
			r.armor = (int) num(call(a.armor, e), 0);
			r.self = self;
			r.player = player.isInstance(e);
			r.hostile = monster.isInstance(e);
			r.passive = mob.isInstance(e) && !r.hostile;
			r.type = typeId(call(a.type, e));
			if (r.player) {
				Object n = mc.call(call(a.name, e), "getString");
				r.name = n instanceof String ? (String) n : "";
			}
			boolean invisible = false;
			try {
				invisible = a.invisibleTo != null && Boolean.TRUE.equals(a.invisibleTo.invoke(e, me));
			} catch (Throwable ignored) {
				// treat as visible
			}
			r.valid = Boolean.TRUE.equals(call(a.alive, e)) && !Boolean.TRUE.equals(call(a.spectator, e)) && e != vehicle && !invisible;
			r.label = (r.player && !self) || (Boolean.TRUE.equals(call(a.customName, e)) && Boolean.TRUE.equals(call(a.customNameVisible, e)));
			if (!r.valid) {
				continue;
			}
			effects(a, e, r);
			r.visible = self || canSee(me, e, nowMs);
		}
		if ((frame & 255L) == 0L) {
			for (Iterator<long[]> it = sight.values().iterator(); it.hasNext();) {
				if (frame - it.next()[2] > 256L) {
					it.remove();
				}
			}
		}
		out.ok = true;
		return true;
	}

	private void effects(Acc a, Object e, HealthScene.Entry r) {
		boolean poison = false, wither = false;
		Object map = call(a.effects, e);
		if (map instanceof Map && !((Map<?, ?>) map).isEmpty()) {
			for (Object inst : ((Map<?, ?>) map).values()) {
				Object key = mc.call(inst, "method_5586"); // "effect.minecraft.regeneration"
				if (!(key instanceof String)) {
					continue;
				}
				String k = (String) key;
				if (k.endsWith(".regeneration")) {
					r.regen = true;
				} else if (k.endsWith(".poison")) {
					poison = true;
				} else if (k.endsWith(".wither")) {
					wither = true;
				}
			}
		}
		boolean frozen = Boolean.TRUE.equals(call(a.frozen, e));
		r.kind = wither ? 2 : poison ? 1 : frozen ? 3 : 0;
	}

	private boolean canSee(Object me, Object e, long nowMs) {
		int id;
		try {
			id = (int) num(call(acc(e.getClass()).id, e), -1);
		} catch (Throwable t) {
			return true;
		}
		long[] s = sight.get(id);
		if (s == null) {
			s = new long[3];
			s[0] = -1;
			sight.put(id, s);
		}
		s[2] = frame;
		if (nowMs - s[0] >= LOS_MS) {
			s[0] = nowMs;
			boolean ok = true;
			try {
				ok = canSee == null || Boolean.TRUE.equals(canSee.invoke(me, e));
			} catch (Throwable ignored) {
				// no line-of-sight test on this version: show it
			}
			s[1] = ok ? 1 : 0;
		}
		return s[1] == 1;
	}

	String target() {
		Object t = mc.get(IntermediaryMc.client(), "field_1692");
		if (t == null) {
			return null;
		}
		String id = typeId(mc.call(t, "method_5864"));
		return id.isEmpty() ? null : id;
	}

	void say(String text, int rgb, boolean chat) throws Exception {
		Object me = mc.get(IntermediaryMc.client(), "field_1724");
		if (me == null) {
			return;
		}
		String prefix = "";
		if (rgb >= 0) {
			int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255;
			prefix = g > r ? "\u00a7a" : r > g ? "\u00a7c" : "";
		}
		Object component = mc.literal(prefix + text);
		Method send = mc.method(me.getClass(), "method_7353", Class.forName("net.minecraft.class_2561"), boolean.class);
		if (send != null) {
			send.invoke(me, component, !chat);
		}
	}
}
