package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.ui.gfx.NavIcons;
import xyz.nativelaunch.ui.Theme;

/**
 * Low Fire: the fire on your screen while you burn is drawn lower, so it stops hiding what is in front of you. The game
 * draws it (see LowFireMixin); this module only holds how much lower, and shows it in its preview.
 */
public final class LowFire extends Module {
	/** How far the fire is moved down, in the game's own units (the vanilla fire sits 0.3 below the camera). */
	private static volatile float drop;
	final Setting.Num height = add(new Setting.Num("height", "Lower the fire by", 60, 10, 100, 5, "%"));

	LowFire() {
		super("low-fire", "Low Fire", "Lowers the fire on your screen while you burn, so you can still see and fight.", VISUAL, NavIcons.FLAME, false);
	}

	/** Extra downward offset the fire overlay gets right now (0 while this module is off). */
	public static float drop() {
		return drop;
	}

	@Override
	public void frame(Game g) {
		drop = height.value / 100f * 0.45f;
	}

	@Override
	protected void onDisable() {
		drop = 0;
	}

	@Override
	public boolean preview(Ui ui, Game g, float x, float y, float w, float h) {
		Canvas c = ui.c;
		// the view from the player's eyes: sky, ground and a crosshair, with flames licking up from the bottom
		c.gradientV(x, y, w, h * 0.55f, 0xFF6BA8E8, 0xFFB9DAF5);
		c.fill(x, y + h * 0.55f, w, h * 0.45f, 0xFF5E8F3F);
		c.fill(x, y + h * 0.55f, w, h * 0.04f, 0xFF77B04C);
		float cx = x + w / 2, cy = y + h / 2;
		c.fill(cx - 7, cy - 1, 14, 2, 0xE0FFFFFF);
		c.fill(cx - 1, cy - 7, 2, 14, 0xE0FFFFFF);
		float frac = height.value / 100f;
		float top = y + h * (0.30f + 0.60f * frac); // where the flames reach up to: lower as the setting grows
		double t = ui.now / 140.0;
		int cols = 22;
		float cw = w / cols;
		for (int i = 0; i < cols; i++) {
			float tip = top + (float) (Math.sin(t + i * 1.7) * 0.5 + Math.sin(t * 1.9 + i * 0.7) * 0.5) * h * 0.035f;
			// flames are tallest at the sides, like the screen overlay (two columns of fire)
			float side = Math.abs((i + 0.5f) / cols - 0.5f) * 2;
			tip += (1 - side) * h * 0.10f;
			float fy = Math.min(tip, y + h);
			c.fill(x + i * cw, fy, cw + 0.5f, y + h - fy, 0xFFE8531C);
			c.fill(x + i * cw, fy + (y + h - fy) * 0.35f, cw + 0.5f, (y + h - fy) * 0.65f, 0xFFF59A23);
			c.fill(x + i * cw, fy + (y + h - fy) * 0.7f, cw + 0.5f, (y + h - fy) * 0.3f, 0xFFFFD34D);
		}
		String t2 = "Fire at " + Math.round(height.value) + "% lower";
		float tw = c.textWidth(Fonts.SEMIBOLD, 10.5f, t2) + 20;
		c.round(x + (w - tw) / 2, y + h - 34, tw, 22, 11, 0xC0000000);
		c.text(Fonts.SEMIBOLD, 10.5f, t2, x + (w - tw) / 2 + 10, y + h - 34 + (22 - c.lineHeight(Fonts.SEMIBOLD, 10.5f)) / 2, Theme.TEXT_SECONDARY);
		return true;
	}
}
