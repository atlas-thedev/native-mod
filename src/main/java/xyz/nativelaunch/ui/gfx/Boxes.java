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

	private int faces;
	private float[] xy = new float[8 * 256];
	private float[] uv = new float[8 * 256];
	private float[] z = new float[256];
	private int[] color = new int[256];
	private Image[] tex = new Image[256];
	private Integer[] order = new Integer[256];

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
		faces = 0;
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

	private void face(Image texture, int tint, float iu, float iv, float[] r, boolean mirror, float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz, float qx, float qy, float qz, float dx, float dy, float dz) {
		if (r[2] - r[0] <= 0 || r[3] - r[1] <= 0) {
			return;
		}
		float[] n = normal(nx, ny, nz);
		if (n[2] >= -0.0001f) {
			return; // facing away
		}
		if (faces == z.length) {
			int cap = faces * 2;
			xy = java.util.Arrays.copyOf(xy, cap * 8);
			uv = java.util.Arrays.copyOf(uv, cap * 8);
			z = java.util.Arrays.copyOf(z, cap);
			color = java.util.Arrays.copyOf(color, cap);
			tex = java.util.Arrays.copyOf(tex, cap);
			order = java.util.Arrays.copyOf(order, cap);
		}
		int o = faces * 8;
		float zs = 0;
		project(ax, ay, az, xy, o);
		zs += p[2];
		project(bx, by, bz, xy, o + 2);
		zs += p[2];
		project(qx, qy, qz, xy, o + 4);
		zs += p[2];
		project(dx, dy, dz, xy, o + 6);
		zs += p[2];
		float e = 0.02f; // keep nearest sampling inside the region
		float u0 = (r[0] + e) * iu, v0 = (r[1] + e) * iv, u1 = (r[2] - e) * iu, v1 = (r[3] - e) * iv;
		if (mirror) {
			float t = u0;
			u0 = u1;
			u1 = t;
		}
		uv[o] = u0;
		uv[o + 1] = v0;
		uv[o + 2] = u1;
		uv[o + 3] = v0;
		uv[o + 4] = u1;
		uv[o + 5] = v1;
		uv[o + 6] = u0;
		uv[o + 7] = v1;
		z[faces] = zs / 4;
		// light from the front, above and a little to the left
		float lx = -0.35f, ly = -0.55f, lz = -0.76f;
		float lit = Math.max(0f, n[0] * lx + n[1] * ly + n[2] * lz);
		float b = 0.58f + 0.42f * lit;
		int a = tint >>> 24, rr = (int) (((tint >> 16) & 255) * b), gg = (int) (((tint >> 8) & 255) * b), bb = (int) ((tint & 255) * b);
		color[faces] = a << 24 | rr << 16 | gg << 8 | bb;
		tex[faces] = texture;
		faces++;
	}

	private final float[] qxy = new float[8], quv = new float[8];

	/** Sorts back to front and draws. */
	public void end(Canvas c) {
		for (int i = 0; i < faces; i++) {
			order[i] = i;
		}
		final float[] zz = z;
		java.util.Arrays.sort(order, 0, faces, (a, b) -> Float.compare(zz[b], zz[a]));
		float s = 1f / c.scale;
		for (int k = 0; k < faces; k++) {
			int i = order[k];
			for (int j = 0; j < 8; j++) {
				qxy[j] = xy[i * 8 + j] * 1f;
				quv[j] = uv[i * 8 + j];
			}
			c.freeQuad(tex[i], qxy, quv, color[i]);
		}
		faces = 0;
	}
}
