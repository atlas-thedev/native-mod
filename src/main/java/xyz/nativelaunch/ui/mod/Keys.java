package xyz.nativelaunch.ui.mod;

import org.lwjgl.glfw.GLFW;

/** GLFW key helpers: live state and short names for bound keys (codes 0 - 7 are mouse buttons). */
public final class Keys {
	private Keys() {
	}

	public static boolean isDown(long window, int code) {
		try {
			if (code < 0) {
				return false;
			}
			if (code <= 7) {
				return GLFW.glfwGetMouseButton(window, code) == GLFW.GLFW_PRESS;
			}
			return GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
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
			String n = GLFW.glfwGetKeyName(code, 0);
			if (n != null && !n.isEmpty()) {
				return n.toUpperCase(java.util.Locale.ROOT);
			}
		} catch (Throwable ignored) {
			// fall through
		}
		return "Key " + code;
	}
}
