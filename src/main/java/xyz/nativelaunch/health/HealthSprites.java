package xyz.nativelaunch.health;

/**
 * The little 9 x 9 pixel icons (heart containers, hearts in every colour, armour), drawn here in code as one sprite
 * sheet. Original artwork in the spirit of the vanilla HUD; nothing is read from the game's textures, so it looks and
 * works the same on every version.
 */
public final class HealthSprites {
	public static final int ICON = 9;
	/** Cell size on the sheet: the icon plus a transparent pixel on each side, so scaling never bleeds into a neighbour. */
	public static final int CELL = ICON + 2;
	public static final int CONTAINER = 0, CONTAINER_HARDCORE = 1, ARMOR_EMPTY = 22, ARMOR_FULL = 23, ARMOR_HALF = 24, CELLS = 25;
	/** Heart colours: normal, poisoned, withered, frozen, absorption. */
	public static final int NORMAL = 0, POISON = 1, WITHER = 2, FROZEN = 3, ABSORB = 4;

	private static final String[] HEART = {
			".XX...XX.",
			"XXXX.XXXX",
			"XXXXXXXXX",
			"XXXXXXXXX",
			"XXXXXXXXX",
			".XXXXXXX.",
			"..XXXXX..",
			"...XXX...",
			"....X....",
	};
	private static final String[] ARMOR = {
			".XX...XX.",
			"XXXX.XXXX",
			"XXXXXXXXX",
			".XXXXXXX.",
			".XXXXXXX.",
			".XXXXXXX.",
			".XXXXXXX.",
			".XXXXXXX.",
			"..XXXXX..",
	};
	private static final int OUTLINE = 0xFF000000, EMPTY_FILL = 0xFF3B3B3B, EMPTY_FILL_HARDCORE = 0xFF2E2E2E;
	/** fill, highlight */
	private static final int[][] COLORS = {
			{0xFFE02B2B, 0xFFFF9E9E},
			{0xFF74931C, 0xFFC9DE74},
			{0xFF303030, 0xFF727272},
			{0xFF8ED8F5, 0xFFFFFFFF},
			{0xFFF0BE1A, 0xFFFFEFA0},
	};
	private static final int[] ARMOR_COLORS = {0xFFC7D0DA, 0xFFFFFFFF};

	private HealthSprites() {
	}

	/** Index of a heart cell. */
	public static int heart(int color, boolean hardcore, boolean half) {
		return 2 + (color * 2 + (hardcore ? 1 : 0)) * 2 + (half ? 1 : 0);
	}

	public static int sheetWidth() {
		return CELLS * CELL;
	}

	public static int sheetHeight() {
		return CELL;
	}

	/** Left u (0..1) of a cell's icon on the sheet; the icon is {@code ICON / sheetWidth()} wide. */
	public static float u0(int cell) {
		return (cell * CELL + 1) / (float) sheetWidth();
	}

	public static float u1(int cell) {
		return (cell * CELL + 1 + ICON) / (float) sheetWidth();
	}

	public static float v0() {
		return 1f / sheetHeight();
	}

	public static float v1() {
		return (1f + ICON) / sheetHeight();
	}

	/** The sheet's ARGB pixels, row by row. */
	public static int[] sheet() {
		int w = sheetWidth(), h = sheetHeight();
		int[] px = new int[w * h];
		paint(px, w, CONTAINER, HEART, EMPTY_FILL, 0, false, 0, false);
		paint(px, w, CONTAINER_HARDCORE, HEART, EMPTY_FILL_HARDCORE, 0, false, 0, true);
		for (int color = 0; color < COLORS.length; color++) {
			for (int hc = 0; hc < 2; hc++) {
				for (int half = 0; half < 2; half++) {
					paint(px, w, heart(color, hc == 1, half == 1), HEART, hc == 1 ? EMPTY_FILL_HARDCORE : EMPTY_FILL,
							COLORS[color][0], half == 1, COLORS[color][1], hc == 1);
				}
			}
		}
		paint(px, w, ARMOR_EMPTY, ARMOR, EMPTY_FILL, 0, false, 0, false);
		paint(px, w, ARMOR_FULL, ARMOR, EMPTY_FILL, ARMOR_COLORS[0], false, ARMOR_COLORS[1], false);
		paint(px, w, ARMOR_HALF, ARMOR, EMPTY_FILL, ARMOR_COLORS[0], true, ARMOR_COLORS[1], false);
		return px;
	}

	private static boolean on(String[] shape, int x, int y) {
		return x >= 0 && y >= 0 && y < shape.length && x < shape[y].length() && shape[y].charAt(x) == 'X';
	}

	/**
	 * Draws one icon: a black outline, a dark inside, and (when {@code fill} is set) the colour over the whole inside
	 * or just its left part ({@code half}). Hardcore hearts get two angry eyes.
	 */
	private static void paint(int[] px, int sheetW, int cell, String[] shape, int emptyFill, int fill, boolean half, int highlight, boolean eyes) {
		int ox = cell * CELL + 1, oy = 1;
		for (int y = 0; y < ICON; y++) {
			for (int x = 0; x < ICON; x++) {
				if (!on(shape, x, y)) {
					continue;
				}
				boolean inside = on(shape, x - 1, y) && on(shape, x + 1, y) && on(shape, x, y - 1) && on(shape, x, y + 1);
				int argb;
				if (!inside) {
					argb = OUTLINE;
				} else if (fill != 0 && (!half || x <= 4)) {
					argb = fill;
					if (highlight != 0 && ((x == 1 && y == 1) || (x == 2 && y == 1) || (x == 1 && y == 2))) {
						argb = highlight;
					}
				} else {
					argb = emptyFill;
				}
				if (eyes && inside && y == 3 && (x == 2 || x == 6)) {
					argb = 0xFF5A0A0A;
				}
				px[(oy + y) * sheetW + ox + x] = argb;
			}
		}
	}
}
