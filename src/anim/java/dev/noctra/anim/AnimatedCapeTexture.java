package dev.noctra.anim;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TickableTexture;

/**
 * A cape texture that plays a vertical frame strip. It replaces the still cape
 * Minecraft registered for the player, under the same id, so every renderer
 * (cape, elytra, inventory preview) animates without any mixin. The texture
 * manager ticks it every client tick; the frame is picked from the wall clock so
 * playback speed does not depend on tick rate.
 */
public final class AnimatedCapeTexture extends DynamicTexture implements TickableTexture {
	private final NativeImage strip;
	private final String stripHash;
	private final int frames;
	private final int frameHeight;
	private final long frameNanos;
	private final long startNanos = System.nanoTime();
	private int shown = -1;
	private boolean closed;

	AnimatedCapeTexture(NativeImage strip, String stripHash, int frames, int fps) {
		super(() -> "noctra-animated-cape", strip.getWidth(), strip.getHeight() / frames, false);
		this.strip = strip;
		this.stripHash = stripHash;
		this.frames = frames;
		this.frameHeight = strip.getHeight() / frames;
		this.frameNanos = Math.max(1L, 1_000_000_000L / Math.max(1, fps));
		show(0);
	}

	String stripHash() {
		return stripHash;
	}

	@Override
	public void tick() {
		if (closed) {
			return;
		}
		int frame = (int) (((System.nanoTime() - startNanos) / frameNanos) % frames);
		if (frame != shown) {
			show(frame);
		}
	}

	private void show(int frame) {
		NativeImage pixels = getPixels();
		if (pixels == null) {
			return;
		}
		strip.copyRect(pixels, 0, frame * frameHeight, 0, 0, pixels.getWidth(), frameHeight, false, false);
		upload();
		shown = frame;
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		super.close();
		strip.close();
	}
}
