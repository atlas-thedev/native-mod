package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;

/** Small drawings the module page's live preview is made of: a blocky game scene and a few labels. */
final class ModPreviews {
	private ModPreviews() {
	}

	/** A block scene (sky, hills, grass, a tree) in a box, zoomed around its centre. The caller clips to the box. */
	static void scene(Canvas c, float x, float y, float w, float h, float zoom) {
		float cx = x + w / 2, cy = y + h / 2;
		float horizon = 0.6f;
		// sky
		c.gradientV(zx(x, cx, zoom) - 1, zy(y, cy, zoom) - 1, w * zoom + 2, h * horizon * zoom + 2, 0xFF6FB2F2, 0xFFBCE0FA);
		// sun
		block(c, x, y, w, h, cx, cy, zoom, 0.74f, 0.1f, 0.09f, 0.16f, 0xFFFFF2B0);
		// far hills
		block(c, x, y, w, h, cx, cy, zoom, 0.0f, 0.46f, 0.34f, 0.15f, 0xFF5E8F5B);
		block(c, x, y, w, h, cx, cy, zoom, 0.28f, 0.5f, 0.3f, 0.11f, 0xFF527F50);
		block(c, x, y, w, h, cx, cy, zoom, 0.55f, 0.42f, 0.45f, 0.19f, 0xFF5E8F5B);
		// ground
		block(c, x, y, w, h, cx, cy, zoom, -0.2f, horizon, 1.4f, 0.06f, 0xFF67A63F);
		block(c, x, y, w, h, cx, cy, zoom, -0.2f, horizon + 0.06f, 1.4f, 0.5f, 0xFF7B5A3A);
		block(c, x, y, w, h, cx, cy, zoom, -0.2f, horizon + 0.2f, 1.4f, 0.4f, 0xFF6B6D70);
		// tree
		block(c, x, y, w, h, cx, cy, zoom, 0.2f, 0.34f, 0.035f, 0.26f, 0xFF6B4A2B);
		block(c, x, y, w, h, cx, cy, zoom, 0.145f, 0.16f, 0.145f, 0.12f, 0xFF3E8A3A);
		block(c, x, y, w, h, cx, cy, zoom, 0.17f, 0.1f, 0.095f, 0.08f, 0xFF489A44);
		// a stone block and a flower
		block(c, x, y, w, h, cx, cy, zoom, 0.62f, 0.52f, 0.07f, 0.08f, 0xFF8A8D91);
		block(c, x, y, w, h, cx, cy, zoom, 0.8f, 0.555f, 0.012f, 0.045f, 0xFFE24D5C);
	}

	private static float zx(float x, float cx, float zoom) {
		return cx + (x - cx) * zoom;
	}

	private static float zy(float y, float cy, float zoom) {
		return cy + (y - cy) * zoom;
	}

	private static void block(Canvas c, float x, float y, float w, float h, float cx, float cy, float z, float nx, float ny, float nw, float nh, int argb) {
		float X = zx(x + nx * w, cx, z), Y = zy(y + ny * h, cy, z);
		c.fill(X, Y, nw * w * z, nh * h * z, argb);
	}

	/** A small rounded label centred on cx. */
	static void chip(Canvas c, String text, float cx, float y, int fg) {
		float tw = c.textWidth(Fonts.SEMIBOLD, 10.5f, text) + 20;
		c.round(cx - tw / 2, y, tw, 22, 11, 0xC0000000);
		c.outline(cx - tw / 2, y, tw, 22, 11, 1, Theme.HAIRLINE_STRONG);
		c.text(Fonts.SEMIBOLD, 10.5f, text, cx - tw / 2 + 10, y + (22 - c.lineHeight(Fonts.SEMIBOLD, 10.5f)) / 2, fg);
	}

	/** Largest 16:9 viewport that fits the box with room for captions; {x, y, w, h}. */
	static float[] viewport(float x, float y, float w, float h) {
		float vw = Math.min(w - 40, (h - 80) * 16f / 9f);
		vw = Math.max(80, vw);
		float vh = vw * 9f / 16f;
		return new float[] {x + (w - vw) / 2, y + (h - vh) / 2 - 4, vw, vh};
	}
}
