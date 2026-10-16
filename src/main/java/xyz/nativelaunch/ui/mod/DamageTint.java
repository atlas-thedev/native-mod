package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.McBridge;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.UiRuntime;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;

/**
 * Damage Tint: the flash on a player or mob that takes damage (red by default) in the colour and strength you pick.
 * Purely cosmetic: it rewrites the colours of the game's own hurt-overlay texture (rows 0 - 7), nothing else.
 */
public final class DamageTint extends Module {
	/** What the game ships: 70 % red, as the overlay texture stores it (ABGR). */
	private static final int VANILLA_ABGR = 0xB20000FF;
	final Setting.Color color = add(new Setting.Color("color", "Tint colour", 0xFFFF0000));
	final Setting.Num opacity = add(new Setting.Num("opacity", "Strength", 70, 0, 100, 5, "%"));
	private int applied = Integer.MIN_VALUE;
	private long appliedAt;
	private int failures;

	DamageTint() {
		super("damage-tint", "Damage Tint", "Change the colour and strength of the flash when you or anything else takes damage. Only the look: no effect on gameplay.", VISUAL,
				Theme.I_PALETTE, false);
	}

	/** ARGB colour + strength as the overlay texture wants it (ABGR). */
	static int abgr(int argb, float percent) {
		int a = (int) (Math.max(0, Math.min(100, percent)) / 100f * 255f); // 70 % is 178, exactly what the game ships
		return (a << 24) | ((argb & 0xFF) << 16) | (((argb >> 8) & 0xFF) << 8) | ((argb >> 16) & 0xFF);
	}

	@Override
	public void frame(Game g) {
		if (!g.inWorld || failures > 3) {
			return; // the overlay texture exists once the renderer does; a version that cannot be changed is left alone
		}
		int want = abgr(color.value, opacity.value);
		long now = System.currentTimeMillis();
		if (want == applied && now - appliedAt < 15_000) {
			return;
		}
		McBridge mc = UiRuntime.mc();
		if (mc != null && mc.setHurtTint(want)) {
			applied = want;
			appliedAt = now;
			failures = 0;
		} else {
			failures++;
		}
	}

	@Override
	protected void onDisable() {
		McBridge mc = UiRuntime.mc();
		if (mc != null) {
			mc.setHurtTint(VANILLA_ABGR);
		}
		applied = Integer.MIN_VALUE;
		failures = 0;
	}

	@Override
	public boolean preview(Ui ui, Game g, float x, float y, float w, float h) {
		Canvas c = ui.c;
		c.gradientV(x, y, w, h * 0.62f, 0xFF4B6FA8, 0xFF7FA5D6);
		c.fill(x, y + h * 0.62f, w, h * 0.38f, 0xFF5E8F3F);
		float u = Math.min(w / 4.2f, h / 3.2f) / 8f; // one "pixel" of the figure
		int tint = Theme.alpha(0xFF000000 | (color.value & 0xFFFFFF), opacity.value / 100f);
		figure(c, x + w * 0.30f, y + h * 0.80f, u, 0);
		figure(c, x + w * 0.70f, y + h * 0.80f, u, tint);
		label(c, "Normal", x + w * 0.30f, y + h * 0.84f);
		label(c, "Hurt", x + w * 0.70f, y + h * 0.84f);
		return true;
	}

	private static void label(Canvas c, String t, float cx, float y) {
		float tw = c.textWidth(Fonts.SEMIBOLD, 10.5f, t) + 18;
		c.round(cx - tw / 2, y, tw, 20, 10, 0xC0000000);
		c.text(Fonts.SEMIBOLD, 10.5f, t, cx - tw / 2 + 9, y + (20 - c.lineHeight(Fonts.SEMIBOLD, 10.5f)) / 2, Theme.TEXT_SECONDARY);
	}

	/** A blocky player-shaped figure standing at (cx, feet), u px per figure pixel; {@code tint} is laid over every part. */
	private static void figure(Canvas c, float cx, float feet, float u, int tint) {
		int[][] parts = { // x, y (from the feet), w, h in figure pixels; colours below
				{-4, 24, 8, 8}, // head
				{-4, 12, 8, 12}, // body
				{-8, 12, 4, 12}, // left arm
				{4, 12, 4, 12}, // right arm
				{-4, 0, 4, 12}, // left leg
				{0, 0, 4, 12}}; // right leg
		int[] colours = {0xFFC49A6C, 0xFF2AA3A6, 0xFFC49A6C, 0xFFC49A6C, 0xFF3A44A8, 0xFF3A44A8};
		for (int i = 0; i < parts.length; i++) {
			float px = cx + parts[i][0] * u, py = feet - (parts[i][1] + parts[i][3]) * u, pw = parts[i][2] * u, ph = parts[i][3] * u;
			c.fill(px, py, pw, ph, colours[i]);
			if ((tint >>> 24) != 0) {
				c.fill(px, py, pw, ph, tint);
			}
		}
		// face
		c.fill(cx - 3 * u, feet - 29 * u, u, u, 0xFF2B1A0E);
		c.fill(cx + 2 * u, feet - 29 * u, u, u, 0xFF2B1A0E);
	}
}
