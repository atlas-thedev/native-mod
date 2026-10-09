package xyz.nativelaunch.ui.gfx;

import java.util.HashMap;
import java.util.Map;

/**
 * Anti-aliased rounded rectangles, rings and soft shadows as 9-slice sprites: each (radius, feather, ring width)
 * is rasterised once on the CPU from a signed distance function, then stretched to any size with 9 quads.
 */
final class Shapes {
	static final class Sprite {
		final int x, y, c, s; // atlas position, corner tile size, sprite size
		final boolean hollow;

		Sprite(int x, int y, int c, int s, boolean hollow) {
			this.x = x;
			this.y = y;
			this.c = c;
			this.s = s;
			this.hollow = hollow;
		}
	}

	private final Map<Integer, Sprite> cache = new HashMap<Integer, Sprite>();
	private int generation = -1;

	/** @param r corner radius px, f feather px (0 = crisp edge), bw ring width px (0 = filled) */
	Sprite get(Atlas atlas, int r, int f, int bw) {
		if (generation != atlas.generation) {
			cache.clear();
			generation = atlas.generation;
		}
		r = Math.max(0, Math.min(r, 120));
		f = Math.max(0, Math.min(f, 120));
		bw = Math.max(0, Math.min(bw, 60));
		int key = r | (f << 8) | (bw << 16);
		Sprite s = cache.get(key);
		if (s == null) {
			s = bake(atlas, r, f, bw);
			if (s != null) {
				cache.put(key, s);
			}
		}
		return s;
	}

	private static Sprite bake(Atlas atlas, int r, int f, int bw) {
		int c = r + f + 2;
		int size = 2 * c + 2;
		int[] at = atlas.alloc(size, size);
		if (at == null) {
			return null;
		}
		byte[] alpha = new byte[size * size];
		float m = f + 1;
		float half = size / 2f - m; // half extent of the shape box
		float cx = size / 2f, cy = size / 2f;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float px = Math.abs(x + 0.5f - cx) - (half - r);
				float py = Math.abs(y + 0.5f - cy) - (half - r);
				float ox = Math.max(px, 0), oy = Math.max(py, 0);
				float d = (float) Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(px, py), 0) - r;
				float a;
				if (f > 0) {
					float t = clamp((d + f) / (2f * f), 0, 1);
					a = 1 - t * t * (3 - 2 * t);
				} else {
					a = clamp(0.5f - d, 0, 1);
				}
				if (bw > 0) {
					a *= clamp(0.5f + d + bw, 0, 1);
				}
				alpha[y * size + x] = (byte) Math.round(a * 255);
			}
		}
		atlas.putAlpha(at[0], at[1], size, size, alpha, size);
		return new Sprite(at[0], at[1], c, size, bw > 0);
	}

	private static float clamp(float v, float lo, float hi) {
		return v < lo ? lo : (v > hi ? hi : v);
	}
}
