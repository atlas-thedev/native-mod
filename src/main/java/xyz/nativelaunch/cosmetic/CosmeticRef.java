package xyz.nativelaunch.cosmetic;

/** One worn cosmetic in the skin directory: the store item id plus its model (NCM JSON) and texture (PNG) hashes. */
public final class CosmeticRef {
	public final String id;
	public final String modelHash;
	public final String textureHash;
	/** Store slot ("hats", "glasses", "back", "shoes"), or null when the server did not say. */
	public final String slot;

	public CosmeticRef(String id, String modelHash, String textureHash) {
		this(id, modelHash, textureHash, null);
	}

	public CosmeticRef(String id, String modelHash, String textureHash, String slot) {
		this.id = id;
		this.modelHash = modelHash;
		this.textureHash = textureHash;
		this.slot = slot;
	}

	/** Wings, jetpacks and backpacks take the cape's place on the player's back. */
	public boolean isBackItem() {
		return "back".equals(slot);
	}

	/** Model + texture: what one baked render asset is keyed by. */
	public String key() {
		return modelHash + "/" + textureHash;
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof CosmeticRef)) {
			return false;
		}
		CosmeticRef o = (CosmeticRef) other;
		return key().equals(o.key()) && (id == null ? o.id == null : id.equals(o.id));
	}

	@Override
	public int hashCode() {
		return key().hashCode();
	}
}
