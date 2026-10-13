package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.gfx.Canvas;

/** A custom crosshair: shape, size, thickness, gap, colours, outline, and a colour change when something is under it. */
final class CrosshairModule extends Module {
	final Setting.Choice shape = add(new Setting.Choice("shape", "Shape", 0, "Cross", "T", "Dot", "Circle", "Square"));
	final Setting.Num size = add(new Setting.Num("size", "Size", 7, 1, 30, 1, "px"));
	final Setting.Num thickness = add(new Setting.Num("thickness", "Thickness", 2, 1, 8, 1, "px"));
	final Setting.Num gap = add(new Setting.Num("gap", "Gap", 3, 0, 20, 1, "px"));
	final Setting.Bool dot = add(new Setting.Bool("dot", "Centre dot", false));
	final Setting.Color color = add(new Setting.Color("color", "Colour", 0xFFFFFFFF));
	final Setting.Num opacity = add(new Setting.Num("opacity", "Opacity", 100, 10, 100, 5, "%"));
	final Setting.Bool outline = add(new Setting.Bool("outline", "Outline", true));
	final Setting.Color outlineColor = add(new Setting.Color("outlineColor", "Outline colour", 0xFF000000));
	final Setting.Bool dynamic = add(new Setting.Bool("dynamic", "Open up while moving", false));
	final Setting.Bool onTarget = add(new Setting.Bool("onTarget", "Change colour over a mob or player", false));
	final Setting.Color targetColor = add(new Setting.Color("targetColor", "Colour over a target", 0xFFEF4444));
	final Setting.Bool hide = add(new Setting.Bool("hide", "Hide the vanilla crosshair", true));
	private float spread;
	private long last;

	CrosshairModule() {
		super("crosshair", "Crosshair", "Replace the vanilla crosshair with your own: shape, size, gap, colour and outline.", VISUAL, Theme.I_CROSSHAIR, false);
	}

    @Override
    protected boolean hidden(Setting s) {
        if (s == targetColor) return !onTarget.value;
        if (s == outlineColor) return !outline.value;
        return shape.value == 2 && (s == gap || s == dot || s == dynamic);
    }

    @Override
    protected void onDisable() {
        Overlays.releaseCrosshair();
    }

	@Override
	public int needs() {
		return onTarget.value ? Game.NEED_TARGET : 0;
	}

	@Override
	public void frame(Game g) {
		if (hide.value && g.hudAllowed) {
			Overlays.replaceCrosshair();
		} else {
            Overlays.releaseCrosshair();
        }
	}

	@Override
	public boolean overlays(Game g) {
		return g.hudAllowed;
	}

	@Override
	public void overlay(Ui ui, Game g, boolean editing) {
		Canvas c = ui.c;
		long dt = Math.min(100, ui.now - last);
		last = ui.now;
		float want = dynamic.value && !g.sample ? Math.min(8f, g.speed * 1.4f) : 0f;
		spread += (want - spread) * Math.min(1f, dt / 90f);
		int col = onTarget.value && g.hasTarget && !g.sample && !editing ? targetColor.value : color.value;
		draw(c, c.width() / 2, c.height() / 2, 1f, col, spread);
	}

	@Override
	public boolean preview(Ui ui, Game g, float x, float y, float w, float h) {
		Canvas c = ui.c;
		// a block-ish backdrop so a white / black crosshair can be judged
		c.round(x, y, w, h, 10, 0xFF6B8E5A);
		c.fill(x, y + h * 0.62f, w, h * 0.38f, 0xFF7A5C3A);
		c.fill(x, y, w, h * 0.5f, 0xFF8EC5F0);
		c.pushClip(x, y, w, h);
		draw(c, x + w / 2, y + h / 2, 2.2f, color.value, 0);
		c.popClip();
		return true;
	}

	private void draw(Canvas c, float cx, float cy, float k, int base, float extraGap) {
		float sc = c.scale;
		int a = Math.round(((base >>> 24) / 255f) * opacity.value / 100f * 255);
		int col = (a << 24) | (base & 0xFFFFFF);
		int oc = Theme.alpha(outlineColor.value, a / 255f);
		int t = Math.max(1, Math.round(thickness.value * k * sc * 0.6f));
		int L = Math.max(1, Math.round(size.value * k * sc * 0.75f));
		int G = Math.max(0, Math.round((gap.value + extraGap) * k * sc * 0.75f));
		int o = outline.value ? Math.max(1, Math.round(sc * 0.7f * Math.min(1.5f, k))) : 0;
		int X = c.px(cx), Y = c.px(cy), half = t / 2;
		int s = shape.value;
		if (s == 2) { // dot
			int d = Math.max(t, L);
			if (o > 0) {
				rect(c, X - d / 2 - o, Y - d / 2 - o, d + 2 * o, d + 2 * o, oc);
			}
			rect(c, X - d / 2, Y - d / 2, d, d, col);
			return;
		}
		if (s == 3 || s == 4) { // circle / square ring
			int d = 2 * (L + G);
			float x0 = (X - d / 2) / sc, y0 = (Y - d / 2) / sc, w = d / sc;
			float r = s == 3 ? w / 2 : 0;
			if (o > 0) {
				c.outline(x0 - o / sc, y0 - o / sc, w + 2 * o / sc, w + 2 * o / sc, s == 3 ? r + o / sc : 0, (t + 2 * o) / sc, oc);
			}
			c.outline(x0, y0, w, w, r, t / sc, col);
			if (dot.value) {
				centreDot(c, X, Y, t, o, col, oc);
			}
			return;
		}
		// arms: left, right, top, bottom (T has no top arm)
		int[][] arms = {
				{X - half - G - L, Y - half, L, t},
				{X - half + t + G, Y - half, L, t},
				{X - half, Y - half - G - L, t, L},
				{X - half, Y - half + t + G, t, L}};
		int n = arms.length;
		if (o > 0) {
			for (int i = 0; i < n; i++) {
				if (s == 1 && i == 2) {
					continue;
				}
				rect(c, arms[i][0] - o, arms[i][1] - o, arms[i][2] + 2 * o, arms[i][3] + 2 * o, oc);
			}
		}
		for (int i = 0; i < n; i++) {
			if (s == 1 && i == 2) {
				continue;
			}
			rect(c, arms[i][0], arms[i][1], arms[i][2], arms[i][3], col);
		}
		if (dot.value) {
			centreDot(c, X, Y, t, o, col, oc);
		}
	}

	private static void centreDot(Canvas c, int X, int Y, int t, int o, int col, int oc) {
		int d = Math.max(2, t);
		if (o > 0) {
			rect(c, X - d / 2 - o, Y - d / 2 - o, d + 2 * o, d + 2 * o, oc);
		}
		rect(c, X - d / 2, Y - d / 2, d, d, col);
	}

	/** A rectangle in whole framebuffer pixels (so the crosshair stays crisp at any UI scale). */
	private static void rect(Canvas c, int x, int y, int w, int h, int argb) {
		float sc = c.scale;
		c.fill(x / sc, y / sc, w / sc, h / sc, argb);
	}
}
