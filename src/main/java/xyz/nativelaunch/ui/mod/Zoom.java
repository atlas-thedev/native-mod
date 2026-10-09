package xyz.nativelaunch.ui.mod;

/** Shared with the zoom mixin: the current FOV divisor (1 = no zoom). Written on the render thread. */
public final class Zoom {
	public static volatile float factor = 1f;

	private Zoom() {
	}
}
