package xyz.nativelaunch.health;

import java.util.Locale;

/** Pure maths for the heart / armour rows and the number text. Icons are 9 px, spaced 8 px, like the vanilla HUD. */
public final class HealthLayout {
	public static final int EMPTY = 0, RED_FULL = 1, RED_HALF = 2, YELLOW_FULL = 3, YELLOW_HALF = 4;
	/** More heart containers than this and the indicator falls back to the number (a modded boss with a million health). */
	public static final int MAX_ICONS = 120;
	public static final int ICON = 9, STEP = 8;
	/** At least this many armour slots are drawn (the vanilla bar), more for high armour. */
	public static final int MIN_ARMOR_SLOTS = 10, MAX_ARMOR_SLOTS = 60;

	/** Filled red hearts (a trailing half counts as one), containers, and absorption hearts. */
	public int red, normal, yellow, total;
	public boolean redHalf, yellowHalf;
	public boolean overflow;

	public HealthLayout set(float health, float maxHealth, float absorption, boolean percent, int percentMax) {
		int hp = (int) Math.ceil(health);
		int max = (int) Math.ceil(maxHealth);
		int abs = (int) Math.ceil(absorption);
		if (percent) {
			hp = max <= 0 ? 0 : (int) Math.ceil(((float) hp / max) * percentMax);
			max = percentMax;
		}
		hp = Math.max(0, hp);
		max = Math.max(0, max);
		abs = Math.max(0, abs);
		red = (hp + 1) / 2;
		redHalf = (hp & 1) == 1;
		normal = (max + 1) / 2;
		yellow = (abs + 1) / 2;
		yellowHalf = (abs & 1) == 1;
		total = normal + yellow;
		overflow = total > MAX_ICONS;
		return this;
	}

	/** Icon kind at a heart index: EMPTY, RED_*, YELLOW_*. */
	public int typeAt(int i) {
		if (i < red) {
			return i == red - 1 && redHalf ? RED_HALF : RED_FULL;
		}
		if (i < normal) {
			return EMPTY;
		}
		return i == total - 1 && yellowHalf ? YELLOW_HALF : YELLOW_FULL;
	}

	public static int rows(int icons, int perRow) {
		perRow = Math.max(1, perRow);
		return icons <= 0 ? 0 : (icons + perRow - 1) / perRow;
	}

	/** Horizontal position (in icon pixels) of the right edge of icon i; icons run right to left because the quad is mirrored. */
	public static float iconX(int i, int count, int perRow) {
		perRow = Math.max(1, perRow);
		float maxX = (Math.min(count, perRow) * STEP + 1) / 2f;
		return maxX - (i % perRow) * STEP;
	}

	public static int rowOf(int i, int perRow) {
		return i / Math.max(1, perRow);
	}

	/** Armour slots to draw for an armour value (one slot = 2 points). */
	public static int armorSlots(int armorValue) {
		int points = (Math.max(0, armorValue) + 1) / 2;
		return Math.max(MIN_ARMOR_SLOTS, Math.min(MAX_ARMOR_SLOTS, points));
	}

	/** 0 empty, 1 full, 2 half for armour slot i. */
	public static int armorType(int i, int armorValue) {
		int points = (Math.max(0, armorValue) + 1) / 2;
		if (i >= points) {
			return 0;
		}
		return i == points - 1 && (armorValue & 1) == 1 ? 2 : 1;
	}

	public static String text(float health, float maxHealth, float absorption, boolean percent, int decimals) {
		if (percent) {
			float pct = maxHealth <= 0 ? 0 : (health + absorption) / maxHealth * 100f;
			return fmt(pct, decimals) + " %";
		}
		return fmt(health + absorption, decimals) + " / " + fmt(maxHealth, decimals);
	}

	public static String fmt(float v, int decimals) {
		return String.format(Locale.ROOT, "%." + Math.max(0, Math.min(6, decimals)) + "f", v);
	}

	/** Heart index that hops while regenerating, like the vanilla bar (index = tick % ceil(max + 5)). */
	public static int regenIndex(int tick, float maxHealth) {
		int mod = Math.max(1, (int) Math.ceil(maxHealth + 5f));
		return Math.floorMod(tick, mod);
	}
}
