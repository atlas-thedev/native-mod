package xyz.nativelaunch.glanim;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import xyz.nativelaunch.core.CapeTextureIds;
import xyz.nativelaunch.core.TextureCache;
import xyz.nativelaunch.core.Log;
import xyz.nativelaunch.core.NativeState;
import xyz.nativelaunch.core.SkinEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.stb.STBImage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Plays Native animated capes on Minecraft 1.16 - 1.21.x.
 *
 * The game downloads an animated cape's first frame like any other cape and keeps it in
 * its texture manager under {@code skins/<sha1>} (up to 1.20.1) or {@code capes/<sha1>}.
 * This animator never replaces that texture: it decodes the animation strip once and, on
 * the render thread, writes the current frame into the very same OpenGL texture with
 * {@code glTexSubImage2D}. Every renderer that draws the cape (player model, elytra,
 * inventory preview) therefore animates, and nothing breaks if the animator is unavailable.
 *
 * Minecraft classes are reached by their Fabric <i>intermediary</i> names, which stay the
 * same across every version, so one jar covers the whole range:
 * <pre>
 *   Minecraft.getInstance()            class_310.method_1551
 *   Minecraft.getTextureManager()      class_310.method_1531
 *   TextureManager.byPath              class_1060.field_5286
 *   AbstractTexture.getId()            class_1044.method_4624     (1.16 - 1.21.4)
 *   AbstractTexture.getTexture()       class_1044.method_68004    (1.21.5+)
 *   GlTexture.glId()                   class_10868.method_68427   (1.21.5+)
 * </pre>
 * The real-client job in {@code ci/client} (1.16.5 - 1.21.11) fails if any of these names stops resolving.
 */
public final class GlCapeAnimator {
	private static final int MAX_STRIP_BYTES = 16 * 1024 * 1024;
	private static final long POLL_MS = 1000;
	private static final long RETRY_MS = 60_000;

	private static final String MINECRAFT = "net.minecraft.class_310";
	private static final String TEXTURE_MANAGER = "net.minecraft.class_1060";
	private static final String ABSTRACT_TEXTURE = "net.minecraft.class_1044";
	private static final String GL_TEXTURE = "net.minecraft.class_10868";

	/** Downloaded strips by strip hash (decoded lazily on the render thread). */
	private static final Map<String, byte[]> BYTES = new ConcurrentHashMap<String, byte[]>();
	private static final Map<String, Long> FAILED = new ConcurrentHashMap<String, Long>();
	/** What should animate right now: texture sha1 -> animation. Replaced as a whole by the poll thread. */
	private static volatile Map<String, Wanted> wanted = Collections.emptyMap();

	/** Render thread only. */
	private static final Map<String, Strip> STRIPS = new HashMap<String, Strip>();
	/** Render thread only: the `wanted` map STRIPS was last pruned against. */
	private static Map<String, Wanted> pruned;
	private static final Map<Object, Boolean> MISMATCHED = new WeakHashMap<Object, Boolean>();

	private static volatile boolean started;
	private static volatile boolean broken;
	private static volatile boolean queued;
	private static final long START_NANOS = System.nanoTime();

	private static Method getInstance;
	private static Method getTextureManager;
	private static Field byPath;
	private static Method getId;      // <= 1.21.4
	private static Method getTexture; // 1.21.5+
	private static String glIdName;   // 1.21.5+

	private GlCapeAnimator() {
	}

	/** One animated cape: the strip to play and how fast. */
	private static final class Wanted {
		final String stripHash;
		final int frames;
		final int fps;

		Wanted(String stripHash, int frames, int fps) {
			this.stripHash = stripHash;
			this.frames = frames;
			this.fps = fps;
		}
	}

	/** A decoded strip: RGBA pixels, frames stacked top to bottom. */
	private static final class Strip {
		final ByteBuffer pixels;
		final int width;
		final int frameHeight;
		final int frames;

		Strip(ByteBuffer pixels, int width, int frameHeight, int frames) {
			this.pixels = pixels;
			this.width = width;
			this.frameHeight = frameHeight;
			this.frames = frames;
		}

		ByteBuffer frame(int index) {
			ByteBuffer view = pixels.duplicate();
			int size = width * frameHeight * 4;
			view.position(index * size);
			view.limit(index * size + size);
			return view.slice();
		}
	}

	/** Called reflectively from NativeMod on Minecraft 1.16 - 1.21.x. Idempotent. */
	public static synchronized void start() throws ReflectiveOperationException {
		if (started) {
			return;
		}
		resolve();
		started = true;
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				loop();
			}
		}, "Native-CapeAnimator");
		thread.setDaemon(true);
		thread.start();
		Log.info("Animated capes enabled ({}).", getId != null ? "GL texture id" : "GPU texture");
	}

	private static void resolve() throws ReflectiveOperationException {
		MappingResolver mappings = FabricLoader.getInstance().getMappingResolver();
		Class<?> minecraft = Class.forName(mappings.mapClassName("intermediary", MINECRAFT));
		Class<?> textureManager = Class.forName(mappings.mapClassName("intermediary", TEXTURE_MANAGER));
		Class<?> abstractTexture = Class.forName(mappings.mapClassName("intermediary", ABSTRACT_TEXTURE));

		getInstance = minecraft.getMethod(mappings.mapMethodName("intermediary", MINECRAFT, "method_1551", "()L" + MINECRAFT.replace('.', '/') + ";"));
		getTextureManager = minecraft.getMethod(mappings.mapMethodName("intermediary", MINECRAFT, "method_1531", "()L" + TEXTURE_MANAGER.replace('.', '/') + ";"));
		byPath = textureManager.getDeclaredField(mappings.mapFieldName("intermediary", TEXTURE_MANAGER, "field_5286", "Ljava/util/Map;"));
		byPath.setAccessible(true);

		try {
			getId = abstractTexture.getMethod(mappings.mapMethodName("intermediary", ABSTRACT_TEXTURE, "method_4624", "()I"));
		} catch (NoSuchMethodException e) {
			getId = null;
		}
		if (getId == null) {
			getTexture = abstractTexture.getMethod(mappings.mapMethodName("intermediary", ABSTRACT_TEXTURE, "method_68004", "()Lcom/mojang/blaze3d/textures/GpuTexture;"));
			glIdName = mappings.mapMethodName("intermediary", GL_TEXTURE, "method_68427", "()I");
		}
	}

	private static void loop() {
		long nextPoll = 0;
		while (!broken) {
			try {
				long now = System.currentTimeMillis();
				if (now >= nextPoll) {
					poll();
					nextPoll = now + POLL_MS;
				}
				Map<String, Wanted> current = wanted;
				int fps = 1;
				for (Wanted want : current.values()) {
					fps = Math.max(fps, want.fps);
				}
				if (!current.isEmpty()) {
					schedule();
				}
				Thread.sleep(current.isEmpty() ? POLL_MS : Math.max(16, 1000 / fps));
			} catch (InterruptedException e) {
				return;
			} catch (Throwable t) {
				Log.warn("Animated cape update failed: {}", t.toString());
			}
		}
	}

	/** Downloads strips for every animated cape in the directory (off the render thread). */
	private static void poll() {
		String base = NativeState.get().directory().textureBase();
		if (base.isEmpty()) {
			return;
		}
		Map<String, Wanted> next = new HashMap<String, Wanted>();
		for (SkinEntry entry : NativeState.get().directory().animated()) {
			if (fetch(base, entry.capeStripHash)) {
				next.put(CapeTextureIds.sha1OfChars(textureHash(base + entry.capeHash)), new Wanted(entry.capeStripHash, entry.capeFrames, entry.capeFps));
			}
		}
		wanted = next;
		// strips of players who left (or changed cloaks) are dropped instead of piling up (each can be up to 16 MB)
		java.util.Set<String> live = new java.util.HashSet<String>();
		for (Wanted w : next.values()) {
			live.add(w.stripHash);
		}
		for (SkinEntry entry : NativeState.get().directory().animated()) {
			live.add(entry.capeStripHash);
		}
		BYTES.keySet().retainAll(live);
	}

	private static String textureHash(String url) {
		try {
			return new MinecraftProfileTexture(url, Collections.<String, String>emptyMap()).getHash();
		} catch (Throwable t) {
			return CapeTextureIds.textureHash(url);
		}
	}

	private static boolean fetch(String base, String stripHash) {
		if (BYTES.containsKey(stripHash)) {
			return true;
		}
		Long failedAt = FAILED.get(stripHash);
		if (failedAt != null && System.currentTimeMillis() - failedAt < RETRY_MS) {
			return false;
		}
		try {
			BYTES.put(stripHash, TextureCache.getOrDownload(base, stripHash, MAX_STRIP_BYTES));
			FAILED.remove(stripHash);
			return true;
		} catch (Exception e) {
			FAILED.put(stripHash, System.currentTimeMillis());
			Log.warn("Could not download an animated cape: {}", e.toString());
			return false;
		}
	}

	/** Queues one frame update on the render thread (never more than one at a time). */
	private static void schedule() throws ReflectiveOperationException {
		if (queued) {
			return;
		}
		Object minecraft = getInstance.invoke(null);
		if (!(minecraft instanceof Executor)) {
			return;
		}
		queued = true;
		((Executor) minecraft).execute(new Runnable() {
			@Override
			public void run() {
				queued = false;
				try {
					apply(minecraft);
				} catch (LinkageError e) {
					broken = true;
					Log.warn("Animated capes are not supported on this Minecraft build: {}", e.toString());
				} catch (Throwable t) {
					Log.warn("Could not animate a cape: {}", t.toString());
				}
			}
		});
	}

	/** Render thread: write the current frame of every animated cape that is loaded. */
	private static void apply(Object minecraft) throws ReflectiveOperationException {
		Map<String, Wanted> current = wanted;
		if (current != pruned) {
			// decoded strips are only kept for capes that still animate
			pruned = current;
			java.util.Set<String> live = new java.util.HashSet<String>();
			for (Wanted w : current.values()) {
				live.add(w.stripHash);
			}
			STRIPS.keySet().retainAll(live);
		}
		if (current.isEmpty()) {
			return;
		}
		Object manager = getTextureManager.invoke(minecraft);
		if (manager == null) {
			return;
		}
		Map<?, ?> loaded = (Map<?, ?>) byPath.get(manager);
		List<Object[]> targets = new ArrayList<Object[]>();
		for (Map.Entry<?, ?> entry : loaded.entrySet()) {
			String id = String.valueOf(entry.getKey());
			int slash = id.lastIndexOf('/');
			if (slash < 0) {
				continue;
			}
			Wanted want = current.get(id.substring(slash + 1));
			if (want != null && CapeTextureIds.matches(id, id.substring(slash + 1)) && entry.getValue() != null) {
				targets.add(new Object[] {entry.getValue(), want});
			}
		}
		if (targets.isEmpty()) {
			return;
		}
		long elapsed = System.nanoTime() - START_NANOS;
		for (Object[] target : targets) {
			Object texture = target[0];
			Wanted want = (Wanted) target[1];
			if (MISMATCHED.containsKey(texture)) {
				continue;
			}
			Strip strip = strip(want);
			if (strip == null) {
				continue;
			}
			int glId = glId(texture);
			if (glId <= 0) {
				continue;
			}
			long frameNanos = Math.max(1L, 1_000_000_000L / Math.max(1, want.fps));
			int frame = (int) ((elapsed / frameNanos) % strip.frames);
			if (!upload(glId, strip, frame)) {
				MISMATCHED.put(texture, Boolean.TRUE);
			}
		}
	}

	private static int glId(Object texture) {
		try {
			if (getId != null) {
				return (Integer) getId.invoke(texture);
			}
			Object gpu = getTexture.invoke(texture);
			if (gpu == null) {
				return 0;
			}
			Method glId = gpu.getClass().getMethod(glIdName);
			return (Integer) glId.invoke(gpu);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0; // not uploaded yet, or not a GL texture: try again next frame
		}
	}

	/** Decodes a strip once per hash (render thread, STB like the game itself). */
	private static Strip strip(Wanted want) {
		Strip cached = STRIPS.get(want.stripHash);
		if (cached != null && cached.frames == want.frames) {
			return cached;
		}
		byte[] bytes = BYTES.get(want.stripHash);
		if (bytes == null) {
			return null;
		}
		ByteBuffer encoded = ByteBuffer.allocateDirect(bytes.length);
		encoded.put(bytes).flip();
		IntBuffer w = ByteBuffer.allocateDirect(4).order(java.nio.ByteOrder.nativeOrder()).asIntBuffer();
		IntBuffer h = ByteBuffer.allocateDirect(4).order(java.nio.ByteOrder.nativeOrder()).asIntBuffer();
		IntBuffer c = ByteBuffer.allocateDirect(4).order(java.nio.ByteOrder.nativeOrder()).asIntBuffer();
		ByteBuffer decoded = STBImage.stbi_load_from_memory(encoded, w, h, c, 4);
		if (decoded == null) {
			drop(want, "could not be decoded");
			return null;
		}
		int width = w.get(0);
		int height = h.get(0);
		try {
			if (want.frames < 2 || height % want.frames != 0 || height < want.frames) {
				drop(want, "is " + width + "x" + height + " and does not split into " + want.frames + " frames");
				return null;
			}
			ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
			pixels.put(decoded).flip();
			Strip strip = new Strip(pixels, width, height / want.frames, want.frames);
			STRIPS.put(want.stripHash, strip);
			return strip;
		} finally {
			STBImage.stbi_image_free(decoded);
		}
	}

	private static void drop(Wanted want, String why) {
		Log.warn("Animated cape strip {}.", why);
		BYTES.remove(want.stripHash);
		FAILED.put(want.stripHash, System.currentTimeMillis());
	}

	/**
	 * Writes one frame into the texture. Every piece of GL state touched is restored, so the
	 * game's own state cache stays correct. False when the texture's size does not match the
	 * frame (a resized or replaced texture): that texture is then left alone.
	 */
	private static boolean upload(int glId, Strip strip, int frame) {
		int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		int unpackBuffer = GL11.glGetInteger(0x88EF); // GL_PIXEL_UNPACK_BUFFER_BINDING
		int rowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
		int skipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
		int skipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
		int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
		try {
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, glId);
			int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
			int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
			if (width == 0 || height == 0) {
				return true; // allocated but not uploaded yet: try again next frame
			}
			if (width != strip.width || height != strip.frameHeight) {
				Log.warn("Animated cape frame is {}x{} but the cape texture is {}x{}: showing it still.", strip.width, strip.frameHeight, width, height);
				return false;
			}
			if (unpackBuffer != 0) {
				GL15.glBindBuffer(0x88EC, 0); // GL_PIXEL_UNPACK_BUFFER
			}
			GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
			GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
			GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
			GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, strip.width, strip.frameHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, strip.frame(frame));
			return true;
		} finally {
			GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
			GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
			GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
			if (unpackBuffer != 0) {
				GL15.glBindBuffer(0x88EC, unpackBuffer);
			}
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
		}
	}
}
