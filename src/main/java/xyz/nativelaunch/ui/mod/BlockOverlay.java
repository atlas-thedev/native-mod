package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;

/**
 * Block Overlay: the outline around the block you look at in a colour (and strength) you pick, or cycling through the
 * rainbow. The game draws it (see BlockOverlayMixin); this module holds the colour the hook asks for. The default dark
 * outline is replaced, a high-contrast outline the player chose in the game's accessibility options is left alone.
 */
public final class BlockOverlay extends Module {
	/** The game's own outline: black at 40 % (0.4 * 255 = 102). */
	private static final int VANILLA = 0x66000000;
	private static volatile boolean active;
	private static volatile int argb = VANILLA;
	private static volatile boolean rainbow;
	private static volatile float speed = 3;
	final Setting.Color color = add(new Setting.Color("color", "Outline colour", 0xFFFFFFFF));
	final Setting.Num opacity = add(new Setting.Num("opacity", "Strength", 100, 10, 100, 5, "%"));
	final Setting.Bool rainbowSetting = add(new Setting.Bool("rainbow", "Rainbow", false));
	final Setting.Num rainbowSpeed = add(new Setting.Num("speed", "Rainbow speed", 3, 1, 10, 1, ""));

	BlockOverlay() {
		super("block-overlay", "Block Overlay", "Colour the outline around the block you are looking at, or let it cycle through the rainbow.", VISUAL, Theme.I_LAYERS, false);
	}

	@Override
	protected boolean hidden(Setting s) {
		return s == rainbowSpeed && !rainbowSetting.value || s == color && rainbowSetting.value;
	}

	@Override
	public void frame(Game g) {
		active = true;
		rainbow = rainbowSetting.value;
		speed = rainbowSpeed.value;
		argb = withOpacity(color.value, opacity.value);
	}

	@Override
	protected void onDisable() {
		active = false;
	}

	static int withOpacity(int rgb, float percent) {
		int a = Math.round(Math.max(0, Math.min(100, percent)) / 100f * 255f);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	/** HSV with full saturation and value, as ARGB at the given alpha. */
	static int hue(float hue, int alpha) {
		float h = (hue - (float) Math.floor(hue)) * 6f;
		float f = h - (float) Math.floor(h);
		float q = 1f - f;
		float r, gr, b;
		switch ((int) h) {
			case 0: r = 1; gr = f; b = 0; break;
			case 1: r = q; gr = 1; b = 0; break;
			case 2: r = 0; gr = 1; b = f; break;
			case 3: r = 0; gr = q; b = 1; break;
			case 4: r = f; gr = 0; b = 1; break;
			default: r = 1; gr = 0; b = q; break;
		}
		return (alpha << 24) | (Math.round(r * 255) << 16) | (Math.round(gr * 255) << 8) | Math.round(b * 255);
	}

	/** The colour the outline is drawn with right now: the picked one in place of the default, anything else as it is. */
	public static int outline(int original) {
		if (!active || original != VANILLA) {
			return original;
		}
		if (rainbow) {
			return hue((System.currentTimeMillis() % 100000L) / (12000f / speed), argb >>> 24);
		}
		return argb;
	}

	/** Same, for the games that take the outline as red / green / blue / alpha floats; true when it replaced them. */
	public static boolean outline(float[] rgba) {
		if (!active || rgba[0] != 0f || rgba[1] != 0f || rgba[2] != 0f || Math.abs(rgba[3] - 0.4f) > 0.001f) {
			return false;
		}
		int c = outline(VANILLA);
		rgba[0] = ((c >> 16) & 0xFF) / 255f;
		rgba[1] = ((c >> 8) & 0xFF) / 255f;
		rgba[2] = (c & 0xFF) / 255f;
		rgba[3] = (c >>> 24) / 255f;
		return true;
	}

	@Override
	public boolean preview(Ui ui, Game g, float x, float y, float w, float h) {
		Canvas c = ui.c;
		c.gradientV(x, y, w, h * 0.58f, 0xFF6BA8E8, 0xFFB9DAF5);
		c.fill(x, y + h * 0.58f, w, h * 0.42f, 0xFF5E8F3F);
		float s = Math.min(w, h) * 0.46f;
		float bx = x + (w - s) / 2, by = y + (h - s) / 2 + h * 0.06f;
		c.fill(bx, by, s, s * 0.22f, 0xFF67A63F);
		c.fill(bx, by + s * 0.22f, s, s * 0.78f, 0xFF7B5A3A);
		for (int i = 0; i < 6; i++) { // a little dirt speckle
			c.fill(bx + s * (0.08f + 0.15f * i), by + s * (0.34f + 0.09f * ((i * 5) % 6)), s * 0.07f, s * 0.07f, 0xFF6A4C30);
		}
		int col = rainbowSetting.value
				? hue((ui.now % 100000L) / (12000f / rainbowSpeed.value), Math.round(opacity.value / 100f * 255f))
				: withOpacity(color.value, opacity.value);
		float t = Math.max(2, s * 0.025f);
		c.outline(bx - t / 2, by - t / 2, s + t, s + t, 0, t, col);
		String hint = "Looking at a block";
		float tw = c.textWidth(Fonts.SEMIBOLD, 10.5f, hint) + 20;
		c.round(x + (w - tw) / 2, y + h - 34, tw, 22, 11, 0xC0000000);
		c.text(Fonts.SEMIBOLD, 10.5f, hint, x + (w - tw) / 2 + 10, y + h - 34 + (22 - c.lineHeight(Fonts.SEMIBOLD, 10.5f)) / 2, Theme.TEXT_SECONDARY);
		return true;
	}
}
