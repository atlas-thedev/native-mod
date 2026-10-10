package xyz.nativelaunch.ui;

import java.lang.reflect.Method;

/** Minecraft 26.x: unpacks the game's input event records (KeyEvent, CharacterEvent, MouseButtonInfo) for UiRuntime. */
public final class Input26 {
	private static Method keyM, keyMods, charM, buttonM, buttonMods;

	private Input26() {
	}

	private static int read(Object target, Method m) throws Exception {
		Object v = m.invoke(target);
		return v instanceof Integer ? (Integer) v : 0;
	}

	public static boolean key(int action, Object event) {
		try {
			if (keyM == null) {
				keyM = event.getClass().getMethod("key");
				keyMods = event.getClass().getMethod("modifiers");
			}
			return UiRuntime.onKey(read(event, keyM), action, read(event, keyMods));
		} catch (Throwable t) {
			return false;
		}
	}

	public static boolean character(Object event) {
		try {
			if (charM == null) {
				charM = event.getClass().getMethod("codepoint");
			}
			return UiRuntime.onChar(read(event, charM));
		} catch (Throwable t) {
			return false;
		}
	}

	public static boolean button(Object info, int action) {
		try {
			if (buttonM == null) {
				buttonM = info.getClass().getMethod("button");
				buttonMods = info.getClass().getMethod("modifiers");
			}
			return UiRuntime.onButton(read(info, buttonM), action, read(info, buttonMods));
		} catch (Throwable t) {
			return false;
		}
	}

	public static boolean scroll(double dx, double dy) {
		try {
			return UiRuntime.onScroll(dx, dy);
		} catch (Throwable t) {
			return false;
		}
	}
}
