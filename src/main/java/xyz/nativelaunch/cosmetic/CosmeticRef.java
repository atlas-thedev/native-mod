package xyz.nativelaunch.cosmetic;

/** One worn cosmetic in the skin directory: the store item id plus its model (NCM JSON) and texture (PNG) hashes. */
public final class CosmeticRef {
	public final String id;
	public final String modelHash;
	public final String textureHash;

	public CosmeticRef(String id, String modelHash, String textureHash) {
		this.id = id;
		this.modelHash = modelHash;
		this.textureHash = textureHash;
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
