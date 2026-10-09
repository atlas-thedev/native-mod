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
}
