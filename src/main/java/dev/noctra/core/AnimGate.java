package dev.noctra.core;

/**
 * Decides whether this Minecraft version can play animated capes in game.
 * The animator is written against Minecraft 26.x's own texture classes; older
 * versions simply keep showing the cape's first frame as a normal cape.
 */
public final class AnimGate {
	public static final int MIN_MAJOR = 26;

	private AnimGate() {
	}

	public static boolean supported(String minecraftVersion) {
		if (minecraftVersion == null) {
			return false;
		}
		int major = 0;
		int digits = 0;
		for (int i = 0; i < minecraftVersion.length(); i++) {
			char c = minecraftVersion.charAt(i);
			if (c < '0' || c > '9') {
				break;
			}
			major = major * 10 + (c - '0');
			if (++digits > 4) {
				return false;
			}
		}
		return digits > 0 && major >= MIN_MAJOR;
	}
}
