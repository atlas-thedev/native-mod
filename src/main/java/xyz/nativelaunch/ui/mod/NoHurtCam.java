package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;

/** No Hurt Cam: the screen no longer jolts sideways when you take damage (the red flash and sound stay). The game's tilt is skipped (NoHurtCamMixin). */
public final class NoHurtCam extends Module {
	private static volatile boolean active;

	NoHurtCam() {
		super("no-hurt-cam", "No Hurt Cam", "Stops the camera from jolting when you take damage, so you keep your aim in a fight.", VISUAL, Theme.I_ZAP, false);
	}

	/** True while the hurt shake should be skipped. */
	public static boolean active() {
		return active;
	}

	@Override
	public void frame(Game g) {
		active = true;
	}

	@Override
	protected void onDisable() {
		active = false;
	}

	@Override
	public boolean preview(Ui ui, Game g, float x, float y, float w, float h) {
		Canvas c = ui.c;
		float half = (w - 36) / 2, vh = Math.min(h - 80, half * 0.7f), top = y + (h - vh) / 2 - 6;
		double phase = (ui.now % 1800) / 1800.0;
		float shake = phase < 0.35 ? (float) (Math.sin(ui.now / 40.0) * 7 * (1 - phase / 0.35)) : 0f;
		for (int i = 0; i < 2; i++) {
			float vx = x + 12 + i * (half + 12);
			c.pushClip(vx, top, half, vh);
			float dx = i == 0 ? shake : 0f, dy = i == 0 ? shake * 0.6f : 0f;
			c.gradientV(vx + dx - 10, top + dy - 10, half + 20, vh * 0.58f + 10, 0xFF6BA8E8, 0xFFB9DAF5);
			c.fill(vx + dx - 10, top + dy + vh * 0.58f, half + 20, vh * 0.42f + 20, 0xFF5E8F3F);
			c.fill(vx + dx + half * 0.4f, top + dy + vh * 0.38f, half * 0.12f, vh * 0.34f, 0xFF6B4A2B);
			c.fill(vx + dx + half * 0.3f, top + dy + vh * 0.16f, half * 0.32f, vh * 0.26f, 0xFF3E8A3A);
			if (i == 0 && phase < 0.35) {
				c.fill(vx, top, half, vh, Theme.alpha(0xFFFF2020, 0.25f * (float) (1 - phase / 0.35)));
			}
			c.popClip();
			c.outline(vx, top, half, vh, 6, 1, Theme.HAIRLINE_STRONG);
			String t = i == 0 ? "Vanilla" : "No Hurt Cam";
			float tw = c.textWidth(Fonts.SEMIBOLD, 10.5f, t) + 18;
			c.round(vx + (half - tw) / 2, top + vh + 8, tw, 20, 10, 0xC0000000);
			c.text(Fonts.SEMIBOLD, 10.5f, t, vx + (half - tw) / 2 + 9, top + vh + 8 + (20 - c.lineHeight(Fonts.SEMIBOLD, 10.5f)) / 2, i == 0 ? Theme.TEXT_SECONDARY : Theme.ONLINE);
		}
		return true;
	}
}
