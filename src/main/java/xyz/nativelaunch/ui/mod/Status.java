package xyz.nativelaunch.ui.mod;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Small pure helpers shared by the status modules (potion effects, armour, health and food). */
public final class Status {
	private Status() {
	}

	private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

	/** Level 1 - 10 as roman numerals like the game; anything else as a plain number. */
	public static String roman(int level) {
		return level >= 1 && level < ROMAN.length ? ROMAN[level] : String.valueOf(level);
	}

	/** Seconds left for a tick count (rounded up, like the game's own timer). */
	public static int seconds(int ticks) {
		return Math.max(0, (ticks + 19) / 20);
	}

	/** "1:05", "12:00", "1:02:03". */
	public static String time(int ticks) {
		int s = seconds(ticks);
		if (s >= 3600) {
			return (s / 3600) + ":" + two(s / 60 % 60) + ":" + two(s % 60);
		}
		return (s / 60) + ":" + two(s % 60);
	}

	private static String two(int v) {
		return v < 10 ? "0" + v : String.valueOf(v);
	}

	/** "night_vision" -> "Night Vision", "mymod:bless" -> "Bless". */
	public static String title(String id) {
		if (id == null || id.isEmpty()) {
			return "";
		}
		String n = id.substring(id.indexOf(':') + 1);
		StringBuilder sb = new StringBuilder(n.length());
		boolean up = true;
		for (int i = 0; i < n.length(); i++) {
			char ch = n.charAt(i);
			if (ch == '_' || ch == '.') {
				sb.append(' ');
				up = true;
			} else {
				sb.append(up ? Character.toUpperCase(ch) : ch);
				up = false;
			}
		}
		return sb.toString();
	}

	/** The game's own names where they differ from a tidy-up of the id. */
	public static String effectName(String id) {
		if ("unluck".equals(id) || "bad_luck".equals(id)) {
			return "Bad Luck";
		}
		if ("luck".equals(id)) {
			return "Luck";
		}
		if ("jump".equals(id)) {
			return "Jump Boost";
		}
		if ("slow_falling".equals(id)) {
			return "Slow Falling";
		}
		return title(id);
	}

	private static final java.util.Set<String> HARMFUL = new java.util.HashSet<String>(java.util.Arrays.asList("slowness", "mining_fatigue",
			"instant_damage", "nausea", "blindness", "hunger", "weakness", "poison", "wither", "levitation", "unluck", "bad_luck",
			"darkness", "bad_omen", "infested", "oozing", "weaving", "wind_charged", "raid_omen", "trial_omen"));

	public static boolean harmful(String id) {
		return HARMFUL.contains(id);
	}

	/** Colour of the durability bar and text: green when new, through yellow to red (the game's own scale). */
	public static int wear(float remaining) {
		// hue 0 (red) .. 120 degrees (green), full saturation and value
		float h = Math.max(0f, Math.min(1f, remaining)) * 2f; // 0..2 sextants
		int up = Math.round(255 * (h <= 1f ? h : 1f)), down = h <= 1f ? 255 : Math.round(255 * (2f - h));
		return 0xFF000000 | (down << 16) | (up << 8);
	}

	// ── food ─────────────────────────────────────────────────────────────

	private static final Map<String, float[]> FOOD = new HashMap<String, float[]>();

	private static void food(String id, int hunger, float saturation) {
		FOOD.put(id, new float[] {hunger, saturation});
	}

	static {
		food("apple", 4, 2.4f);
		food("baked_potato", 5, 6f);
		food("beef", 3, 1.8f);
		food("beetroot", 1, 1.2f);
		food("beetroot_soup", 6, 7.2f);
		food("bread", 5, 6f);
		food("carrot", 3, 3.6f);
		food("chicken", 2, 1.2f);
		food("chorus_fruit", 4, 2.4f);
		food("cod", 2, 0.4f);
		food("cooked_beef", 8, 12.8f);
		food("cooked_chicken", 6, 7.2f);
		food("cooked_cod", 5, 6f);
		food("cooked_mutton", 6, 9.6f);
		food("cooked_porkchop", 8, 12.8f);
		food("cooked_rabbit", 5, 6f);
		food("cooked_salmon", 6, 9.6f);
		food("cookie", 2, 0.4f);
		food("dried_kelp", 1, 0.6f);
		food("enchanted_golden_apple", 4, 9.6f);
		food("golden_apple", 4, 9.6f);
		food("golden_carrot", 6, 14.4f);
		food("honey_bottle", 6, 1.2f);
		food("melon_slice", 2, 1.2f);
		food("mushroom_stew", 6, 7.2f);
		food("mutton", 2, 1.2f);
		food("poisonous_potato", 2, 1.2f);
		food("porkchop", 3, 1.8f);
		food("potato", 1, 0.6f);
		food("pufferfish", 1, 0.2f);
		food("pumpkin_pie", 8, 4.8f);
		food("rabbit", 3, 1.8f);
		food("rabbit_stew", 10, 12f);
		food("rotten_flesh", 4, 0.8f);
		food("salmon", 2, 0.4f);
		food("spider_eye", 2, 3.2f);
		food("suspicious_stew", 6, 7.2f);
		food("sweet_berries", 2, 0.4f);
		food("glow_berries", 2, 0.4f);
		food("tropical_fish", 1, 0.2f);
	}

	/** Hunger points (half drumsticks) a food item restores, or 0 when the item is not food. */
	public static int foodHunger(String id) {
		float[] f = id == null ? null : FOOD.get(id.toLowerCase(Locale.ROOT));
		return f == null ? 0 : (int) f[0];
	}

	/** Saturation a food item restores (0 when not food). */
	public static float foodSaturation(String id) {
		float[] f = id == null ? null : FOOD.get(id.toLowerCase(Locale.ROOT));
		return f == null ? 0f : f[1];
	}

	/** Formats a health value: "20", "7.5". */
	public static String hp(float v) {
		return Math.abs(v - Math.round(v)) < 0.05f ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
	}
}
