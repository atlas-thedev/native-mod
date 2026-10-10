package xyz.nativelaunch.ui;

import org.lwjgl.PointerBuffer;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import xyz.nativelaunch.core.Http;
import xyz.nativelaunch.ui.gfx.Image;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pictures and animated GIFs shown inside chat messages: downloaded off-thread, decoded, cached (newest 80). */
public final class ChatMedia {
	/** One picture: a single frame, or the frames of a GIF with their delays. */
	public static final class Media {
		public volatile Image[] frames;
		public volatile int[] delays;
		public volatile boolean failed;
		public int width, height;
		private int total;

		public boolean ready() {
			return frames != null;
		}

		public Image frame(long now) {
			Image[] f = frames;
			if (f == null) {
				return null;
			}
			if (f.length == 1 || total <= 0) {
				return f[0];
			}
			int t = (int) (now % total);
			for (int i = 0; i < f.length; i++) {
				t -= delays[i];
				if (t < 0) {
					return f[i];
				}
			}
			return f[0];
		}

		public boolean animated() {
			Image[] f = frames;
			return f != null && f.length > 1;
		}
	}

	private static final Map<String, Media> cache = new LinkedHashMap<String, Media>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Media> eldest) {
			return size() > 80;
		}
	};
	private static final ExecutorService pool = Executors.newFixedThreadPool(3, r -> {
		Thread t = new Thread(r, "Native-Media");
		t.setDaemon(true);
		return t;
	});

	private ChatMedia() {
	}

	/** Only Native uploads and Giphy are ever fetched. */
	public static boolean allowed(String url, String api) {
		if (url == null) {
			return false;
		}
		if (api != null && url.startsWith(api + "/v1/social/media/")) {
			return true;
		}
		return url.matches("https://media[0-9]*\\.giphy\\.com/media/.+\\.gif(\\?.*)?");
	}

	/** The picture for a URL; starts loading on first call. Never blocks. */
	public static Media get(final String url, final String api) {
		synchronized (cache) {
			Media m = cache.get(url);
			if (m != null) {
				return m;
			}
			final Media fresh = new Media();
			cache.put(url, fresh);
			if (!allowed(url, api)) {
				fresh.failed = true;
				return fresh;
			}
			String fetch = url.contains("giphy.com") ? url.replaceAll("/giphy\\.gif", "/200w.gif") : url;
			final String target = fetch;
			pool.execute(() -> {
				try {
					fill(fresh, Http.getBytes(target, 10 * 1024 * 1024));
				} catch (Throwable t) {
					fresh.failed = true;
				}
			});
			return fresh;
		}
	}

	/** Shows a picture we already have (just pasted) before the server has it. */
	public static Media putLocal(String key, byte[] bytes) {
		Media m = new Media();
		try {
			fill(m, bytes);
		} catch (Throwable t) {
			m.failed = true;
		}
		synchronized (cache) {
			cache.put(key, m);
		}
		return m;
	}

	private static void fill(Media m, byte[] bytes) {
		boolean gif = bytes.length > 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F';
		if (gif && decodeGif(m, bytes)) {
			return;
		}
		Image img = Image.decode(bytes);
		if (img == null) {
			m.failed = true;
			return;
		}
		if (img.width > 1280) {
			img = img.scaled(1280, Math.max(1, img.height * 1280 / img.width));
		}
		m.width = img.width;
		m.height = img.height;
		m.delays = new int[] {100};
		m.frames = new Image[] {img};
	}

	private static boolean decodeGif(Media m, byte[] bytes) {
		ByteBuffer src = MemoryUtil.memAlloc(bytes.length);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			src.put(bytes).flip();
			PointerBuffer delays = stack.mallocPointer(1);
			IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), z = stack.mallocInt(1), comp = stack.mallocInt(1);
			ByteBuffer px = STBImage.stbi_load_gif_from_memory(src, delays, w, h, z, comp, 4);
			if (px == null) {
				return false;
			}
			try {
				int width = w.get(0), height = h.get(0), count = Math.max(1, z.get(0));
				int step = Math.max(1, (count + 79) / 80);
				IntBuffer d = delays.getIntBuffer(0, count);
				int use = (count + step - 1) / step;
				Image[] frames = new Image[use];
				int[] times = new int[use];
				int total = 0;
				for (int f = 0, i = 0; f < count; f += step, i++) {
					int[] argb = new int[width * height];
					int base = f * width * height * 4;
					for (int p = 0; p < argb.length; p++) {
						int o = base + p * 4;
						argb[p] = ((px.get(o + 3) & 0xFF) << 24) | ((px.get(o) & 0xFF) << 16) | ((px.get(o + 1) & 0xFF) << 8) | (px.get(o + 2) & 0xFF);
					}
					Image img = new Image(width, height, argb);
					if (width > 300) {
						img = img.scaled(300, Math.max(1, height * 300 / width));
					}
					frames[i] = img;
					int delay = 0;
					for (int k = f; k < Math.min(count, f + step); k++) {
						delay += Math.max(20, d.get(k));
					}
					times[i] = delay;
					total += delay;
				}
				m.width = frames[0].width;
				m.height = frames[0].height;
				m.delays = times;
				m.total = total;
				m.frames = frames;
				return true;
			} finally {
				STBImage.stbi_image_free(px);
			}
		} catch (Throwable t) {
			return false;
		} finally {
			MemoryUtil.memFree(src);
		}
	}
}
