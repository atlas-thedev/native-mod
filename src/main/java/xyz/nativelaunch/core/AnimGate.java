package xyz.nativelaunch.core;

/**
 * Decides how this Minecraft version plays animated capes in game.
 *
 * <ul>
 *   <li>{@link Mode#MODERN} - Minecraft 26.x+: the animator written against the game's own
 *       (unobfuscated) texture classes.</li>
 *   <li>{@link Mode#GL} - Minecraft 1.16 - 1.21.x: frames are written straight into the cape's
 *       OpenGL texture, found through stable Fabric intermediary names.</li>
 *   <li>{@link Mode#NONE} - anything else (snapshots, unknown versions): the first frame shows as a still cape.</li>
 * </ul>
 */
public final class AnimGate {
	public static final int MIN_MAJOR = 26;

	public enum Mode { NONE, GL, MODERN }

	private AnimGate() {
	}

	public static Mode mode(String minecraftVersion) {
		int[] parts = parse(minecraftVersion);
		if (parts == null) {
			return Mode.NONE;
		}
		if (parts[0] >= MIN_MAJOR) {
			return Mode.MODERN;
		}
		if (parts[0] == 1 && parts[1] >= 16 && parts[1] <= 21) {
			return Mode.GL;
		}
		return Mode.NONE;
	}

	/** True when animated capes play on this version (every release from 1.16 on). */
	public static boolean supported(String minecraftVersion) {
		return mode(minecraftVersion) != Mode.NONE;
	}

	/** Numeric version parts ("1.20.1", "26.3", "1.21.5-rc.1" -> 1.21.5); null for anything else, e.g. "25w14a". */
	private static int[] parse(String version) {
		if (version == null) {
			return null;
		}
		String core = version.trim();
		for (char stop : new char[] {'-', '+', ' '}) {
			int at = core.indexOf(stop);
			if (at >= 0) {
				core = core.substring(0, at);
			}
		}
		String[] pieces = core.split("\\.");
		if (pieces.length == 1 && !pieces[0].isEmpty()) {
			pieces = new String[] {pieces[0], "0"};
		}
		if (pieces.length < 2 || pieces.length > 3) {
			return null;
		}
		int[] out = new int[pieces.length];
		for (int i = 0; i < pieces.length; i++) {
			String piece = pieces[i];
			if (piece.isEmpty() || piece.length() > 4) {
				return null;
			}
			for (int j = 0; j < piece.length(); j++) {
				if (piece.charAt(j) < '0' || piece.charAt(j) > '9') {
					return null;
				}
			}
			out[i] = Integer.parseInt(piece);
		}
		return out;
	}
}
