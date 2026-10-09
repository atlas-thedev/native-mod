package xyz.nativelaunch.ui.gfx;

import java.util.HashMap;
import java.util.Map;

/**
 * Immediate-mode 2D painter. Everything is described in logical units, converted to physical pixels with
 * {@link #scale}, clipped on the CPU and collected as textured quads in batches (one per texture). A backend
 * (raw OpenGL, or Minecraft's own GUI renderer on 26.x) uploads the atlas and draws the batches in order.
 */
public final class Canvas {
	public final Atlas atlas;
	public final Fonts fonts;
	private final Shapes shapes = new Shapes();
	public float scale = 1;
	public int fbW, fbH;

	// quad storage: 8 floats (x0 y0 x1 y1 u0 v0 u1 v1) + 4 colours (tl tr br bl)
	public float[] geo = new float[8 * 4096];
	public int[] col = new int[4 * 4096];
	public int quads;
	// batches: texture (null = atlas) + first quad + count
	public Image[] batchTex = new Image[64];
	public int[] batchStart = new int[64];
	public int batches;

	private final int[] clip = new int[4 * 32];
	private int clipDepth;
	private float alpha = 1;
	private final float[] alphaStack = new float[32];
	private int alphaDepth;
	private final Map<Object, int[]> stamped = new HashMap<Object, int[]>();
	private int stampedGeneration = -1;

	public Canvas(Atlas atlas, Fonts fonts) {
		this.atlas = atlas;
		this.fonts = fonts;
	}

	public void begin(int fbW, int fbH, float scale) {
		if (atlas.isFull()) {
			atlas.reset();
		}
		this.fbW = fbW;
		this.fbH = fbH;
		this.scale = scale;
		quads = 0;
		batches = 0;
		clipDepth = 0;
		clip[0] = 0;
		clip[1] = 0;
		clip[2] = fbW;
		clip[3] = fbH;
		alpha = 1;
		alphaDepth = 0;
	}

	public float width() {
		return fbW / scale;
	}

	public float height() {
		return fbH / scale;
	}

	public int px(float v) {
		return Math.round(v * scale);
	}

	// ── state ────────────────────────────────────────────────────────────

	public void pushClip(float x, float y, float w, float h) {
		int x0 = px(x), y0 = px(y), x1 = px(x + w), y1 = px(y + h);
		int b = clipDepth * 4;
		int n = Math.min(clipDepth + 1, 31) * 4;
		clip[n] = Math.max(clip[b], x0);
		clip[n + 1] = Math.max(clip[b + 1], y0);
		clip[n + 2] = Math.min(clip[b + 2], x1);
		clip[n + 3] = Math.min(clip[b + 3], y1);
		clipDepth = Math.min(clipDepth + 1, 31);
	}

	public void popClip() {
		if (clipDepth > 0) {
			clipDepth--;
		}
	}

	/** Multiplies the alpha of everything drawn until {@link #popAlpha()}. */
	public void pushAlpha(float a) {
		alphaStack[Math.min(alphaDepth, 31)] = alpha;
		alphaDepth = Math.min(alphaDepth + 1, 31);
		alpha *= Math.max(0, Math.min(1, a));
	}

	public void popAlpha() {
		if (alphaDepth > 0) {
			alpha = alphaStack[--alphaDepth];
		}
	}

	/** True when a logical point is inside the current clip (for hit testing). */
	public boolean inClip(float x, float y) {
		int b = clipDepth * 4;
		float X = x * scale, Y = y * scale;
		return X >= clip[b] && X < clip[b + 2] && Y >= clip[b + 1] && Y < clip[b + 3];
	}

	// ── primitives ───────────────────────────────────────────────────────

	public void fill(float x, float y, float w, float h, int argb) {
		int x0 = px(x), y0 = px(y), x1 = px(x + w), y1 = px(y + h);
		solid(x0, y0, x1, y1, argb, argb, argb, argb);
	}

	public void gradientV(float x, float y, float w, float h, int top, int bottom) {
		solid(px(x), px(y), px(x + w), px(y + h), top, top, bottom, bottom);
	}

	public void gradientH(float x, float y, float w, float h, int left, int right) {
		solid(px(x), px(y), px(x + w), px(y + h), left, right, right, left);
	}

	private void solid(int x0, int y0, int x1, int y1, int c0, int c1, int c2, int c3) {
		float u = atlas.whiteU, v = atlas.whiteV;
		quad(null, x0, y0, x1, y1, u, v, u, v, c0, c1, c2, c3);
	}

	public void round(float x, float y, float w, float h, float r, int argb) {
		nine(px(x), px(y), px(x + w), px(y + h), px(r), 0, 0, argb);
	}

	public void outline(float x, float y, float w, float h, float r, float width, int argb) {
		nine(px(x), px(y), px(x + w), px(y + h), px(r), 0, Math.max(1, px(width)), argb);
	}

	/** Soft drop shadow around a rounded box; blur in logical units. */
	public void shadow(float x, float y, float w, float h, float r, float blur, int argb) {
		nine(px(x), px(y), px(x + w), px(y + h), px(r), Math.max(1, px(blur)), 0, argb);
	}

	public void circle(float cx, float cy, float radius, int argb) {
		round(cx - radius, cy - radius, radius * 2, radius * 2, radius, argb);
	}

	private void nine(int X0, int Y0, int X1, int Y1, int r, int f, int bw, int argb) {
		int w = X1 - X0, h = Y1 - Y0;
		if (w <= 0 || h <= 0) {
			return;
		}
		r = Math.max(0, Math.min(r, (Math.min(w, h) - 2) / 2));
		if (r == 0 && f == 0 && bw == 0) {
			solid(X0, Y0, X1, Y1, argb, argb, argb, argb);
			return;
		}
		Shapes.Sprite s = shapes.get(atlas, r, f, bw);
		if (s == null) {
			return;
		}
		int m = f + 1;
		int ox0 = X0 - m, oy0 = Y0 - m, ox1 = X1 + m, oy1 = Y1 + m;
		int c = s.c;
		float inv = 1f / atlas.size;
		float u0 = s.x * inv, u1 = (s.x + c) * inv, u2 = (s.x + c + 2) * inv, u3 = (s.x + s.s) * inv;
		float v0 = s.y * inv, v1 = (s.y + c) * inv, v2 = (s.y + c + 2) * inv, v3 = (s.y + s.s) * inv;
		int xa = ox0 + c, xb = ox1 - c, ya = oy0 + c, yb = oy1 - c;
		// corners
		quad(null, ox0, oy0, xa, ya, u0, v0, u1, v1, argb, argb, argb, argb);
		quad(null, xb, oy0, ox1, ya, u2, v0, u3, v1, argb, argb, argb, argb);
		quad(null, ox0, yb, xa, oy1, u0, v2, u1, v3, argb, argb, argb, argb);
		quad(null, xb, yb, ox1, oy1, u2, v2, u3, v3, argb, argb, argb, argb);
		// edges
		if (xb > xa) {
			quad(null, xa, oy0, xb, ya, u1, v0, u2, v1, argb, argb, argb, argb);
			quad(null, xa, yb, xb, oy1, u1, v2, u2, v3, argb, argb, argb, argb);
		}
		if (yb > ya) {
			quad(null, ox0, ya, xa, yb, u0, v1, u1, v2, argb, argb, argb, argb);
			quad(null, xb, ya, ox1, yb, u2, v1, u3, v2, argb, argb, argb, argb);
		}
		if (!s.hollow && xb > xa && yb > ya) {
			solid(xa, ya, xb, yb, argb, argb, argb, argb);
		}
	}

	// ── text ─────────────────────────────────────────────────────────────

	public Font font(int face, float size) {
		return fonts.get(face, Math.round(size * scale));
	}

	public float textWidth(int face, float size, CharSequence text) {
		return font(face, size).width(text) / scale;
	}

	/** Line height in logical units. */
	public float lineHeight(int face, float size) {
		return font(face, size).lineHeight / scale;
	}

	/**
	 * Draws one line; y is the top of the line box. Returns the advance in logical units.
	 */
	public float text(int face, float size, CharSequence text, float x, float y, int argb) {
		Font f = font(face, size);
		float pen = x * scale;
		float base = Math.round(y * scale + (f.lineHeight - (f.ascent + f.descent)) / 2f + f.ascent);
		float inv = 1f / atlas.size;
		for (int i = 0; i < text.length(); ) {
			int cp = Character.codePointAt(text, i);
			i += Character.charCount(cp);
			Glyph g = f.glyph(atlas, cp);
			if (g.w > 0) {
				int gx = Math.round(pen) + g.xoff;
				int gy = (int) base + g.yoff;
				quad(null, gx, gy, gx + g.w, gy + g.h, g.ax * inv, g.ay * inv, (g.ax + g.w) * inv, (g.ay + g.h) * inv, argb, argb, argb, argb);
			}
			pen += g.advance;
		}
		return (pen - x * scale) / scale;
	}

	/** Text cut to fit maxW with an ellipsis. */
	public String ellipsize(int face, float size, String text, float maxW) {
		if (text == null) {
			return "";
		}
		Font f = font(face, size);
		float max = maxW * scale;
		if (f.width(text) <= max) {
			return text;
		}
		float dots = f.width("\u2026");
		float w = 0;
		int i = 0;
		while (i < text.length()) {
			int cp = text.codePointAt(i);
			float a = f.advance(cp);
			if (w + a + dots > max) {
				break;
			}
			w += a;
			i += Character.charCount(cp);
		}
		return text.substring(0, i) + "\u2026";
	}

	/** A Lucide icon centred on (cx, cy). */
	public void icon(int codepoint, float size, float cx, float cy, int argb) {
		Font f = font(Fonts.ICONS, size);
		Glyph g = f.glyph(atlas, codepoint);
		if (g.w <= 0) {
			return;
		}
		float inv = 1f / atlas.size;
		// centre the glyph's ink box on the point
		int gx = Math.round(cx * scale - g.w / 2f);
		int gy = Math.round(cy * scale - g.h / 2f);
		quad(null, gx, gy, gx + g.w, gy + g.h, g.ax * inv, g.ay * inv, (g.ax + g.w) * inv, (g.ay + g.h) * inv, argb, argb, argb, argb);
	}

	// ── images ───────────────────────────────────────────────────────────

	/** An image with its own texture, stretched over the box (uv sub-rectangle in 0..1). */
	public void image(Image img, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argb) {
		if (img == null) {
			return;
		}
		quad(img, px(x), px(y), px(x + w), px(y + h), u0, v0, u1, v1, argb, argb, argb, argb);
	}

	/** Covers the box with the image (like CSS background-size: cover), zoomed around the centre. */
	public void imageCover(Image img, float x, float y, float w, float h, float zoom, float panX, float panY, int argb) {
		if (img == null) {
			return;
		}
		float boxAspect = w / h, imgAspect = (float) img.width / img.height;
		float uw = 1, vh = 1;
		if (imgAspect > boxAspect) {
			uw = boxAspect / imgAspect;
		} else {
			vh = imgAspect / boxAspect;
		}
		uw /= zoom;
		vh /= zoom;
		float cu = 0.5f + panX * (1 - uw) / 2, cv = 0.5f + panY * (1 - vh) / 2;
		image(img, x, y, w, h, cu - uw / 2, cv - vh / 2, cu + uw / 2, cv + vh / 2, argb);
	}

	/**
	 * Draws a small image through the atlas at exactly its on-screen pixel size (logos, player faces).
	 * {@code smooth} = area-averaged resample; otherwise nearest (pixel art).
	 */
	public void stamp(Object key, Image src, float x, float y, float w, float h, boolean smooth, int argb) {
		if (src == null) {
			return;
		}
		int X0 = px(x), Y0 = px(y), X1 = px(x + w), Y1 = px(y + h);
		int pw = X1 - X0, ph = Y1 - Y0;
		if (pw <= 0 || ph <= 0) {
			return;
		}
		if (stampedGeneration != atlas.generation) {
			stamped.clear();
			stampedGeneration = atlas.generation;
		}
		int bw = smooth ? pw : src.width, bh = smooth ? ph : src.height;
		java.util.List<Object> k = java.util.Arrays.asList(key, bw, bh);
		int[] at = stamped.get(k);
		if (at == null) {
			Image s = smooth ? src.scaled(bw, bh) : src;
			at = atlas.alloc(bw, bh);
			if (at == null) {
				return;
			}
			atlas.putArgb(at[0], at[1], bw, bh, s.argb);
			stamped.put(k, at);
		}
		float inv = 1f / atlas.size;
		quad(null, X0, Y0, X1, Y1, at[0] * inv, at[1] * inv, (at[0] + bw) * inv, (at[1] + bh) * inv, argb, argb, argb, argb);
	}

	// ── core ─────────────────────────────────────────────────────────────

	private void quad(Image tex, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int c0, int c1, int c2, int c3) {
		int b = clipDepth * 4;
		float cx0 = clip[b], cy0 = clip[b + 1], cx1 = clip[b + 2], cy1 = clip[b + 3];
		if (x1 <= cx0 || x0 >= cx1 || y1 <= cy0 || y0 >= cy1 || x1 <= x0 || y1 <= y0) {
			return;
		}
		if (x0 < cx0) {
			u0 += (u1 - u0) * (cx0 - x0) / (x1 - x0);
			x0 = cx0;
		}
		if (x1 > cx1) {
			u1 -= (u1 - u0) * (x1 - cx1) / (x1 - x0);
			x1 = cx1;
		}
		if (y0 < cy0) {
			float t = (cy0 - y0) / (y1 - y0);
			v0 += (v1 - v0) * t;
			if (c0 != c3 || c1 != c2) {
				c0 = lerpColor(c0, c3, t);
				c1 = lerpColor(c1, c2, t);
			}
			y0 = cy0;
		}
		if (y1 > cy1) {
			float t = (y1 - cy1) / (y1 - y0);
			v1 -= (v1 - v0) * t;
			if (c0 != c3 || c1 != c2) {
				c3 = lerpColor(c3, c0, t);
				c2 = lerpColor(c2, c1, t);
			}
			y1 = cy1;
		}
		if (alpha < 1) {
			c0 = mulAlpha(c0, alpha);
			c1 = mulAlpha(c1, alpha);
			c2 = mulAlpha(c2, alpha);
			c3 = mulAlpha(c3, alpha);
		}
		if ((c0 | c1 | c2 | c3) >>> 24 == 0) {
			return;
		}
		if (batches == 0 || batchTex[batches - 1] != tex) {
			if (batches == batchTex.length) {
				batchTex = java.util.Arrays.copyOf(batchTex, batches * 2);
				batchStart = java.util.Arrays.copyOf(batchStart, batches * 2);
			}
			batchTex[batches] = tex;
			batchStart[batches] = quads;
			batches++;
		}
		if (quads * 8 + 8 > geo.length) {
			geo = java.util.Arrays.copyOf(geo, geo.length * 2);
			col = java.util.Arrays.copyOf(col, col.length * 2);
		}
		int g = quads * 8;
		geo[g] = x0;
		geo[g + 1] = y0;
		geo[g + 2] = x1;
		geo[g + 3] = y1;
		geo[g + 4] = u0;
		geo[g + 5] = v0;
		geo[g + 6] = u1;
		geo[g + 7] = v1;
		int k = quads * 4;
		col[k] = c0;
		col[k + 1] = c1;
		col[k + 2] = c2;
		col[k + 3] = c3;
		quads++;
	}

	/** Quads in batch i: [batchStart[i], batchEnd(i)). */
	public int batchEnd(int i) {
		return i + 1 < batches ? batchStart[i + 1] : quads;
	}

	public static int mulAlpha(int argb, float a) {
		int al = Math.round((argb >>> 24) * a);
		return (al << 24) | (argb & 0xFFFFFF);
	}

	public static int lerpColor(int a, int b, float t) {
		int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
				| (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
	}
}
