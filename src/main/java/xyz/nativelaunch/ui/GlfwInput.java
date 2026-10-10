package xyz.nativelaunch.ui;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWCharModsCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;

/**
 * Sits in front of Minecraft's own GLFW callbacks: while a Native screen is open its input goes to the Native UI,
 * everything else is passed straight through to the game. Re-checked periodically in case the game re-registers.
 *
 * Only loaded through {@link RawInput#open(long)}, so Minecraft 26.3 (SDL, no {@code org.lwjgl.glfw.GLFW})
 * never links against it.
 */
final class GlfwInput implements RawInput {
	private final long window;
	private GLFWKeyCallback mcKey;
	private GLFWCharCallback mcChar;
	private GLFWCharModsCallback mcCharMods;
	private GLFWMouseButtonCallback mcButton;
	private GLFWCursorPosCallback mcPos;
	private GLFWScrollCallback mcScroll;
	private final GLFWKeyCallback key;
	private final GLFWCharCallback chr;
	private final GLFWCharModsCallback charMods;
	private final GLFWMouseButtonCallback button;
	private final GLFWCursorPosCallback pos;
	private final GLFWScrollCallback scroll;

	GlfwInput(long window) {
		this.window = window;
		key = GLFWKeyCallback.create((w, k, sc, action, mods) -> {
			if (!UiRuntime.onKey(k, action, mods) && mcKey != null) {
				mcKey.invoke(w, k, sc, action, mods);
			}
		});
		chr = GLFWCharCallback.create((w, cp) -> {
			if (!UiRuntime.onChar(cp) && mcChar != null) {
				mcChar.invoke(w, cp);
			}
		});
		charMods = GLFWCharModsCallback.create((w, cp, mods) -> {
			if (!UiRuntime.onChar(cp) && mcCharMods != null) {
				mcCharMods.invoke(w, cp, mods);
			}
		});
		button = GLFWMouseButtonCallback.create((w, b, action, mods) -> {
			if (!UiRuntime.onButton(b, action, mods) && mcButton != null) {
				mcButton.invoke(w, b, action, mods);
			}
		});
		pos = GLFWCursorPosCallback.create((w, x, y) -> {
			UiRuntime.onMove(x, y);
			if (mcPos != null) {
				mcPos.invoke(w, x, y);
			}
		});
		scroll = GLFWScrollCallback.create((w, dx, dy) -> {
			if (!UiRuntime.onScroll(dx, dy) && mcScroll != null) {
				mcScroll.invoke(w, dx, dy);
			}
		});
		install();
	}

	/** (Re)installs our callbacks, adopting whatever the game registered in between. */
	@Override
	public void install() {
		GLFWKeyCallback k = GLFW.glfwSetKeyCallback(window, key);
		if (k != null && k.address() != key.address()) {
			mcKey = k;
		}
		GLFWMouseButtonCallback b = GLFW.glfwSetMouseButtonCallback(window, button);
		if (b != null && b.address() != button.address()) {
			mcButton = b;
		}
		GLFWCursorPosCallback p = GLFW.glfwSetCursorPosCallback(window, pos);
		if (p != null && p.address() != pos.address()) {
			mcPos = p;
		}
		GLFWScrollCallback s = GLFW.glfwSetScrollCallback(window, scroll);
		if (s != null && s.address() != scroll.address()) {
			mcScroll = s;
		}
		// The game uses either the char or the char-mods callback depending on version: wrap the one it uses.
		GLFWCharModsCallback cm = GLFW.glfwSetCharModsCallback(window, charMods);
		if (cm != null && cm.address() != charMods.address()) {
			mcCharMods = cm;
		}
		if (mcCharMods == null) {
			GLFW.glfwSetCharModsCallback(window, null);
			GLFWCharCallback c = GLFW.glfwSetCharCallback(window, chr);
			if (c != null && c.address() != chr.address()) {
				mcChar = c;
			}
		}
	}

	@Override
	public String clipboard() {
		return GLFW.glfwGetClipboardString(window);
	}

	@Override
	public void clipboard(String text) {
		GLFW.glfwSetClipboardString(window, text);
	}
}
