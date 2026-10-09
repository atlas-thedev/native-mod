package xyz.nativelaunch.ui.mod;

import org.lwjgl.glfw.GLFW;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.UiRuntime;

/** Modules that make the game lighter to run. */
final class PerformanceModules {
	private PerformanceModules() {
	}

	/**
	 * Background FPS: while the window is in the background or minimised the game keeps drawing hundreds of frames nobody sees.
	 * This caps the frame rate there (the sleep happens right before the frame is shown), so the CPU / GPU cool down and
	 * other apps (Discord, the browser, a recording) get the power back. Full speed returns the moment you click the game.
	 */
	static final class BackgroundFps extends Module {
		final Setting.Num unfocused = add(new Setting.Num("unfocused", "FPS in the background", 30, 5, 120, 5, ""));
		final Setting.Num minimized = add(new Setting.Num("minimized", "FPS when minimised", 5, 1, 30, 1, ""));
		private long checkAt, lastFrame;
		private boolean focused = true, iconified;

		BackgroundFps() {
			super("bgfps", "Background FPS", "Lowers the frame rate while the game is in the background or minimised.", PERFORMANCE,
					Theme.I_ZAP, true);
		}

		@Override
		public void frame(Game g) {
			long now = System.nanoTime();
			if (now - checkAt > 200_000_000L) {
				checkAt = now;
				long w = UiRuntime.mc().window();
				if (w != 0) {
					focused = GLFW.glfwGetWindowAttrib(w, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
					iconified = GLFW.glfwGetWindowAttrib(w, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;
				}
			}
			int cap = iconified ? Math.round(minimized.value) : !focused ? Math.round(unfocused.value) : 0;
			if (cap > 0 && lastFrame != 0) {
				long wait = 1_000_000_000L / cap - (now - lastFrame);
				if (wait > 1_000_000L) {
					try {
						Thread.sleep(Math.min(1000, wait / 1_000_000L));
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
			}
			lastFrame = System.nanoTime();
		}
	}
}
