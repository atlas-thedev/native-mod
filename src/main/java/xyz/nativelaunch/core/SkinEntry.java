package xyz.nativelaunch.core;

/** One row of the Native skin directory: a player's published skin and cape. */
public final class SkinEntry {
	public final String name;
	public final boolean slim;
	/** SHA-256 of the skin PNG, or null when the player has none. */
	public final String skinHash;
	/** SHA-256 of the cape PNG, or null when the player has none. */
	public final String capeHash;
	/** Dashless lowercase Minecraft UUID when the Native account is linked to a premium account. */
	public final String minecraftUuid;
	public final long revision;
	/**
	 * SHA-256 of the animated cape strip (frames stacked top to bottom), or null for a still cape.
	 * {@link #capeHash} is always the first frame, so anything that cannot animate shows a normal cape.
	 */
	public final String capeStripHash;
	/** Frame count of the animated cape strip (0 when the cape is not animated). */
	public final int capeFrames;
	/** Playback speed of the animated cape strip in frames per second (0 when not animated). */
	public final int capeFps;
	/**
	 * The look for the linked premium player when the Native and premium names are the same:
	 * the Mojang skin stays and only this cape (or nothing, when null) is shown.
	 * Null when the entry has no separate premium look (older servers).
	 */
	public final SkinEntry premium;

	public SkinEntry(String name, boolean slim, String skinHash, String capeHash, String minecraftUuid, long revision) {
		this(name, slim, skinHash, capeHash, minecraftUuid, revision, null, 0, 0);
	}

	public SkinEntry(String name, boolean slim, String skinHash, String capeHash, String minecraftUuid, long revision,
			String capeStripHash, int capeFrames, int capeFps) {
		this(name, slim, skinHash, capeHash, minecraftUuid, revision, capeStripHash, capeFrames, capeFps, null);
	}

	public SkinEntry(String name, boolean slim, String skinHash, String capeHash, String minecraftUuid, long revision,
			String capeStripHash, int capeFrames, int capeFps, SkinEntry premium) {
		this.premium = premium;
		this.name = name;
		this.slim = slim;
		this.skinHash = skinHash;
		this.capeHash = capeHash;
		this.minecraftUuid = minecraftUuid;
		this.revision = revision;
		boolean animated = capeHash != null && capeStripHash != null && capeFrames > 1 && capeFps > 0;
		this.capeStripHash = animated ? capeStripHash : null;
		this.capeFrames = animated ? Math.min(capeFrames, 256) : 0;
		this.capeFps = animated ? Math.min(capeFps, 60) : 0;
	}

	/** True when the player wears an animated cape (strip hash, frames and fps are all valid). */
	public boolean hasAnimatedCape() {
		return capeStripHash != null;
	}

	public boolean isEmpty() {
		return skinHash == null && capeHash == null && (premium == null || premium.capeHash == null);
	}
}
