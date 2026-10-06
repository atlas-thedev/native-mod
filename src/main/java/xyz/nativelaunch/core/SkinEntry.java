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
	 * True when the server sent a premium block ({@code "p"}): the Native name equals the linked premium
	 * name, so a real premium session keeps its Mojang skin and only wears {@link #premiumCapeHash}.
	 */
	public final boolean hasPremium;
	public final String premiumCapeHash;
	public final String premiumStripHash;
	public final int premiumFrames;
	public final int premiumFps;
	/** Worn 3D cosmetics (hat, glasses, back, shoes): never null, at most {@link #MAX_COSMETICS}. */
	public final java.util.List<xyz.nativelaunch.cosmetic.CosmeticRef> cosmetics;
	public static final int MAX_COSMETICS = 8;

	public SkinEntry(String name, boolean slim, String skinHash, String capeHash, String minecraftUuid, long revision) {
		this(name, slim, skinHash, capeHash, minecraftUuid, revision, null, 0, 0);
	}

	public SkinEntry(String name, boolean slim, String skinHash, String capeHash, String minecraftUuid, long revision,
			String capeStripHash, int capeFrames, int capeFps) {
		this(name, slim, skinHash, capeHash, minecraftUuid, revision, capeStripHash, capeFrames, capeFps, false, null, null, 0, 0);
	}

	public SkinEntry(String name, boolean slim, String skinHash, String capeHash, String minecraftUuid, long revision,
			String capeStripHash, int capeFrames, int capeFps,
			boolean hasPremium, String premiumCapeHash, String premiumStripHash, int premiumFrames, int premiumFps) {
		this(name, slim, skinHash, capeHash, minecraftUuid, revision, capeStripHash, capeFrames, capeFps,
				hasPremium, premiumCapeHash, premiumStripHash, premiumFrames, premiumFps, null);
	}

	public SkinEntry(String name, boolean slim, String skinHash, String capeHash, String minecraftUuid, long revision,
			String capeStripHash, int capeFrames, int capeFps,
			boolean hasPremium, String premiumCapeHash, String premiumStripHash, int premiumFrames, int premiumFps,
			java.util.List<xyz.nativelaunch.cosmetic.CosmeticRef> cosmetics) {
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
		this.hasPremium = hasPremium;
		this.premiumCapeHash = hasPremium ? premiumCapeHash : null;
		boolean premiumAnimated = this.premiumCapeHash != null && premiumStripHash != null && premiumFrames > 1 && premiumFps > 0;
		this.premiumStripHash = premiumAnimated ? premiumStripHash : null;
		this.premiumFrames = premiumAnimated ? Math.min(premiumFrames, 256) : 0;
		this.premiumFps = premiumAnimated ? Math.min(premiumFps, 60) : 0;
		if (cosmetics == null || cosmetics.isEmpty()) {
			this.cosmetics = java.util.Collections.emptyList();
		} else {
			java.util.List<xyz.nativelaunch.cosmetic.CosmeticRef> copy = new java.util.ArrayList<xyz.nativelaunch.cosmetic.CosmeticRef>(cosmetics);
			this.cosmetics = java.util.Collections.unmodifiableList(copy.size() > MAX_COSMETICS ? copy.subList(0, MAX_COSMETICS) : copy);
		}
	}

	/** True when the player wears an animated cape (strip hash, frames and fps are all valid). */
	public boolean hasAnimatedCape() {
		return capeStripHash != null;
	}

	public boolean isEmpty() {
		return skinHash == null && capeHash == null && premiumCapeHash == null && cosmetics.isEmpty();
	}
}
