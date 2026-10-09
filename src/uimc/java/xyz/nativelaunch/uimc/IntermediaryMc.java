package xyz.nativelaunch.uimc;

import net.minecraft.class_1041;
import net.minecraft.class_1074;
import net.minecraft.class_310;
import net.minecraft.class_429;
import net.minecraft.class_4325;
import net.minecraft.class_437;
import net.minecraft.class_442;
import net.minecraft.class_500;
import net.minecraft.class_526;
import xyz.nativelaunch.ui.McBridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** McBridge for Minecraft 1.16 - 1.21.11, written against intermediary-named stubs. */
public final class IntermediaryMc implements McBridge {
	private final String version;
	private Constructor<?> modsScreen;
	private boolean modsLooked;

	public IntermediaryMc(String version) {
		this.version = version == null ? "" : version;
	}

	private static class_310 client() {
		return class_310.method_1551();
	}

	@Override
	public boolean ready() {
		class_310 c = client();
		return c != null && c.method_22683() != null;
	}

	@Override
	public Object screen() {
		return client().field_1755;
	}

	@Override
	public boolean isVanillaTitle(Object screen) {
		return screen instanceof class_442;
	}

	@Override
	public int hostKind(Object screen) {
		return screen instanceof NativeHostScreen ? ((NativeHostScreen) screen).kind : 0;
	}

	@Override
	public Object hostParent(Object screen) {
		return screen instanceof NativeHostScreen ? ((NativeHostScreen) screen).parent : null;
	}

	@Override
	public Object newHost(int kind, Object parent) {
		return new NativeHostScreen(kind, parent instanceof class_437 ? (class_437) parent : null);
	}

	@Override
	public Object newVanillaTitle() {
		return new class_442();
	}

	@Override
	public void setScreen(Object screen) {
		client().method_1507((class_437) screen);
	}

	@Override
	public boolean open(String action, Object parent) {
		class_437 p = parent instanceof class_437 ? (class_437) parent : null;
		class_437 next;
		if ("singleplayer".equals(action)) {
			next = new class_526(p);
		} else if ("multiplayer".equals(action)) {
			next = new class_500(p);
		} else if ("realms".equals(action)) {
			next = new class_4325(p);
		} else if ("options".equals(action)) {
			next = new class_429(p, client().field_1690);
		} else if ("mods".equals(action)) {
			Constructor<?> ctor = mods();
			if (ctor == null) {
				return false;
			}
			try {
				next = (class_437) ctor.newInstance(p);
			} catch (Throwable t) {
				return false;
			}
		} else {
			return false;
		}
		setScreen(next);
		return true;
	}

	private Constructor<?> mods() {
		if (!modsLooked) {
			modsLooked = true;
			for (String name : new String[] {"com.terraformersmc.modmenu.gui.ModsScreen", "io.github.prospector.modmenu.gui.ModsScreen"}) {
				try {
					Class<?> cls = Class.forName(name);
					for (Constructor<?> c : cls.getConstructors()) {
						if (c.getParameterTypes().length == 1 && c.getParameterTypes()[0].isAssignableFrom(class_437.class)) {
							modsScreen = c;
							break;
						}
					}
					if (modsScreen != null) {
						break;
					}
				} catch (Throwable ignored) {
					// not installed
				}
			}
		}
		return modsScreen;
	}

	@Override
	public boolean hasMods() {
		return mods() != null;
	}

	@Override
	public void quit() {
		client().method_1592();
	}

	@Override
	public boolean inWorld() {
		return client().field_1687 != null;
	}

	@Override
	public boolean overlay() {
		return client().method_18506() != null;
	}

	private static class_1041 win() {
		return client().method_22683();
	}

	@Override
	public long window() {
		return win().method_4490();
	}

	@Override
	public int fbWidth() {
		return win().method_4489();
	}

	@Override
	public int fbHeight() {
		return win().method_4506();
	}

	@Override
	public int windowWidth() {
		return win().method_4480();
	}

	@Override
	public int windowHeight() {
		return win().method_4507();
	}

	@Override
	public String tr(String key, String fallback) {
		try {
			String s = class_1074.method_4662(key);
			return s == null || s.equals(key) ? fallback : s;
		} catch (Throwable t) {
			return fallback;
		}
	}

	@Override
	public String version() {
		return version;
	}

	// ── client mods: everything below goes through reflection with intermediary names (stable 1.16 - 1.21.11,
	//    checked against Yarn for 1.16.5, 1.17.1, 1.20.1, 1.20.4, 1.21.1, 1.21.4, 1.21.9 and 1.21.11) ──────────

	private static final Object MISSING = new Object();
	private final Map<String, Object> members = new HashMap<String, Object>();

	private Field field(Class<?> owner, String name) {
		String key = owner.getName() + "#" + name;
		Object m = members.get(key);
		if (m == null) {
			m = MISSING;
			for (Class<?> c = owner; c != null && m == MISSING; c = c.getSuperclass()) {
				try {
					Field f = c.getDeclaredField(name);
					f.setAccessible(true);
					m = f;
				} catch (Throwable ignored) {
					// keep looking up the hierarchy
				}
			}
			members.put(key, m);
		}
		return m == MISSING ? null : (Field) m;
	}

	private Method method(Class<?> owner, String name, Class<?>... params) {
		String key = owner.getName() + "." + name + params.length;
		Object m = members.get(key);
		if (m == null) {
			m = MISSING;
			try {
				Method found = owner.getMethod(name, params);
				found.setAccessible(true);
				m = found;
			} catch (Throwable notPublic) {
				for (Class<?> c = owner; c != null && m == MISSING; c = c.getSuperclass()) {
					try {
						Method found = c.getDeclaredMethod(name, params);
						found.setAccessible(true);
						m = found;
					} catch (Throwable ignored) {
						// keep looking
					}
				}
			}
			members.put(key, m);
		}
		return m == MISSING ? null : (Method) m;
	}

	private Object get(Object target, String name) {
		if (target == null) {
			return null;
		}
		Field f = field(target.getClass(), name);
		try {
			return f == null ? null : f.get(target);
		} catch (Throwable t) {
			return null;
		}
	}

	private Object call(Object target, String name) {
		if (target == null) {
			return null;
		}
		Method m = method(target.getClass(), name);
		try {
			return m == null ? null : m.invoke(target);
		} catch (Throwable t) {
			return null;
		}
	}

	private Object options() {
		return client().field_1690;
	}

	private Object localPlayer() {
		return get(client(), "field_1724");
	}

	@Override
	public boolean isChat(Object screen) {
		return screen != null && "net.minecraft.class_408".equals(screen.getClass().getName())
				|| screen != null && screen.getClass().getSuperclass() != null && "net.minecraft.class_408".equals(screen.getClass().getSuperclass().getName());
	}

	@Override
	public boolean player(double[] out) {
		Object p = localPlayer();
		if (p == null) {
			return false;
		}
		Object x = call(p, "method_23317"), y = call(p, "method_23318"), z = call(p, "method_23321");
		if (!(x instanceof Double) || !(y instanceof Double) || !(z instanceof Double)) {
			return false;
		}
		out[0] = (Double) x;
		out[1] = (Double) y;
		out[2] = (Double) z;
		Object yaw = call(p, "method_36454"), pitch = call(p, "method_36455");
		if (!(yaw instanceof Float)) {
			yaw = get(p, "field_6031"); // 1.16
		}
		if (!(pitch instanceof Float)) {
			pitch = get(p, "field_5965");
		}
		out[3] = yaw instanceof Float ? (Float) yaw : 0;
		out[4] = pitch instanceof Float ? (Float) pitch : 0;
		return true;
	}

	@Override
	public int ping() {
		Object p = localPlayer();
		Object handler = call(client(), "method_1562");
		if (p == null || handler == null) {
			return -1;
		}
		Object id = call(p, "method_5667");
		Method entry = method(handler.getClass(), "method_2871", UUID.class);
		try {
			Object e = entry == null || !(id instanceof UUID) ? null : entry.invoke(handler, id);
			Object latency = call(e, "method_2959");
			return latency instanceof Integer ? (Integer) latency : -1;
		} catch (Throwable t) {
			return -1;
		}
	}

	@Override
	public String server() {
		Object info = call(client(), "method_1558");
		Object address = get(info, "field_3761");
		if (address instanceof String && !((String) address).isEmpty()) {
			return (String) address;
		}
		Object single = call(client(), "method_1542");
		return Boolean.TRUE.equals(single) ? "Singleplayer" : null;
	}

	@Override
	public boolean hudHidden() {
		return Boolean.TRUE.equals(get(options(), "field_1842"));
	}

	@Override
	public boolean debugOpen() {
		Object old = get(options(), "field_1866"); // <= 1.20.1
		if (old instanceof Boolean) {
			return (Boolean) old;
		}
		return Boolean.TRUE.equals(call(call(client(), "method_53526"), "method_53536"));
	}

	private static String keyField(String control) {
		switch (control) {
			case "forward": return "field_1894";
			case "left": return "field_1913";
			case "back": return "field_1881";
			case "right": return "field_1849";
			case "jump": return "field_1903";
			case "sneak": return "field_1832";
			case "sprint": return "field_1867";
			case "attack": return "field_1886";
			case "use": return "field_1904";
			default: return null;
		}
	}

	@Override
	public int boundKey(String control) {
		String f = keyField(control);
		Object binding = f == null ? null : get(options(), f);
		Object key = get(binding, "field_1655");
		Object code = call(key, "method_1444");
		return code instanceof Integer ? (Integer) code : -1;
	}

	@Override
	public void setPressed(String control, boolean pressed) {
		String f = keyField(control);
		Object binding = f == null ? null : get(options(), f);
		if (binding == null) {
			return;
		}
		Method m = method(binding.getClass(), "method_23481", boolean.class);
		try {
			if (m != null) {
				m.invoke(binding, pressed);
			}
		} catch (Throwable ignored) {
			// leave the key alone
		}
	}

	@Override
	public double gamma() {
		Object options = options();
		Field f = options == null ? null : field(options.getClass(), "field_1840");
		try {
			if (f == null) {
				return Double.NaN;
			}
			if (f.getType() == double.class) {
				return f.getDouble(options); // <= 1.18.2
			}
			Object value = get(f.get(options), "field_37868"); // SimpleOption value, 1.19+
			return value instanceof Double ? (Double) value : Double.NaN;
		} catch (Throwable t) {
			return Double.NaN;
		}
	}

	@Override
	public void setGamma(double value) {
		Object options = options();
		Field f = options == null ? null : field(options.getClass(), "field_1840");
		try {
			if (f == null) {
				return;
			}
			if (f.getType() == double.class) {
				f.setDouble(options, value);
				return;
			}
			Object option = f.get(options);
			Field v = option == null ? null : field(option.getClass(), "field_37868");
			if (v != null) {
				v.set(option, Double.valueOf(value)); // set directly: the slider's validator would clamp 16 to 1
			}
		} catch (Throwable ignored) {
			// leave brightness alone
		}
	}

	@Override
	public boolean smoothCamera() {
		return Boolean.TRUE.equals(get(options(), "field_1914"));
	}

	@Override
	public void setSmoothCamera(boolean on) {
		Object options = options();
		Field f = options == null ? null : field(options.getClass(), "field_1914");
		try {
			if (f != null) {
				f.setBoolean(options, on);
			}
		} catch (Throwable ignored) {
			// leave it
		}
	}

	@Override
	public String username() {
		Object name = call(call(client(), "method_1548"), "method_1676");
		return name instanceof String ? (String) name : null;
	}
}
