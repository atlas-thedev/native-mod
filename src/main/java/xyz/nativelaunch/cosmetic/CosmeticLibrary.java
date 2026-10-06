package xyz.nativelaunch.cosmetic;

import xyz.nativelaunch.core.Log;
import xyz.nativelaunch.core.NativeState;
import xyz.nativelaunch.core.SkinDirectory;
import xyz.nativelaunch.core.TextureCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Downloads (and disk-caches) cosmetic models and textures by hash, off the render thread. Lookups never block:
 * a cosmetic simply appears once its files are ready.
 */
public final class CosmeticLibrary {
	static final int MAX_MODEL_BYTES = 256 * 1024;
	static final int MAX_TEXTURE_BYTES = 1024 * 1024;
	private static final long RETRY_MS = 60_000;

	/** A cosmetic ready to draw. {@link #baked} belongs to the running version's renderer. */
	public static final class Loaded {
		public final CosmeticRef ref;
		public final CosmeticModel model;
		public final byte[] png;
		public volatile Object baked;

		Loaded(CosmeticRef ref, CosmeticModel model, byte[] png) {
			this.ref = ref;
			this.model = model;
			this.png = png;
		}
	}

	private static final class Slot {
		volatile Loaded loaded;
		volatile boolean loading;
		volatile long retryAt;
	}

	private static final Map<String, Slot> SLOTS = new ConcurrentHashMap<String, Slot>();
	private static final ExecutorService WORKER = Executors.newFixedThreadPool(3, new ThreadFactory() {
		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, "Native cosmetics");
			t.setDaemon(true);
			return t;
		}
	});

	private CosmeticLibrary() {
	}

	/** The cosmetics a player wears that are ready to draw (possibly empty, never null). */
	public static List<Loaded> worn(String name, UUID id) {
		NativeState state = NativeState.get();
		SkinDirectory directory = state.directory();
		List<CosmeticRef> refs = directory.cosmetics(name, id);
		if (refs.isEmpty()) {
			return Collections.emptyList();
		}
		List<Loaded> out = new ArrayList<Loaded>(refs.size());
		for (CosmeticRef ref : refs) {
			Loaded loaded = get(ref, directory.textureBase());
			if (loaded != null) {
				out.add(loaded);
			}
		}
		return out;
	}

	/** The cosmetic when it is ready; otherwise starts (or waits for) its download and returns null. */
	public static Loaded get(final CosmeticRef ref, String textureBase) {
		Slot slot = SLOTS.get(ref.key());
		if (slot == null) {
			slot = new Slot();
			Slot raced = SLOTS.putIfAbsent(ref.key(), slot);
			if (raced != null) {
				slot = raced;
			}
		}
		Loaded loaded = slot.loaded;
		if (loaded != null) {
			return loaded;
		}
		if (slot.loading || System.currentTimeMillis() < slot.retryAt) {
			return null;
		}
		slot.loading = true;
		final Slot target = slot;
		final String base = textureBase == null || textureBase.isEmpty() ? NativeState.DEFAULT_API + "/csl/textures/" : textureBase;
		WORKER.execute(new Runnable() {
			@Override
			public void run() {
				try {
					byte[] modelBytes = TextureCache.getOrDownload(base, ref.modelHash, MAX_MODEL_BYTES);
					byte[] png = TextureCache.getOrDownload(base, ref.textureHash, MAX_TEXTURE_BYTES);
					if (png == null || png.length < 8 || (png[0] & 0xff) != 0x89 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') {
						throw new IllegalArgumentException("texture is not a PNG");
					}
					target.loaded = new Loaded(ref, CosmeticModel.parse(modelBytes), png);
				} catch (Throwable t) {
					target.retryAt = System.currentTimeMillis() + RETRY_MS;
					Log.warn("Cosmetic {} is unavailable ({}).", ref.id, t.toString());
				} finally {
					target.loading = false;
				}
			}
		});
		return null;
	}

	/**
	 * Warms the cosmetics players wear (from the launcher's shared disk cache, or the network when it isn't
	 * there yet) so they are ready the moment a player comes into view. Cheap: one request per distinct cosmetic.
	 */
	public static void preload(java.util.List<CosmeticRef> refs, String textureBase) {
		if (refs == null || refs.isEmpty()) {
			return;
		}
		for (CosmeticRef ref : refs) {
			get(ref, textureBase);
		}
	}

	/** Test hook: put a ready cosmetic in place. */
	static void put(Loaded loaded) {
		Slot slot = new Slot();
		slot.loaded = loaded;
		SLOTS.put(loaded.ref.key(), slot);
	}

	public static Loaded create(CosmeticRef ref, CosmeticModel model, byte[] png) {
		return new Loaded(ref, model, png);
	}
}
