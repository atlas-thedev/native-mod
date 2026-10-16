package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.core.Log;
import xyz.nativelaunch.core.NativeState;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.ui.gfx.Image;
import xyz.nativelaunch.ui.gfx.NavIcons;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * More client modules: Nether coordinates, moon phase, day / night cycle, totem and item counters, playtime, reach
 * display and the shulker box tooltip. (Low Fire and Damage Tint have their own files: the game hooks live next to them.)
 */
public final class ExtraModules {
	private ExtraModules() {
	}

	static void register() {
		Modules.register(new NetherCoords());
		Modules.register(new MoonPhase());
		Modules.register(new DayNight());
		Modules.register(new TotemCounter());
		Modules.register(new ItemCounter());
		Modules.register(new Playtime());
		Modules.register(new ReachDisplay());
		Modules.register(new ShulkerTooltip());
		Modules.register(new Combo());
		Modules.register(new Cooldowns());
		Modules.register(new BlockOverlay());
		Modules.register(new LowFire());
		Modules.register(new DamageTint());
		Modules.register(new Freelook());
		Modules.register(new NoHurtCam());
		Modules.register(new TimeChanger());
		Modules.register(new WeatherChanger());
	}

	/** Called once per UI frame (also while no Playtime module is on: the total keeps counting). */
	public static void tick(Game g) {
		Playtime.tick(g);
	}

	// ── world clock (shared by the moon and day / night modules) ───────────

	/** The world's time of day in ticks, read from the game a few times a second; animated for previews. */
	static final class Clock {
		private static long next, time = -1;
		private static String dim = "overworld";

		static long dayTime(Game g) {
			if (g.sample) {
				return System.currentTimeMillis() % 24000; // a whole day in 24 s so the preview moves
			}
			refresh();
			return time;
		}

		static String dimension(Game g) {
			if (g.sample) {
				return "overworld";
			}
			refresh();
			return dim;
		}

		private static void refresh() {
			long now = System.currentTimeMillis();
			if (now < next) {
				return;
			}
			next = now + 250;
			try {
				time = HudModules.mc().dayTime();
				String d = HudModules.mc().dimension();
				dim = d == null ? "overworld" : d;
			} catch (Throwable t) {
				time = -1;
			}
		}
	}

	private static String two(long v) {
		return v < 10 ? "0" + v : String.valueOf(v);
	}

	// ── Nether coordinates ──────────────────────────────────────────────────

	static final class NetherCoords extends HudModule {
		final Setting.Bool compact = add(new Setting.Bool("compact", "One line", false));

		NetherCoords() {
			super("nether-coords", "Nether Coordinates", "Where a Nether portal here would lead: your position converted between the Overworld and the Nether (1 : 8).",
					Theme.I_COMPASS, false, 0f, 0.22f);
		}

		@Override
		public boolean visible(Game g) {
			return g.sample || !"the_end".equals(Clock.dimension(g));
		}

		@Override
		protected int lines(Game g, String[] l, String[] v) {
			boolean nether = !g.sample && "the_nether".equals(Clock.dimension(g));
			double f = nether ? 8.0 : 1.0 / 8.0;
			long x = (long) Math.floor(g.x * f), z = (long) Math.floor(g.z * f);
			String where = nether ? "Overworld" : "Nether";
			if (compact.value) {
				l[0] = where;
				v[0] = x + " " + z;
				return 1;
			}
			l[0] = where + " X";
			v[0] = String.valueOf(x);
			l[1] = where + " Z";
			v[1] = String.valueOf(z);
			return 2;
		}
	}

	// ── moon phase ──────────────────────────────────────────────────────────

	static final String[] PHASES = {"Full Moon", "Waning Gibbous", "Last Quarter", "Waning Crescent", "New Moon", "Waxing Crescent", "First Quarter",
			"Waxing Gibbous"};

	/** The moon drawn for one phase (0 = full ... 4 = new), lit like a sphere. White-ish on a dark disc; cached per size. */
	static final Map<Long, Image> MOONS = new HashMap<Long, Image>();

	static Image moon(int phase, int px) {
		long key = ((long) phase << 32) | px;
		Image img = MOONS.get(key);
		if (img == null) {
			if (MOONS.size() > 96) {
				MOONS.clear();
			}
			img = renderMoon(phase, px);
			MOONS.put(key, img);
		}
		return img;
	}

	private static Image renderMoon(int phase, int px) {
		int[] argb = new int[px * px];
		double alpha = phase * Math.PI / 4;
		// light direction: from the right while waxing, from the left while waning
		double lx = -Math.sin(alpha), lz = Math.cos(alpha);
		float r = px / 2f;
		for (int j = 0; j < px; j++) {
			for (int i = 0; i < px; i++) {
				double nx = (i + 0.5f - r) / r, ny = (j + 0.5f - r) / r;
				double d2 = nx * nx + ny * ny;
				double edge = Math.max(0, Math.min(1, (1 - Math.sqrt(d2)) * r + 0.5));
				if (edge <= 0) {
					continue;
				}
				double nz = Math.sqrt(Math.max(0, 1 - d2));
				double lit = nx * lx + nz * lz; // >0 faces the light
				double k = Math.max(0, Math.min(1, lit * 6 + 0.5)); // a soft terminator
				int shade = (int) Math.round(0x2A + (0xEC - 0x2A) * k);
				int rr = shade, gg = (int) Math.round(shade * 0.985), bb = Math.min(255, (int) Math.round(shade * 1.02 + (1 - k) * 12));
				argb[j * px + i] = ((int) Math.round(edge * 255) << 24) | (rr << 16) | (gg << 8) | bb;
			}
		}
		return new Image(px, px, argb);
	}

	static final class MoonPhase extends StatusModules.Box {
		final Setting.Bool name = add(new Setting.Bool("name", "Show phase name", true));
		final Setting.Bool next = add(new Setting.Bool("next", "Show next full moon", true));
		final Setting.Bool day = add(new Setting.Bool("day", "Show day count", true));

		MoonPhase() {
			super("moon-phase", "Moon Phase", "The phase of the moon tonight, how many nights until the next full moon (more slimes in swamps, brighter nights) and the day count.",
					NavIcons.MOON, false, 0f, 0.3f);
			drop(labels, color);
			style.initial(STYLE_CARD);
		}

		@Override
		void render(Canvas c, Ui ui, Game g, float x, float y) {
			float s = scale.value, pad = pad();
			long t = g.sample ? ((System.currentTimeMillis() / 2200) % 8) * 24000L + 18000L : Clock.dayTime(g);
			long days = t < 0 ? 0 : t / 24000L;
			int phase = (int) (days % 8);
			float icon = 30 * s;
			String title = t < 0 ? "Unknown" : PHASES[phase];
			String sub = null;
			if (t >= 0 && next.value) {
				long to = (8 - phase) % 8;
				sub = to == 0 ? "Full moon tonight" : "Full moon in " + to + (to == 1 ? " night" : " nights");
			}
			String dayText = t >= 0 && day.value ? "Day " + (days + 1) : null;
			float fs = 12 * s, fs2 = 10.5f * s;
			float tw = name.value ? c.textWidth(Fonts.SEMIBOLD, fs, title) : 0;
			if (sub != null) {
				tw = Math.max(tw, c.textWidth(Fonts.REGULAR, fs2, sub));
			}
			if (dayText != null) {
				tw = Math.max(tw, c.textWidth(Fonts.REGULAR, fs2, dayText));
			}
			float lh = c.lineHeight(Fonts.SEMIBOLD, fs), lh2 = c.lineHeight(Fonts.REGULAR, fs2);
			int rows = (name.value ? 1 : 0) + (sub != null ? 1 : 0) + (dayText != null ? 1 : 0);
			float textH = (name.value ? lh : 0) + ((sub != null ? 1 : 0) + (dayText != null ? 1 : 0)) * lh2 + Math.max(0, rows - 1) * 2 * s;
			float gap = rows > 0 ? 9 * s : 0;
			float w = pad * 2 + icon + gap + tw, h = pad * 2 + Math.max(icon, textH);
			if (ui != null) {
				int px = Math.max(8, c.px(icon));
				Image img = moon(t < 0 ? 4 : phase, px);
				c.stamp(img, img, x + pad, y + (h - icon) / 2, icon, icon, false, 0xFFFFFFFF);
				float tx = x + pad + icon + gap, ty = y + (h - textH) / 2;
				if (name.value) {
					text(c, Fonts.SEMIBOLD, fs, title, tx, ty, 0xFFFFFFFF);
					ty += lh + 2 * s;
				}
				if (sub != null) {
					text(c, Fonts.REGULAR, fs2, sub, tx, ty, 0xFFB4B8C4);
					ty += lh2 + 2 * s;
				}
				if (dayText != null) {
					text(c, Fonts.REGULAR, fs2, dayText, tx, ty, 0xFF8C90A0);
				}
			}
			done(w, h);
		}
	}

	// ── day / night cycle ───────────────────────────────────────────────────

	static final class DayNight extends StatusModules.Box {
		final Setting.Bool clock = add(new Setting.Bool("clock", "Show the in-game clock", true));
		final Setting.Bool countdown = add(new Setting.Bool("countdown", "Show time until the next change", true));
		final Setting.Bool day = add(new Setting.Bool("day", "Show day count", false));

		DayNight() {
			super("day-night", "Day / Night Cycle", "Shows where the sun is: the in-game clock, day or night, and how long until it changes (monsters spawn from dusk).",
					Theme.I_SUN, false, 0f, 0.38f);
			drop(labels, color);
			style.initial(STYLE_CARD);
		}

		/** 0 day, 1 dusk, 2 night, 3 dawn */
		static int phaseOf(long t) {
			if (t < 12000) {
				return 0;
			}
			if (t < 13000) {
				return 1;
			}
			return t < 23000 ? 2 : 3;
		}

		private static final String[] NAMES = {"Day", "Dusk", "Night", "Dawn"};
		private static final String[] NEXT = {"Dusk in ", "Night in ", "Dawn in ", "Day in "};
		private static final int[] ENDS = {12000, 13000, 23000, 24000};

		@Override
		void render(Canvas c, Ui ui, Game g, float x, float y) {
			float s = scale.value, pad = pad();
			long raw = Clock.dayTime(g);
			boolean known = raw >= 0;
			long t = known ? raw % 24000L : 6000;
			int ph = phaseOf(t);
			float barW = 124 * s, barH = 6 * s;
			float fs = 12 * s, fs2 = 10.5f * s;
			long hours = (t / 1000 + 6) % 24, minutes = (t % 1000) * 60 / 1000;
			String head = NAMES[ph] + (clock.value && known ? "  " + two(hours) + ":" + two(minutes) : "");
			String dayText = day.value && known ? "Day " + (raw / 24000L + 1) : null;
			long secs = (ENDS[ph] - t) / 20;
			String sub = countdown.value && known ? NEXT[ph] + (secs / 60) + ":" + two(secs % 60) : null;
			float lh = c.lineHeight(Fonts.SEMIBOLD, fs), lh2 = c.lineHeight(Fonts.REGULAR, fs2);
			float w = pad * 2 + Math.max(barW, Math.max(c.textWidth(Fonts.SEMIBOLD, fs, head) + (dayText == null ? 0 : c.textWidth(Fonts.REGULAR, fs2, dayText) + 10 * s),
					sub == null ? 0 : c.textWidth(Fonts.REGULAR, fs2, sub)));
			float h = pad * 2 + lh + 5 * s + barH + 12 * s + (sub != null ? lh2 + 3 * s : 0);
			if (ui != null) {
				float ix = x + pad, iy = y + pad;
				text(c, Fonts.SEMIBOLD, fs, head, ix, iy, 0xFFFFFFFF);
				if (dayText != null) {
					float dw = c.textWidth(Fonts.REGULAR, fs2, dayText);
					text(c, Fonts.REGULAR, fs2, dayText, x + w - pad - dw, iy + (lh - lh2) / 2, 0xFF9AA0B2);
				}
				float by = iy + lh + 7 * s;
				float bw = w - pad * 2;
				// the sky over a whole day: day, dusk, night, dawn (the day starts at sunrise)
				float seg = bw / 24000f;
				c.round(ix, by, bw, barH, barH / 2, 0xFF1B2347);
				c.pushClip(ix, by, bw, barH);
				c.fill(ix, by, 12000 * seg, barH, 0xFF5AA9F0);
				c.gradientH(ix + 12000 * seg, by, 1000 * seg, barH, 0xFF5AA9F0, 0xFFF2994A);
				c.fill(ix + 13000 * seg, by, 10000 * seg, barH, 0xFF1B2347);
				c.gradientH(ix + 23000 * seg, by, 1000 * seg, barH, 0xFF1B2347, 0xFFF2994A);
				c.popClip();
				if (known || g.sample) {
					float mx = ix + t * seg, my = by + barH / 2;
					boolean night = ph == 2;
					c.circle(mx, my, 6 * s, 0xAA000000);
					c.circle(mx, my, 4.6f * s, night ? 0xFFE6EAF5 : 0xFFFFD25A);
					if (night) {
						c.circle(mx + 1.6f * s, my - 1.2f * s, 3.6f * s, 0xFF1B2347);
					}
				}
				if (sub != null) {
					text(c, Fonts.REGULAR, fs2, sub, ix, by + barH + 7 * s, 0xFFB4B8C4);
				}
			}
			done(w, h);
		}
	}

	// ── counters ────────────────────────────────────────────────────────────

	static int count(Game g, String id) {
		int n = 0;
		for (Game.Slot s : g.inv) {
			if (!s.empty() && id.equals(s.id)) {
				n += s.count;
			}
		}
		if (!g.offhand.empty() && id.equals(g.offhand.id)) {
			n += g.offhand.count;
		}
		return n;
	}

	/** An item's icon and how many you carry (backpack, hotbar and off hand). */
	abstract static class Counter extends StatusModules.Box {
		final Setting.Bool hideNone = add(new Setting.Bool("hide", "Hide when you have none", false));
		final Setting.Bool showName = add(new Setting.Bool("name", "Show the item name", false));
		private final Game.Slot icon = new Game.Slot();

		Counter(String id, String name, String description, int ic, float x, float y) {
			super(id, name, description, ic, false, x, y);
			drop(labels, color);
			style.initial(STYLE_CARD);
		}

		@Override
		public int needs() {
			return Game.NEED_INVENTORY | Game.NEED_ARMOR;
		}

		/** The item counted right now (bare id), or null when there is none to show (no item held). */
		abstract String item(Game g);

		abstract String itemName(String id);

		abstract int sample();

		@Override
		public boolean visible(Game g) {
			if (g.sample) {
				return true;
			}
			String id = item(g);
			return id != null && (!hideNone.value || count(g, id) > 0);
		}

		@Override
		void render(Canvas c, Ui ui, Game g, float x, float y) {
			float s = scale.value, pad = pad();
			String id = g.sample ? sampleItem() : item(g);
			int n = g.sample ? sample() : id == null ? 0 : count(g, id);
			float ic = 16 * s, fs = 13 * s;
			String num = String.valueOf(n);
			String label = showName.value && id != null ? itemName(id) : null;
			float nw = c.textWidth(Fonts.BOLD, fs, num);
			float lw = label == null ? 0 : c.textWidth(Fonts.REGULAR, 10.5f * s, label);
			float textW = Math.max(nw, lw);
			float lh = c.lineHeight(Fonts.BOLD, fs), lh2 = label == null ? 0 : c.lineHeight(Fonts.REGULAR, 10.5f * s);
			float textH = lh + (label == null ? 0 : lh2 + 1 * s);
			float w = pad * 2 + ic + 6 * s + textW, h = pad * 2 + Math.max(ic, textH);
			if (ui != null && id != null) {
				icon.set(id, 1, 0, 0);
				c.pushAlpha(n == 0 ? 0.45f : 1f);
				StatusModules.item(c, icon, x + pad, y + (h - ic) / 2, ic, 0xFFFFFFFF);
				c.popAlpha();
				float tx = x + pad + ic + 6 * s, ty = y + (h - textH) / 2;
				text(c, Fonts.BOLD, fs, num, tx, ty, n == 0 ? 0xFFF87171 : 0xFFFFFFFF);
				if (label != null) {
					text(c, Fonts.REGULAR, 10.5f * s, label, tx, ty + lh + 1 * s, 0xFF9AA0B2);
				}
			}
			done(w, h);
		}

		abstract String sampleItem();
	}

	static final class TotemCounter extends Counter {
		TotemCounter() {
			super("totem-counter", "Totem Counter", "How many Totems of Undying you carry, so you know before the next fight.", Theme.I_HEART_PULSE, 0f, 0.46f);
		}

		@Override
		String item(Game g) {
			return "totem_of_undying";
		}

		@Override
		String itemName(String id) {
			return "Totems";
		}

		@Override
		int sample() {
			return 3;
		}

		@Override
		String sampleItem() {
			return "totem_of_undying";
		}
	}

	static final class ItemCounter extends Counter {
		/** Display names and ids, in list order. The empty choice is "the item in your hand". */
		static final String[][] ITEMS = {
				{"Ender Pearl", "ender_pearl"}, {"Golden Apple", "golden_apple"}, {"Enchanted Golden Apple", "enchanted_golden_apple"},
				{"Arrow", "arrow"}, {"End Crystal", "end_crystal"}, {"Obsidian", "obsidian"}, {"Respawn Anchor", "respawn_anchor"},
				{"Glowstone", "glowstone"}, {"Totem of Undying", "totem_of_undying"}, {"Experience Bottle", "experience_bottle"},
				{"Firework Rocket", "firework_rocket"}, {"Cooked Beef", "cooked_beef"}, {"Cooked Porkchop", "cooked_porkchop"},
				{"Snowball", "snowball"}, {"Fire Charge", "fire_charge"}, {"Shield", "shield"}, {"Water Bucket", "water_bucket"},
				{"Cobweb", "cobweb"}, {"Oak Planks", "oak_planks"}, {"Cobblestone", "cobblestone"}, {"Diamond", "diamond"}, {"Gold Ingot", "gold_ingot"}};
		private static final List<String> NAMES = new ArrayList<String>();

		static {
			for (String[] it : ITEMS) {
				NAMES.add(it[0]);
			}
		}

		final Setting.Device pick = add(new Setting.Device("item", "Item to count", new java.util.function.Supplier<List<String>>() {
			@Override
			public List<String> get() {
				return NAMES;
			}
		}).emptyLabel("The item in my hand"));

		ItemCounter() {
			super("item-counter", "Item Counter", "Counts any item you pick (pearls, golden apples, arrows, crystals...), or whatever you are holding.", Theme.I_BACKPACK, 0f, 0.54f);
		}

		private String chosen() {
			for (String[] it : ITEMS) {
				if (it[0].equals(pick.value)) {
					return it[1];
				}
			}
			return null;
		}

		@Override
		String item(Game g) {
			String id = chosen();
			return id != null ? id : g.hand.empty() ? null : g.hand.id;
		}

		@Override
		String itemName(String id) {
			for (String[] it : ITEMS) {
				if (it[1].equals(id)) {
					return it[0];
				}
			}
			StringBuilder sb = new StringBuilder();
			for (String part : id.split("_")) {
				if (!part.isEmpty()) {
					sb.append(sb.length() > 0 ? " " : "").append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
				}
			}
			return sb.toString();
		}

		static String pretty(String id) {
			StringBuilder sb = new StringBuilder();
			for (String part : id.split("_")) {
				if (!part.isEmpty()) {
					sb.append(sb.length() > 0 ? " " : "").append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
				}
			}
			return sb.toString();
		}

		@Override
		int sample() {
			return 16;
		}

		@Override
		String sampleItem() {
			String id = chosen();
			return id != null ? id : "ender_pearl";
		}
	}


	// ── combo counter ───────────────────────────────────────────────────────

	/** The counting rules, apart from the game: a hit adds one (at most one per damage-immunity window), damage taken or silence ends it. */
	static final class ComboTracker {
		static final long IMMUNITY_MS = 450;
		int combo, best;
		long lastHitAt, lastCountAt;

		/** An attack landed on something in reach. */
		void hit(long now) {
			if (combo > 0 && now - lastCountAt < IMMUNITY_MS) {
				return; // inside the target's damage immunity: this swing did no damage
			}
			combo++;
			best = Math.max(best, combo);
			lastCountAt = lastHitAt = now;
		}

		void damaged() {
			combo = 0;
		}

		/** No hit for {@code timeoutMs}: the combo is over. */
		void tick(long now, long timeoutMs) {
			if (combo > 0 && now - lastHitAt > timeoutMs) {
				combo = 0;
			}
		}
	}

	static final class Combo extends HudModule {
		final Setting.Num timeout = add(new Setting.Num("timeout", "Combo ends after", 3, 1, 10, 1, "s"));
		final Setting.Bool damage = add(new Setting.Bool("damage", "End the combo when I take damage", true));
		final Setting.Bool best = add(new Setting.Bool("best", "Show my best combo", true));
		private final ComboTracker tracker = new ComboTracker();
		private float lastHealth = -1;
		private boolean wasDown;

		Combo() {
			super("combo", "Combo Counter", "Counts the hits you land in a row. It ends when you take damage or stop hitting for a few seconds.", Theme.I_SWORD, false, 0f, 0.46f);
		}

		@Override
		public int needs() {
			return Game.NEED_VITALS;
		}

		@Override
		public void frame(Game g) {
			long now = System.currentTimeMillis();
			float hp = g.health + g.absorption;
			if (damage.value && lastHealth >= 0 && hp < lastHealth - 0.01f) {
				tracker.damaged();
			}
			lastHealth = hp;
			boolean down = g.down[Game.ATTACK];
			if (down && !wasDown) {
				double d = HudModules.mc().reach(); // something with a hitbox is under the crosshair
				if (!Double.isNaN(d) && d > 0 && d < 8) {
					tracker.hit(now);
				}
			}
			wasDown = down;
			tracker.tick(now, (long) (timeout.value * 1000));
		}

		@Override
		protected int lines(Game g, String[] l, String[] v) {
			int c = g.sample ? 7 : tracker.combo, b = g.sample ? 12 : tracker.best;
			l[0] = "Combo";
			v[0] = String.valueOf(c);
			if (best.value) {
				l[1] = "Best";
				v[1] = String.valueOf(Math.max(b, c));
				return 2;
			}
			return 1;
		}
	}

	// ── cooldowns ───────────────────────────────────────────────────────────

	static final class Cooldowns extends StatusModules.Box {
		final Setting.Bool names = add(new Setting.Bool("names", "Show item names", false));
		private final String[] ids = new String[8];
		private final float[] progress = new float[8];
		private final Game.Slot icon = new Game.Slot();
		private int count;
		private long next;

		Cooldowns() {
			super("cooldowns", "Item Cooldowns", "A bar that drains for every item on cooldown: ender pearls, shields, chorus fruit and more.", Theme.I_TIMER, false, 0f, 0.6f);
			drop(labels, color);
			style.initial(STYLE_CARD);
		}

		@Override
		public void frame(Game g) {
			long now = System.currentTimeMillis();
			if (now < next) {
				return;
			}
			next = now + 60;
			count = HudModules.mc().cooldowns(ids, progress);
		}

		@Override
		public boolean visible(Game g) {
			return g.sample || count > 0;
		}

		@Override
		void render(Canvas c, Ui ui, Game g, float x, float y) {
			float s = scale.value, pad = pad();
			int n = g.sample ? 2 : count;
			float ic = 16 * s, barW = 54 * s, barH = 4 * s, rowH = ic + 4 * s, gap = 3 * s;
			float fs = 10.5f * s;
			float nameW = 0;
			for (int i = 0; i < n && names.value; i++) {
				String id = g.sample ? (i == 0 ? "ender_pearl" : "shield") : ids[i];
				nameW = Math.max(nameW, c.textWidth(Fonts.REGULAR, fs, ItemCounter.pretty(id)));
			}
			float w = pad * 2 + ic + 6 * s + Math.max(barW, nameW), h = pad * 2 + Math.max(1, n) * rowH + Math.max(0, n - 1) * gap;
			if (ui != null) {
				for (int i = 0; i < n; i++) {
					String id = g.sample ? (i == 0 ? "ender_pearl" : "shield") : ids[i];
					float f = g.sample ? (i == 0 ? 0.6f : 0.25f) + 0.15f * (float) Math.sin(System.currentTimeMillis() / 600.0) : progress[i];
					float ry = y + pad + i * (rowH + gap);
					icon.set(id, 1, 0, 0);
					StatusModules.item(c, icon, x + pad, ry + (rowH - ic) / 2, ic, 0xFFFFFFFF);
					float bx = x + pad + ic + 6 * s;
					float by = ry + (names.value ? rowH - barH - 1 * s : (rowH - barH) / 2);
					if (names.value) {
						text(c, Fonts.REGULAR, fs, ItemCounter.pretty(id), bx, ry, 0xFFC4C8D4);
					}
					c.round(bx, by, barW, barH, barH / 2, 0x33FFFFFF);
					c.round(bx, by, Math.max(barH, barW * Math.max(0f, Math.min(1f, f))), barH, barH / 2, f > 0.66f ? 0xFFF87171 : f > 0.33f ? 0xFFFBBF24 : 0xFF4ADE80);
				}
			}
			done(w, h);
		}
	}

	// ── playtime ────────────────────────────────────────────────────────────

	static final class Playtime extends HudModule {
		private static long total = -1, session, last, savedAt;
		private static boolean dirty;
		final Setting.Choice mode = add(new Setting.Choice("mode", "Show", 2, "Session", "Total", "Both"));

		Playtime() {
			super("playtime", "Playtime", "How long you have played: this session and your total time in Minecraft with Native.", Theme.I_TIMER, false, 0f, 0.3f);
		}

		private static java.nio.file.Path file() {
			java.nio.file.Path dir = NativeState.get().gameDir();
			return dir == null ? null : dir.resolve("config").resolve("native-playtime.json");
		}

		private static void load() {
			total = 0;
			try {
				java.nio.file.Path f = file();
				if (f != null && java.nio.file.Files.isRegularFile(f)) {
					String json = new String(java.nio.file.Files.readAllBytes(f), java.nio.charset.StandardCharsets.UTF_8);
					total = Math.max(0, new com.google.gson.JsonParser().parse(json).getAsJsonObject().get("totalMs").getAsLong());
				}
			} catch (Throwable t) {
				Log.debug("Playtime not read: {}", t.toString());
			}
		}

		private static void save() {
			dirty = false;
			savedAt = System.currentTimeMillis();
			try {
				java.nio.file.Path f = file();
				if (f != null) {
					java.nio.file.Files.createDirectories(f.getParent());
					com.google.gson.JsonObject o = new com.google.gson.JsonObject();
					o.addProperty("totalMs", total);
					java.nio.file.Files.write(f, o.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
				}
			} catch (Throwable t) {
				Log.debug("Playtime not saved: {}", t.toString());
			}
		}

		/** Counts the time spent in a world (per frame, so a freeze or a pause of the window never adds a jump). */
		static void tick(Game g) {
			long now = System.currentTimeMillis();
			if (total < 0) {
				load();
				savedAt = now;
			}
			if (last != 0 && g.inWorld) {
				long dt = Math.min(1000, now - last);
				if (dt > 0) {
					session += dt;
					total += dt;
					dirty = true;
				}
			}
			last = now;
			if (dirty && now - savedAt > 30_000) {
				save();
			}
		}

		static String hms(long ms) {
			long sec = ms / 1000;
			long h = sec / 3600, m = sec % 3600 / 60, s = sec % 60;
			return h > 0 ? h + ":" + two(m) + ":" + two(s) : m + ":" + two(s);
		}

		static String span(long ms) {
			long min = ms / 60000;
			long h = min / 60;
			if (h >= 100) {
				return h + "h";
			}
			return h > 0 ? h + "h " + two(min % 60) + "m" : min + "m";
		}

		@Override
		protected int lines(Game g, String[] l, String[] v) {
			long ses = g.sample ? 5025000L : session, tot = g.sample ? 142_380_000L : Math.max(0, total);
			int n = 0;
			if (mode.value != 1) {
				l[n] = "Session";
				v[n++] = hms(ses);
			}
			if (mode.value != 0) {
				l[n] = "Total";
				v[n++] = span(tot);
			}
			return n;
		}
	}

	// ── reach display ───────────────────────────────────────────────────────

	static final class ReachDisplay extends HudModule {
		final Setting.Choice decimals = add(new Setting.Choice("decimals", "Decimals", 1, "1", "2", "3"));
		final Setting.Num hold = add(new Setting.Num("hold", "Keep the last hit for", 4, 1, 15, 1, "s"));
		private double last = Double.NaN;
		private long lastAt;
		private boolean wasDown;

		ReachDisplay() {
			super("reach", "Reach Display", "Shows how far away the player you just hit was (in blocks, from your eyes to where you hit).", Theme.I_CROSSHAIR, false, 0f, 0.38f);
		}

		@Override
		public void frame(Game g) {
			boolean down = g.down[Game.ATTACK];
			if (down && !wasDown) {
				double d = HudModules.mc().reach();
				if (!Double.isNaN(d) && d > 0 && d < 8) {
					last = d;
					lastAt = System.currentTimeMillis();
				}
			}
			wasDown = down;
		}

		@Override
		protected int lines(Game g, String[] l, String[] v) {
			double d = g.sample ? 2.94 : last;
			boolean fresh = g.sample || !Double.isNaN(d) && System.currentTimeMillis() - lastAt < (long) (hold.value * 1000);
			l[0] = "Reach";
			v[0] = fresh ? String.format(Locale.ROOT, "%." + (decimals.value + 1) + "f", d) + " blocks" : "-";
			return 1;
		}
	}
}
