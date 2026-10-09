package xyz.nativelaunch.ui.gfx;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** A decoded image (ARGB). Large ones (backgrounds) get their own texture; small ones go into the atlas. */
public final class Image {
	public final int width, height;
	public final int[] argb;
	/** Backend texture handle (0 = not uploaded yet). */
	public int handle;
	public boolean linear = true;

	public Image(int width, int height, int[] argb) {
		this.width = width;
		this.height = height;
		this.argb = argb;
	}

	public static Image resource(String path) {
		try (InputStream in = Image.class.getResourceAsStream(path)) {
			if (in == null) {
				return null;
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[65536];
			int n;
			while ((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
			}
			return decode(out.toByteArray());
		} catch (Throwable t) {
			return null;
		}
	}

	/** PNG / JPEG via stb_image. */
	public static Image decode(byte[] bytes) {
		ByteBuffer src = MemoryUtil.memAlloc(bytes.length);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			src.put(bytes).flip();
			IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), comp = stack.mallocInt(1);
			ByteBuffer px = STBImage.stbi_load_from_memory(src, w, h, comp, 4);
			if (px == null) {
				return null;
			}
			try {
				int width = w.get(0), height = h.get(0);
				int[] argb = new int[width * height];
				for (int i = 0; i < argb.length; i++) {
					int r = px.get(i * 4) & 0xFF, g = px.get(i * 4 + 1) & 0xFF, b = px.get(i * 4 + 2) & 0xFF, a = px.get(i * 4 + 3) & 0xFF;
					argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
				}
				return new Image(width, height, argb);
			} finally {
				STBImage.stbi_image_free(px);
			}
		} catch (Throwable t) {
			return null;
		} finally {
			MemoryUtil.memFree(src);
		}
	}

	/** Area-averaged downscale (premultiplied), for crisp small logos. */
	public Image scaled(int w, int h) {
		w = Math.max(1, w);
		h = Math.max(1, h);
		int[] out = new int[w * h];
		float sx = (float) width / w, sy = (float) height / h;
		for (int y = 0; y < h; y++) {
			int y0 = (int) (y * sy), y1 = Math.max(y0 + 1, (int) ((y + 1) * sy));
			for (int x = 0; x < w; x++) {
				int x0 = (int) (x * sx), x1 = Math.max(x0 + 1, (int) ((x + 1) * sx));
				long a = 0, r = 0, g = 0, b = 0;
				int n = 0;
				for (int yy = y0; yy < y1 && yy < height; yy++) {
					for (int xx = x0; xx < x1 && xx < width; xx++) {
						int c = argb[yy * width + xx];
						int ca = c >>> 24;
						a += ca;
						r += ((c >> 16) & 0xFF) * ca;
						g += ((c >> 8) & 0xFF) * ca;
						b += (c & 0xFF) * ca;
						n++;
					}
				}
				if (n == 0 || a == 0) {
					continue;
				}
				out[y * w + x] = ((int) (a / n) << 24) | ((int) (r / a) << 16) | ((int) (g / a) << 8) | (int) (b / a);
			}
		}
		return new Image(w, h, out);
	}
}
