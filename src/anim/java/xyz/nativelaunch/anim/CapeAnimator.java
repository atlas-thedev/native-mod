package xyz.nativelaunch.anim;

import com.google.common.hash.Hashing;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.NativeImage;
import xyz.nativelaunch.core.TextureCache;
import xyz.nativelaunch.core.Log;
import xyz.nativelaunch.core.NativeState;
import xyz.nativelaunch.core.SkinEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays Native animated capes on Minecraft 26.x.
 *
 * The game loads an animated cape's first frame like any other cape and keeps it
 * under {@code minecraft:capes/<sha1 of the texture hash>}. A background thread
 * downloads each animation strip once; the render thread then swaps the still
 * texture for an {@link AnimatedCapeTexture} as soon as the game has loaded it.
 */
public final class CapeAnimator {
	private static final int MAX_STRIP_BYTES = 16 * 1024 * 1024;
	private static final long POLL_MS = 500;
	private static final long RETRY_MS = 60_000;

	private static final Map<String, byte[]> STRIPS = new ConcurrentHashMap<>();
	private static final Map<String, Long> FAILED = new ConcurrentHashMap<>();
	private static volatile boolean started;
	private static volatile boolean broken;
	private static volatile boolean queued;
	private static Field byPath;

	private CapeAnimator() {
	}

	/** Called reflectively from NativeMod on Minecraft 26.x. Idempotent. */
	public static synchronized void start() throws ReflectiveOperationException {
		if (started) {
			return;
		}
		byPath = TextureManager.class.getDeclaredField("byPath");
		byPath.setAccessible(true);
		started = true;
		Thread thread = new Thread(CapeAnimator::loop, "Native-CapeAnimator");
		thread.setDaemon(true);
		thread.start();
		Log.info("Animated capes enabled.");
	}

	/** One animated cape the game may have loaded. */
	private record Wanted(Identifier id, String stripHash, int frames, int fps) {
	}

	private static void loop() {
		while (!broken) {
			try {
				Thread.sleep(POLL_MS);
				poll();
			} catch (InterruptedException e) {
				return;
			} catch (Throwable t) {
				Log.warn("Animated cape update failed: {}", t.toString());
			}
		}
	}

	private static void poll() {
		String base = NativeState.get().directory().textureBase();
		if (base.isEmpty()) {
			return;
		}
		List<Wanted> wanted = new ArrayList<>();
		java.util.Set<String> live = new java.util.HashSet<>();
		for (SkinEntry entry : NativeState.get().directory().animated()) {
			live.add(entry.capeStripHash);
			if (fetch(base, entry.capeStripHash)) {
				wanted.add(new Wanted(textureId(base + entry.capeHash), entry.capeStripHash, entry.capeFrames, entry.capeFps));
			}
		}
		// strips of players who left (or changed cloaks) are dropped instead of piling up (each can be up to 16 MB)
		STRIPS.keySet().retainAll(live);
		Minecraft minecraft = Minecraft.getInstance();
		if (wanted.isEmpty() || minecraft == null || queued) {
			return;
		}
		queued = true;
		List<Wanted> batch = Collections.unmodifiableList(wanted);
		minecraft.execute(() -> {
			queued = false;
			try {
				apply(minecraft, batch);
			} catch (LinkageError e) {
				broken = true;
				Log.warn("Animated capes are not supported on this Minecraft build: {}", e.toString());
			} catch (Throwable t) {
				Log.warn("Could not animate a cape: {}", t.toString());
			}
		});
	}

	/** Downloads a strip once (off the render thread). True when its bytes are ready. */
	private static boolean fetch(String base, String stripHash) {
		if (STRIPS.containsKey(stripHash)) {
			return true;
		}
		Long failedAt = FAILED.get(stripHash);
		if (failedAt != null && System.currentTimeMillis() - failedAt < RETRY_MS) {
			return false;
		}
		try {
			STRIPS.put(stripHash, TextureCache.getOrDownload(base, stripHash, MAX_STRIP_BYTES));
			FAILED.remove(stripHash);
			return true;
		} catch (Exception e) {
			FAILED.put(stripHash, System.currentTimeMillis());
			Log.warn("Could not download an animated cape: {}", e.toString());
			return false;
		}
	}

	/** Same id SkinManager gives a downloaded cape: capes/ + sha1(texture hash). */
	static Identifier textureId(String capeUrl) {
		String hash = new MinecraftProfileTexture(capeUrl, Map.of()).getHash();
		return Identifier.withDefaultNamespace("capes/" + Hashing.sha1().hashUnencodedChars(hash).toString());
	}

	@SuppressWarnings("unchecked")
	private static void apply(Minecraft minecraft, List<Wanted> batch) throws Exception {
		TextureManager textures = minecraft.getTextureManager();
		if (textures == null) {
			return;
		}
		Map<Identifier, AbstractTexture> loaded = (Map<Identifier, AbstractTexture>) byPath.get(textures);
		for (Wanted want : batch) {
			AbstractTexture current = loaded.get(want.id());
			if (current == null) {
				continue; // nobody wearing it is on screen yet
			}
			if (current instanceof AnimatedCapeTexture anim && anim.stripHash().equals(want.stripHash())) {
				continue;
			}
			byte[] bytes = STRIPS.get(want.stripHash());
			if (bytes == null) {
				continue;
			}
			NativeImage strip = NativeImage.read(bytes);
			if (want.frames() < 2 || strip.getHeight() % want.frames() != 0 || strip.getHeight() < want.frames()) {
				Log.warn("Animated cape strip {}x{} does not split into {} frames.", strip.getWidth(), strip.getHeight(), want.frames());
				strip.close();
				STRIPS.remove(want.stripHash());
				FAILED.put(want.stripHash(), System.currentTimeMillis());
				continue;
			}
			textures.register(want.id(), new AnimatedCapeTexture(strip, want.stripHash(), want.frames(), want.fps()));
		}
	}
}
