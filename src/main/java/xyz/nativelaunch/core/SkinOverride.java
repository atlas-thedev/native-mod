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
	/**
	 * Changes exactly when what is shown changes. Used as the textures timestamp so the game's skin
	 * cache (keyed by the textures property) stays warm between lookups and reloads after a change.
	 */
	public final long stamp;
	/** True: show no cape at all, not even the vanilla one (a back cosmetic takes its place). */
	public final boolean hideCape;

	public SkinOverride(String skinUrl, String capeUrl, boolean slim) {
		this(skinUrl, capeUrl, slim, null, 0, 0);
	}

	public SkinOverride(String skinUrl, String capeUrl, boolean slim, String capeStripUrl, int capeFrames, int capeFps) {
		this(skinUrl, capeUrl, slim, capeStripUrl, capeFrames, capeFps, false);
	}

	public SkinOverride(String skinUrl, String capeUrl, boolean slim, String capeStripUrl, int capeFrames, int capeFps, boolean hideCape) {
		this.hideCape = hideCape;
		if (hideCape) {
			capeUrl = null;
			capeStripUrl = null;
		}
		this.skinUrl = skinUrl;
		this.capeUrl = capeUrl;
		this.slim = slim;
		boolean animated = capeUrl != null && capeStripUrl != null && capeFrames > 1 && capeFps > 0;
		this.capeStripUrl = animated ? capeStripUrl : null;
		this.capeFrames = animated ? capeFrames : 0;
		this.capeFps = animated ? capeFps : 0;
		long h = 1125899906842597L;
		for (String part : new String[] {skinUrl, capeUrl, this.capeStripUrl, slim ? "slim" : "default",
				Integer.toString(this.capeFrames), Integer.toString(this.capeFps), hideCape ? "nocape" : null}) {
			h = 31 * h + (part == null ? 0 : part.hashCode());
		}
		this.stamp = h & Long.MAX_VALUE;
	}

	public boolean hasAnimatedCape() {
		return capeStripUrl != null;
	}
}
