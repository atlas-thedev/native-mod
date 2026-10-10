package xyz.nativelaunch.ui;

import xyz.nativelaunch.core.Log;

/**
 * The window-level input hook used on the versions that still ship GLFW (Minecraft 1.16 - 26.2).
 *
 * Minecraft 26.3 replaced GLFW with SDL, so {@code org.lwjgl.glfw.GLFW} does not exist there and the only
 * implementation ({@link GlfwInput}) must never be loaded. It is therefore reached reflectively: on 26.3 the
 * bridge reports {@code handlesInput()}, nothing asks for a hook, and the class is never touched.
 */
interface RawInput {
	void install();

	String clipboard();

	void clipboard(String text);

	/** The GLFW hook for this window, or null when this Minecraft has no GLFW. */
	static RawInput open(long window) {
		try {
			Class<?> cls = Class.forName("xyz.nativelaunch.ui.GlfwInput");
			return (RawInput) cls.getDeclaredConstructor(long.class).newInstance(Long.valueOf(window));
		} catch (Throwable t) {
			Log.warn("Window input hook unavailable ({}): using the game's own input.", t.toString());
			return null;
		}
	}
}
