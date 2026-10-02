package dev.noctra.core;

/** What Noctra wants shown for one player. Either URL may be null (keep the vanilla texture). */
public final class SkinOverride {
	public final String skinUrl;
	public final String capeUrl;
	public final boolean slim;

	public SkinOverride(String skinUrl, String capeUrl, boolean slim) {
		this.skinUrl = skinUrl;
		this.capeUrl = capeUrl;
		this.slim = slim;
	}
}
