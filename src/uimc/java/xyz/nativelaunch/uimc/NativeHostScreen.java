package xyz.nativelaunch.uimc;

import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_437;
import net.minecraft.class_4587;

/**
 * An empty vanilla Screen that only exists so Minecraft treats our UI as "a screen is open" (cursor released, game
 * input paused, world keeps rendering behind it). It draws nothing; the Native UI paints on top every frame.
 * The render methods are declared for every descriptor 1.16 - 1.21.11 used, so whichever one the game calls is a no-op.
 */
public final class NativeHostScreen extends class_437 {
	final int kind;
	final class_437 parent;

	NativeHostScreen(int kind, class_437 parent) {
		super(class_2561.method_30163(kind == 1 ? "Native" : kind == 2 ? "Native Relay" : kind == 3 ? "Native Menu" : "Native HUD Editor"));
		this.kind = kind;
		this.parent = parent;
	}

	/** shouldPause */
	public boolean method_25421() {
		return false;
	}

	/** shouldCloseOnEsc: Esc is handled by the Native UI itself */
	public boolean method_25422() {
		return false;
	}

	// render (1.16 - 1.19.4 / 1.20+)
	public void method_25394(class_4587 matrices, int mouseX, int mouseY, float delta) {
	}

	public void method_25394(class_332 context, int mouseX, int mouseY, float delta) {
	}

	// renderBackground (1.16 - 1.19.4 / 1.20.1 / 1.20.2+)
	public void method_25420(class_4587 matrices) {
	}

	public void method_25420(class_332 context) {
	}

	public void method_25420(class_332 context, int mouseX, int mouseY, float delta) {
	}
}
