package xyz.nativelaunch.ui.gfx;

/**
 * One RGBA texture that holds every glyph, icon, rounded-corner sprite and player face the Native UI draws,
 * so a whole frame is a single texture bind. Regions are packed into shelves; when the atlas fills up it is
 * cleared at the start of the next frame and everything re-bakes on demand (bumping {@link #generation}).
 * Sampled with nearest filtering: every sprite is baked at the exact pixel size it is drawn at.
 */
public final class Atlas {
	public final int size;
	public final byte[] pixels; // RGBA, row-major
	private int shelfY;
	private int shelfX;
	private int shelfH;
	private boolean full;
	private int dirtyX0 = Integer.MAX_VALUE, dirtyY0 = Integer.MAX_VALUE, dirtyX1, dirtyY1;
	/** Bumped on every reset: caches holding atlas regions compare against it. */
	public int generation;
	/** Pixel centre of an always-white texel, for solid fills. */
	public final float whiteU, whiteV;

	public Atlas(int size) {
		this.size = size;
		this.pixels = new byte[size * size * 4];
		this.whiteU = 1.5f / size;
		this.whiteV = 1.5f / size;
		reset();
	}

	public void reset() {
		java.util.Arrays.fill(pixels, (byte) 0);
		for (int y = 0; y < 4; y++) {
			for (int x = 0; x < 4; x++) {
				int i = (y * size + x) * 4;
				pixels[i] = pixels[i + 1] = pixels[i + 2] = pixels[i + 3] = (byte) 255;
			}
		}
		shelfX = 6;
		shelfY = 0;
		shelfH = 6;
		full = false;
		generation++;
		markDirty(0, 0, size, size);
	}

	/** True once an allocation failed: the owner resets the atlas before the next frame. */
	public boolean isFull() {
		return full;
	}

	/** Reserves a w x h region (with a 1 px gutter); returns {x, y} or null when there is no room left. */
	public int[] alloc(int w, int h) {
		int pw = w + 1, ph = h + 1;
		if (pw > size || ph > size) {
			return null;
		}
		if (shelfX + pw > size) {
			shelfY += shelfH;
			shelfX = 0;
			shelfH = 0;
		}
		if (shelfY + ph > size) {
			full = true;
			return null;
		}
		int[] at = {shelfX, shelfY};
		shelfX += pw;
		shelfH = Math.max(shelfH, ph);
		return at;
	}

	/** Writes white with the given coverage (0-255) per pixel. */
	public void putAlpha(int x, int y, int w, int h, byte[] alpha, int stride) {
		for (int row = 0; row < h; row++) {
			int dst = ((y + row) * size + x) * 4;
			int src = row * stride;
			for (int col = 0; col < w; col++) {
				pixels[dst] = pixels[dst + 1] = pixels[dst + 2] = (byte) 255;
				pixels[dst + 3] = alpha[src + col];
				dst += 4;
			}
		}
		markDirty(x, y, w, h);
	}

	/** Writes straight ARGB pixels. */
	public void putArgb(int x, int y, int w, int h, int[] argb) {
		for (int row = 0; row < h; row++) {
			int dst = ((y + row) * size + x) * 4;
			for (int col = 0; col < w; col++) {
				int c = argb[row * w + col];
				pixels[dst] = (byte) (c >>> 16);
				pixels[dst + 1] = (byte) (c >>> 8);
				pixels[dst + 2] = (byte) c;
				pixels[dst + 3] = (byte) (c >>> 24);
				dst += 4;
			}
		}
		markDirty(x, y, w, h);
	}

	private void markDirty(int x, int y, int w, int h) {
		dirtyX0 = Math.min(dirtyX0, x);
		dirtyY0 = Math.min(dirtyY0, y);
		dirtyX1 = Math.max(dirtyX1, x + w);
		dirtyY1 = Math.max(dirtyY1, y + h);
	}

	/** The rectangle changed since the last call ({x, y, w, h}), or null; clears it. */
	public int[] takeDirty() {
		if (dirtyX0 >= dirtyX1 || dirtyY0 >= dirtyY1) {
			return null;
		}
		int[] r = {dirtyX0, dirtyY0, dirtyX1 - dirtyX0, dirtyY1 - dirtyY0};
		dirtyX0 = dirtyY0 = Integer.MAX_VALUE;
		dirtyX1 = dirtyY1 = 0;
		return r;
	}
}
