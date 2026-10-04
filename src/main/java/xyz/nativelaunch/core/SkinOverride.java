package xyz.nativelaunch.core;

/** What Native wants shown for one player. Either URL may be null (keep the vanilla texture). */
public final class SkinOverride {
	public final String skinUrl;
	public final String capeUrl;
	public final boolean slim;
	/** Animated cape strip URL (frames stacked vertically), or null. {@link #capeUrl} is the first frame. */
	public final String capeStripUrl;
	public final int capeFrames;
	public final int capeFps;

	public SkinOverride(String skinUrl, String capeUrl, boolean slim) {
		this(skinUrl, capeUrl, slim, null, 0, 0);
	}

	public SkinOverride(String skinUrl, String capeUrl, boolean slim, String capeStripUrl, int capeFrames, int capeFps) {
		this.skinUrl = skinUrl;
		this.capeUrl = capeUrl;
		this.slim = slim;
		boolean animated = capeUrl != null && capeStripUrl != null && capeFrames > 1 && capeFps > 0;
		this.capeStripUrl = animated ? capeStripUrl : null;
		this.capeFrames = animated ? capeFrames : 0;
		this.capeFps = animated ? capeFps : 0;
	}

	public boolean hasAnimatedCape() {
		return capeStripUrl != null;
	}
}
