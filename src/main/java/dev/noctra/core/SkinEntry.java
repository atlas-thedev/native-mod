package dev.noctra.core;

/** One row of the Noctra skin directory: a player's published skin and cape. */
public final class SkinEntry {
	public final String name;
	public final boolean slim;
	/** SHA-256 of the skin PNG, or null when the player has none. */
	public final String skinHash;
	/** SHA-256 of the cape PNG, or null when the player has none. */
	public final String capeHash;
	/** Dashless lowercase Minecraft UUID when the Noctra account is linked to a premium account. */
	public final String minecraftUuid;
	public final long revision;

	public SkinEntry(String name, boolean slim, String skinHash, String capeHash, String minecraftUuid, long revision) {
		this.name = name;
		this.slim = slim;
		this.skinHash = skinHash;
		this.capeHash = capeHash;
		this.minecraftUuid = minecraftUuid;
		this.revision = revision;
	}

	public boolean isEmpty() {
		return skinHash == null && capeHash == null;
	}
}
