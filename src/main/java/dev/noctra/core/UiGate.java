package dev.noctra.core;

/**
 * The Noctra title screen is written against Minecraft 26.3's classes. Anywhere else
 * (older versions, or newer ones whose screens changed) the game's own menu is left alone.
 */
public final class UiGate {
	private UiGate() {
	}

	/** True for 26.3 and its patch releases (26.3.1, ...), not for 26.30 or 26.3-rc style look-alikes of other lines. */
	public static boolean supportsTitleScreen(String minecraftVersion) {
		if (minecraftVersion == null) {
			return false;
		}
		String v = minecraftVersion.trim();
		if (v.equals("26.3")) {
			return true;
		}
		return v.startsWith("26.3.") && v.length() > 5 && Character.isDigit(v.charAt(5));
	}
}
