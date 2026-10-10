package xyz.nativelaunch.ui.gfx;

/**
 * A tiny orthographic box renderer for previews (player model + cosmetics): Minecraft-style cubes with box UV
 * are transformed on the CPU, back faces culled, the rest depth-sorted and handed to the Canvas as free quads.
 * Coordinates are model pixels with y pointing down, exactly like vanilla entity models.
 */
public final class Boxes {
	private final float[] m = new float[12]; // 3x3 rotation (row-major) + translation
	private final float[] stack = new float[12 * 32];
	private int depth;
	private final float[] view = new float[9];
	private float cx, cy, scale;

	/**
	 * Faces are cut into one solid quad per texel and every texel is depth-sorted on its own. Sorting whole faces
	 * (or cubes) can't be right when cubes overlap: a cape, a hood around the head or blades crossing the back would
	 * paint over parts that are in front of them (the see-through look). Per texel there is no such overlap, and fully
	 * transparent texels are dropped, so hats, wings and blades show what is really behind them.
	 */
	private int quads;
	private float[] qx = new float[8 * 1024];
	private float[] qz = new float[1024];
	private int[] qc = new int[1024];
	private long[] keys = new long[1024];
	private static final Image WHITE = new Image(1, 1, new int[] {0xFFFFFFFF});
	private static final float[] WHITE_UV = {0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f};
	/** Texels per face side at most (big textures are sampled, not drawn texel by texel). */
	private static final int MAX_SIDE = 48;
	/** Draw faces that point away too (cosmetics: vanilla renders those without back-face culling). */
	public boolean twoSided;

	public void begin(float cx, float cy, float scale, float yaw, float pitch) {
		this.cx = cx;
		this.cy = cy;
		this.scale = scale;
		// view = Rx(pitch) * Ry(yaw)
		float cyw = (float) Math.cos(yaw), syw = (float) Math.sin(yaw), cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
		float[] ry = {cyw, 0, syw, 0, 1, 0, -syw, 0, cyw};
		float[] rx = {1, 0, 0, 0, cp, -sp, 0, sp, cp};
		mul3(rx, ry, view);
		identity();
		depth = 0;
		quads = 0;
		twoSided = false;
	}

	public void identity() {
		for (int i = 0; i < 12; i++) {
			m[i] = 0;
		}
		m[0] = m[4] = m[8] = 1;
	}

	public void push() {
		System.arraycopy(m, 0, stack, depth * 12, 12);
		depth++;
	}

	public void pop() {
		depth--;
		System.arraycopy(stack, depth * 12, m, 0, 12);
	}

	public void translate(float x, float y, float zz) {
		m[9] += m[0] * x + m[1] * y + m[2] * zz;
		m[10] += m[3] * x + m[4] * y + m[5] * zz;
		m[11] += m[6] * x + m[7] * y + m[8] * zz;
	}

	/** Vanilla ModelPart order: v' = Rz(roll) * Ry(yaw) * Rx(pitch) * v. */
	public void rotate(float pitch, float yaw, float roll) {
		if (roll != 0) {
			rot(2, roll);
		}
		if (yaw != 0) {
			rot(1, yaw);
		}
		if (pitch != 0) {
			rot(0, pitch);
		}
	}

	private void rot(int axis, float a) {
		float c = (float) Math.cos(a), s = (float) Math.sin(a);
		float[] r;
		if (axis == 0) {
			r = new float[] {1, 0, 0, 0, c, -s, 0, s, c};
		} else if (axis == 1) {
			r = new float[] {c, 0, s, 0, 1, 0, -s, 0, c};
		} else {
			r = new float[] {c, -s, 0, s, c, 0, 0, 0, 1};
		}
		float[] out = new float[9];
		float[] cur = {m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8]};
		mul3(cur, r, out);
		System.arraycopy(out, 0, m, 0, 9);
	}

	private static void mul3(float[] a, float[] b, float[] out) {
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) {
				out[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
			}
		}
	}

	private final float[] p = new float[3];

	private void project(float x, float y, float zz, float[] out, int o) {
		float wx = m[0] * x + m[1] * y + m[2] * zz + m[9];
		float wy = m[3] * x + m[4] * y + m[5] * zz + m[10];
		float wz = m[6] * x + m[7] * y + m[8] * zz + m[11];
		float vx = view[0] * wx + view[1] * wy + view[2] * wz;
		float vy = view[3] * wx + view[4] * wy + view[5] * wz;
		float vz = view[6] * wx + view[7] * wy + view[8] * wz;
		out[o] = cx + vx * scale;
		out[o + 1] = cy + vy * scale;
		p[2] = vz;
	}

	/** Normal (model space) to camera space z and a brightness. */
	private float[] normal(float nx, float ny, float nz) {
		float wx = m[0] * nx + m[1] * ny + m[2] * nz;
		float wy = m[3] * nx + m[4] * ny + m[5] * nz;
		float wz = m[6] * nx + m[7] * ny + m[8] * nz;
		float vx = view[0] * wx + view[1] * wy + view[2] * wz;
		float vy = view[3] * wx + view[4] * wy + view[5] * wz;
		float vz = view[6] * wx + view[7] * wy + view[8] * wz;
		return new float[] {vx, vy, vz};
	}

	/**
	 * One cube. (x, y, z) is the min corner, (w, h, d) the size in pixels, (u, v) the box-UV origin on a
	 * texW x texH texture.
	 */
	public void cube(Image texture, int texW, int texH, float x, float y, float zz, float w, float h, float d, float u, float v,
			float inflate, boolean mirror, int tint) {
		if (texture == null) {
			return;
		}
		float x0 = x - inflate, y0 = y - inflate, z0 = zz - inflate;
		float x1 = x + w + inflate, y1 = y + h + inflate, z1 = zz + d + inflate;
		float iu = 1f / texW, iv = 1f / texH;
		// region rects {u0, v0, u1, v1} in pixels
		float[] front = {u + d, v + d, u + d + w, v + d + h};
		float[] right = {u, v + d, u + d, v + d + h};
		float[] left = {u + d + w, v + d, u + d + w + d, v + d + h};
		float[] back = {u + d + w + d, v + d, u + d + w + d + w, v + d + h};
		float[] top = {u + d, v, u + d + w, v + d};
		float[] bottom = {u + d + w, v, u + d + w + w, v + d};
		if (mirror) {
			float[] t = right;
			right = left;
			left = t;
		}
		face(texture, tint, iu, iv, front, mirror, 0, 0, -1, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
		face(texture, tint, iu, iv, right, mirror, -1, 0, 0, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1);
		face(texture, tint, iu, iv, left, mirror, 1, 0, 0, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
		face(texture, tint, iu, iv, back, mirror, 0, 0, 1, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1);
		face(texture, tint, iu, iv, top, mirror, 0, -1, 0, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
		face(texture, tint, iu, iv, bottom, mirror, 0, 1, 0, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
	}

	private final float[] fa = new float[3], fb = new float[3], fd = new float[3];
	private float[] gx = new float[(MAX_SIDE + 1) * (MAX_SIDE + 1)], gy = new float[gx.length], gz = new float[gx.length];

	private void face(Image texture, int tint, float iu, float iv, float[] r, boolean mirror, float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz, float qqx, float qqy, float qqz, float dx, float dy, float dz) {
		if (r[2] - r[0] <= 0 || r[3] - r[1] <= 0) {
			return;
		}
		float[] n = normal(nx, ny, nz);
		if (n[2] > -0.0001f && n[2] < 0.0001f) {
			return; // edge-on
		}
		boolean away = n[2] > 0;
		if (away && !twoSided) {
			return;
		}
		if (away) {
			n[0] = -n[0];
			n[1] = -n[1];
			n[2] = -n[2]; // light the inside as the side we are looking at
		}
		float[] t2 = new float[2];
		project(ax, ay, az, t2, 0);
		fa[0] = t2[0]; fa[1] = t2[1]; fa[2] = p[2];
		project(bx, by, bz, t2, 0);
		fb[0] = t2[0]; fb[1] = t2[1]; fb[2] = p[2];
		project(dx, dy, dz, t2, 0);
		fd[0] = t2[0]; fd[1] = t2[1]; fd[2] = p[2];
		// light from the front, above and a little to the left
		float lit = Math.max(0f, n[0] * -0.35f + n[1] * -0.55f + n[2] * -0.76f);
		float bright = 0.58f + 0.42f * lit;

		// texel grid of the region, in real image pixels (textures can be bigger than the model's declared size)
		float sx = texture.width * iu, sy = texture.height * iv;
		float px0 = r[0] * sx, py0 = r[1] * sy, px1 = r[2] * sx, py1 = r[3] * sy;
		int cols = Math.max(1, Math.round(px1 - px0)), rows = Math.max(1, Math.round(py1 - py0));
		int gc = Math.min(cols, MAX_SIDE), gr = Math.min(rows, MAX_SIDE);
		int stride = gc + 1;
		for (int j = 0; j <= gr; j++) {
			float t = (float) j / gr;
			for (int i = 0; i <= gc; i++) {
				float u = (float) i / gc;
				int k = j * stride + i;
				gx[k] = fa[0] + (fb[0] - fa[0]) * u + (fd[0] - fa[0]) * t;
				gy[k] = fa[1] + (fb[1] - fa[1]) * u + (fd[1] - fa[1]) * t;
				gz[k] = fa[2] + (fb[2] - fa[2]) * u + (fd[2] - fa[2]) * t;
			}
		}
		int[] px = texture.argb;
		int tw = texture.width, th = texture.height;
		int ta = tint >>> 24, tr = (tint >> 16) & 255, tg = (tint >> 8) & 255, tb = tint & 255;
		for (int j = 0; j < gr; j++) {
			int sy0 = (int) (py0 + (j + 0.5f) * (py1 - py0) / gr);
			if (sy0 < 0 || sy0 >= th) {
				continue;
			}
			for (int i = 0; i < gc; i++) {
				float uu = (i + 0.5f) / gc;
				int sx0 = (int) (mirror ? px1 - uu * (px1 - px0) : px0 + uu * (px1 - px0));
				if (sx0 < 0 || sx0 >= tw) {
					continue;
				}
				int c = px[sy0 * tw + sx0];
				int a = (c >>> 24) * ta / 255;
				if (a < 8) {
					continue; // transparent texel: whatever is behind shows through
				}
				int rr = (int) (((c >> 16) & 255) * tr / 255 * bright);
				int gg = (int) (((c >> 8) & 255) * tg / 255 * bright);
				int bb = (int) ((c & 255) * tb / 255 * bright);
				add(j * stride + i, stride, a << 24 | rr << 16 | gg << 8 | bb);
			}
		}
	}

	private void add(int k, int stride, int color) {
		if (quads == qz.length) {
			int cap = quads * 2;
			qx = java.util.Arrays.copyOf(qx, cap * 8);
			qz = java.util.Arrays.copyOf(qz, cap);
			qc = java.util.Arrays.copyOf(qc, cap);
			keys = new long[cap];
		}
		int o = quads * 8;
		int k1 = k + 1, k2 = k + stride + 1, k3 = k + stride;
		qx[o] = gx[k];
		qx[o + 1] = gy[k];
		qx[o + 2] = gx[k1];
		qx[o + 3] = gy[k1];
		qx[o + 4] = gx[k2];
		qx[o + 5] = gy[k2];
		qx[o + 6] = gx[k3];
		qx[o + 7] = gy[k3];
		qz[quads] = (gz[k] + gz[k1] + gz[k2] + gz[k3]) * 0.25f;
		qc[quads] = color;
		quads++;
	}

	private final float[] qxy = new float[8];

	/** Sorts every texel back to front (larger view z = farther) and draws them as one batch. */
	public void end(Canvas c) {
		for (int i = 0; i < quads; i++) {
			int bits = Float.floatToIntBits(qz[i]);
			bits ^= (bits >> 31) & 0x7FFFFFFF; // order-preserving int for floats
			keys[i] = ((long) (-(long) bits - 1) << 32) | (i & 0xFFFFFFFFL); // descending z
		}
		java.util.Arrays.sort(keys, 0, quads);
		for (int k = 0; k < quads; k++) {
			int i = (int) keys[k];
			System.arraycopy(qx, i * 8, qxy, 0, 8);
			c.freeQuad(WHITE, qxy, WHITE_UV, qc[i]);
		}
		quads = 0;
	}
}
