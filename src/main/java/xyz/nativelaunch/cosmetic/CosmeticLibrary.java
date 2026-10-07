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
	static final int MAX_MODEL_BYTES = 1024 * 1024;
	static final int MAX_TEXTURE_BYTES = 2 * 1024 * 1024;
	private static final long RETRY_MS = 60_000;

	/**
	 * A cosmetic ready to draw, as one player wears it. The model, texture and baked render asset are shared by
	 * everyone wearing the same files; {@link #ref} is this player's own (its id and the side they picked), so two
	 * players can hold the same balloon in different hands.
	 */
	public static final class Loaded {
		public final CosmeticRef ref;
		public final CosmeticModel model;
		private final Asset asset;

		/** What every wearer shares: the texture until it is on the GPU, then the running renderer's baked asset. */
		static final class Asset {
			volatile byte[] png;
			volatile Object baked;
			/** Per-player views by ref (side / id), so worn() doesn't allocate every frame. */
			final Map<CosmeticRef, Loaded> views = new ConcurrentHashMap<CosmeticRef, Loaded>();
		}

		Loaded(CosmeticRef ref, CosmeticModel model, byte[] png) {
			this.ref = ref;
			this.model = model;
			this.asset = new Asset();
			this.asset.png = png;
			this.asset.views.put(ref, this);
		}

		private Loaded(CosmeticRef ref, CosmeticModel model, Asset asset) {
			this.ref = ref;
			this.model = model;
			this.asset = asset;
		}

		/** The texture file; null once the running renderer has uploaded it, see {@link #release()}. */
		public byte[] png() {
			return asset.png;
		}

		/** The running version's baked render asset (shared by every wearer), or null before it is baked. */
		public Object baked() {
			return asset.baked;
		}

		public void setBaked(Object baked) {
			asset.baked = baked;
		}

		/** Called by a renderer once the texture lives on the GPU: the PNG bytes are no longer needed in memory. */
		public void release() {
			asset.png = null;
		}

		/** This cosmetic as worn through `other` (same files, that player's id and side). */
		public Loaded as(CosmeticRef other) {
			if (other == null || other == ref) {
				return this;
			}
			Loaded view = asset.views.get(other);
			if (view == null) {
				if (asset.views.size() > 64) {
					asset.views.clear(); // ids/sides are few; never let this grow without bound
				}
				view = new Loaded(other, model, asset);
				asset.views.put(other, view);
			}
			return view;
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
				out.add(loaded.as(ref)); // the cache is shared by everyone wearing these files: draw with this player's side
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
		final String base = textureBase == null || textureBase.isEmpty() ? NativeState.get().api() + "/csl/textures/" : textureBase;
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
