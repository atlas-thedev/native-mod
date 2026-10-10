package xyz.nativelaunch.ui26;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Minecraft 26.x: an empty vanilla Screen so the game treats the Native UI as "a screen is open" (cursor released,
 * game input paused, world keeps rendering). It draws nothing itself; the Native layer is added after the toasts.
 */
public final class NativeHostScreen26 extends Screen {
	final int kind;
	final Screen parent;

	NativeHostScreen26(int kind, Screen parent) {
		super(Component.literal(kind == 1 ? "Native" : kind == 2 ? "Native Relay" : kind == 3 ? "Native Menu" : kind == 5 ? "Native Pause" : "Native HUD Editor"));
		this.kind = kind;
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return kind == 5;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false; // Esc is handled by the Native UI itself
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
	}
}
