package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.McBridge;
import xyz.nativelaunch.ui.UiRuntime;

/**
 * Key helpers: live state and short names for bound keys (codes 0 - 7 are mouse buttons).
 *
 * The codes themselves are the GLFW ones Minecraft still uses in its key bindings, but nothing here touches
 * {@code org.lwjgl.glfw.GLFW}: 26.3 replaced GLFW with SDL and the class is gone there. The live state comes
 * from the version bridge when it can poll the window, otherwise from the key events the UI already sees.
 */
public final class Keys {
	/** Fallback key state, fed by the input events (works on every version, including SDL). */
	private static final java.util.BitSet DOWN = new java.util.BitSet();
	private static final int MAX_CODE = 1024;

	private Keys() {
	}

	/** Called for every key / mouse event the UI sees: keeps the fallback state in step. action: 1 press, 2 repeat, 0 release. */
	public static void track(int code, int action) {
		if (code < 0 || code >= MAX_CODE) {
			return;
		}
		synchronized (DOWN) {
			DOWN.set(code, action != 0);
		}
	}

	/** Window lost focus: nothing can be held down any more. */
	public static void clearTracked() {
		synchronized (DOWN) {
			DOWN.clear();
		}
	}

	private static boolean tracked(int code) {
		if (code < 0 || code >= MAX_CODE) {
			return false;
		}
		synchronized (DOWN) {
			return DOWN.get(code);
		}
	}

	private static McBridge bridge() {
		try {
			return UiRuntime.mc();
		} catch (Throwable t) {
			return null;
		}
	}

	/** The window argument is kept for callers; the bridge owns the window now. */
	public static boolean isDown(long window, int code) {
		try {
			if (code < 0) {
				return false;
			}
			McBridge mc = bridge();
			int state = mc == null ? -1 : mc.keyState(code);
			if (state >= 0) {
				return state == 1;
			}
			return tracked(code);
		} catch (Throwable t) {
			return false;
		}
	}

	/** Short label for a key (W, LMB, Space, LShift ...). */
	public static String name(int code) {
		switch (code) {
			case -1: return "None";
			case 0: return "LMB";
			case 1: return "RMB";
			case 2: return "MMB";
			case 3: return "M4";
			case 4: return "M5";
			case 32: return "Space";
			case 256: return "Esc";
			case 257: return "Enter";
			case 258: return "Tab";
			case 259: return "Backspace";
			case 280: return "Caps";
			case 340: return "LShift";
			case 341: return "LCtrl";
			case 342: return "LAlt";
			case 344: return "RShift";
			case 345: return "RCtrl";
			case 346: return "RAlt";
			case 262: return "Right";
			case 263: return "Left";
			case 264: return "Down";
			case 265: return "Up";
			default:
				break;
		}
		if (code >= 290 && code <= 314) {
			return "F" + (code - 289);
		}
		if (code >= 320 && code <= 329) {
			return "Num " + (code - 320);
		}
		if (code >= 65 && code <= 90 || code >= 48 && code <= 57) {
			return String.valueOf((char) code);
		}
		try {
			McBridge mc = bridge();
			String n = mc == null ? null : mc.keyLabel(code);
			if (n != null && !n.isEmpty()) {
				return n.toUpperCase(java.util.Locale.ROOT);
			}
		} catch (Throwable ignored) {
			// fall through
		}
		return "Key " + code;
	}
}
