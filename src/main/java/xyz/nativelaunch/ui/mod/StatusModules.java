package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.ui.gfx.Image;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Status modules: potion effects, armour and inventory. They
 * draw the game's own textures (mob effect icons, item icons, heart / food / armour sprites), read straight from the
 * running game's assets, so they follow the resource pack the player uses.
 */
public final class StatusModules {
	private StatusModules() {
	}

	static void register() {
		Modules.register(new Effects());
		Modules.register(new Armor());
		Modules.register(new InventoryGrid());
	}

	private static final int GOOD = 0xFF4ADE80, BAD = 0xFFF87171, GOLD = 0xFFFFC832;

	/** Shared plumbing: one measure / draw routine, a card / text / brackets frame around it. */
	abstract static class Box extends HudModule {
		final float[] size = new float[2];

		Box(String id, String name, String description, int icon, boolean enabled, float x, float y) {
			super(id, name, description, icon, enabled, x, y);
		}

		/** Lays out ({@code ui == null}) or draws the module with its top-left corner at x, y, and sets {@link #size}. */
		abstract void render(Canvas c, Ui ui, Game g, float x, float y);

		@Override
		public float[] measure(Canvas c, Game g) {
			render(c, null, g, 0, 0);
			return size;
		}

		@Override
		public void paint(Ui ui, float x, float y, Game g) {
			Canvas c = ui.c;
			float[] sz = measure(c, g);
			float w = sz[0], h = sz[1], s = scale.value;
			if (style.value == STYLE_CARD) {
				background(c, x, y, w, h, s);
			} else if (style.value == STYLE_BRACKETS) {
				brackets(c, x, y, w, h, s);
			}
			render(c, ui, g, x, y);
		}

		/** Space between the frame and the content. */
		float pad() {
			float s = scale.value;
			return style.value == STYLE_CARD ? 5 * s : style.value == STYLE_BRACKETS ? 4 * s : s;
		}

		void done(float w, float h) {
			size[0] = Math.max(1, w);
			size[1] = Math.max(1, h);
		}
	}

	/** An item's icon from the game's textures, or a plain tile with its first letter when the game has none. */
	static void item(Canvas c, Game.Slot sl, float x, float y, float size, int tint) {
		String id = sl.id;
		Image img = Vanilla.item(sl);
		if (img != null) {
			Vanilla.draw(c, img, x, y, size, size, tint);
			return;
		}
		c.round(x + size * 0.08f, y + size * 0.08f, size * 0.84f, size * 0.84f, size * 0.2f, Theme.alpha(0xFF52525B, ((tint >>> 24) / 255f)));
		String t = id == null || id.isEmpty() ? "?" : id.substring(0, 1).toUpperCase();
		float fs = size * 0.6f;
		c.text(Fonts.SEMIBOLD, fs, t, x + (size - c.textWidth(Fonts.SEMIBOLD, fs, t)) / 2, y + (size - c.lineHeight(Fonts.SEMIBOLD, fs)) / 2, tint);
	}

	/** The thin durability bar the game draws on a worn item (13 of 16 px wide, near the bottom). */
	static void durabilityBar(Canvas c, Game.Slot sl, float x, float y, float size) {
		if (sl.max <= 0 || sl.damage <= 0) {
			return;
		}
		float f = sl.durability();
		float bx = x + size * 0.125f, bw = size * 0.8125f, by = y + size * 0.8125f, bh = Math.max(1f, size * 0.125f);
		c.fill(bx, by, bw, bh, 0xFF000000);
		c.fill(bx, by, Math.max(1f, bw * f), Math.max(1f, bh - size * 0.0625f), Status.wear(f));
	}

	static int pulse(long now, float period, float lo) {
		return Math.round(255 * (lo + (1 - lo) * (0.5f + 0.5f * (float) Math.sin(now / (double) period))));
	}

	// ── potion effects ───────────────────────────────────────────────────

	static final class Effects extends Box {
		final Setting.Choice layout = add(new Setting.Choice("layout", "Layout", 0, "List", "Row"));
		final Setting.Bool icons = fixed(new Setting.Bool("icons", "Show icons", true));
		final Setting.Bool names = fixed(new Setting.Bool("names", "Show names", true));
		final Setting.Bool level = fixed(new Setting.Bool("level", "Show level", true));
		final Setting.Bool time = fixed(new Setting.Bool("time", "Show time left", true));
		final Setting.Bool bar = fixed(new Setting.Bool("bar", "Time bar", true));
		final Setting.Bool byType = fixed(new Setting.Bool("type", "Colour good / bad effects", true));
		final Setting.Bool beacon = fixed(new Setting.Bool("beacon", "Show beacon effects", true));
		final Setting.Bool flash = fixed(new Setting.Bool("flash", "Flash when about to run out", true));
		final Setting.Choice sort = fixed(new Setting.Choice("sort", "Sort by", 0, "Time left", "Level", "Name"));
		final Setting.Bool hide = fixed(new Setting.Bool("hide", "Hide the vanilla effect icons", true));
		private final ArrayList<Game.Effect> view = new ArrayList<Game.Effect>();
		/** id -> {longest time seen, frame it was last seen}: the bar shows time left against the full length. */
		private final Map<String, int[]> peak = new HashMap<String, int[]>();
		private int gen;

		private static final Comparator<Game.Effect> BY_TIME = new Comparator<Game.Effect>() {
			@Override
			public int compare(Game.Effect a, Game.Effect b) {
				return Integer.compare(a.infinite ? Integer.MAX_VALUE : a.ticks, b.infinite ? Integer.MAX_VALUE : b.ticks);
			}
		};
		private static final Comparator<Game.Effect> BY_LEVEL = new Comparator<Game.Effect>() {
			@Override
			public int compare(Game.Effect a, Game.Effect b) {
				return b.amp != a.amp ? Integer.compare(b.amp, a.amp) : BY_TIME.compare(a, b);
			}
		};
		private static final Comparator<Game.Effect> BY_NAME = new Comparator<Game.Effect>() {
			@Override
			public int compare(Game.Effect a, Game.Effect b) {
				return Status.effectName(a.id).compareTo(Status.effectName(b.id));
			}
		};

		Effects() {
			super("effects", "Potion Effects", "Your active potion effects with the game's own icons, level and time left.", Theme.I_FLASK, false, 1f, 0.04f);
			drop(labels);
		}

		@Override
		public int needs() {
			return Game.NEED_EFFECTS;
		}

		@Override
		public boolean visible(Game g) {
			return g.sample || collect(g) > 0;
		}

		@Override
		public void frame(Game g) {
			if (hide.value && g.hudAllowed && g.vitalsOk) {
				Overlays.replaceEffects();
			}
			gen++;
			for (int i = 0; i < g.effectCount; i++) {
				Game.Effect e = g.effects[i];
				int[] p = peak.get(e.id);
				if (p == null) {
					peak.put(e.id, new int[] {e.ticks, gen});
				} else {
					p[0] = Math.max(p[0], e.ticks);
					p[1] = gen;
				}
			}
			for (Iterator<int[]> it = peak.values().iterator(); it.hasNext();) {
				if (it.next()[1] != gen) {
					it.remove();
				}
			}
		}

		private int collect(Game g) {
			view.clear();
			for (int i = 0; i < g.effectCount; i++) {
				Game.Effect e = g.effects[i];
				if (e.ambient && !beacon.value) {
					continue;
				}
				view.add(e);
			}
			if (view.size() > 1) {
				Collections.sort(view, sort.value == 1 ? BY_LEVEL : sort.value == 2 ? BY_NAME : BY_TIME);
			}
			while (view.size() > 12) {
				view.remove(view.size() - 1);
			}
			return view.size();
		}

		private float fraction(Game g, Game.Effect e) {
			if (e.infinite) {
				return 1f;
			}
			int[] p = g.sample ? null : peak.get(e.id);
			int total = g.sample ? 20 * 600 : p == null ? e.ticks : p[0];
			return Math.max(0f, Math.min(1f, e.ticks / (float) Math.max(1, total)));
		}

		private String title(Game.Effect e) {
			String n = names.value ? Status.effectName(e.id) : "";
			String l = level.value && e.amp > 0 ? Status.roman(e.amp + 1) : "";
			return n.isEmpty() ? l : l.isEmpty() ? n : n + " " + l;
		}

		@Override
		void render(Canvas c, Ui ui, Game g, float x, float y) {
			float s = scale.value, pad = pad();
			int n = collect(g);
			if (n == 0) {
				done(1, 1);
				return;
			}
			if (ui != null && !g.sample && hide.value) {
				Overlays.replaceEffects();
			}
			long now = ui == null ? 0 : ui.now;
			float ic = 18 * s, gap = 3 * s;
			boolean showIcon = icons.value, barOn = bar.value, timeOn = time.value;
			float nf = 11.5f * s, tf = 10.5f * s;
			float lh1 = c.lineHeight(Fonts.SEMIBOLD, nf), lh2 = c.lineHeight(Fonts.MEDIUM, tf);
			if (layout.value == 0) {
				boolean nameLine = names.value || level.value;
				float textH = nameLine && timeOn ? lh1 + lh2 : nameLine ? lh1 : timeOn ? lh2 : 0;
				float content = Math.max(showIcon ? ic : 0, textH), rowH = content + (barOn ? 4 * s : 0);
				float inner = 0;
				for (int i = 0; i < n; i++) {
					Game.Effect e = view.get(i);
					float tw = Math.max(nameLine ? c.textWidth(Fonts.SEMIBOLD, nf, title(e)) : 0, timeOn ? c.textWidth(Fonts.MEDIUM, tf, e.infinite ? "Infinite" : Status.time(e.ticks)) : 0);
					inner = Math.max(inner, (showIcon ? ic + (tw > 0 ? 6 * s : 0) : 0) + tw);
				}
				inner = Math.max(inner, 48 * s);
				if (ui != null) {
					float ry = y + pad;
					for (int i = 0; i < n; i++) {
						Game.Effect e = view.get(i);
						boolean urgent = flash.value && !e.infinite && !e.ambient && Status.seconds(e.ticks) <= 10;
						int a = urgent ? pulse(now, 140, 0.45f) : 255;
						int tint = a << 24 | 0xFFFFFF;
						int accent = byType.value ? (Status.harmful(e.id) ? BAD : GOOD) : color.value;
						float tx = x + pad;
						if (showIcon) {
							effectIcon(c, e.id, tx, ry + (content - ic) / 2, ic, tint);
							tx += ic + 6 * s;
						}
						float ty = ry + (content - textH) / 2;
						if (nameLine) {
							text(c, Fonts.SEMIBOLD, nf, title(e), tx, ty, Theme.alpha(color.value, a / 255f));
							ty += lh1;
						}
						if (timeOn) {
							int tc = urgent ? BAD : Theme.alpha(color.value, 0.72f);
							text(c, Fonts.MEDIUM, tf, e.infinite ? "Infinite" : Status.time(e.ticks), tx, ty, Theme.alpha(tc, a / 255f));
						}
						if (barOn) {
							float by = ry + content + 1.5f * s, bh = Math.max(1.5f, 2 * s);
							c.round(x + pad, by, inner, bh, bh / 2, 0x33FFFFFF);
							c.round(x + pad, by, Math.max(bh, inner * fraction(g, e)), bh, bh / 2, Theme.alpha(accent, a / 255f));
						}
						ry += rowH + gap;
					}
				}
				done(inner + 2 * pad + 4 * s, n * rowH + (n - 1) * gap + 2 * pad);
				return;
			}
			// row of tiles: icon (or the name when icons are off), level badge, time below
			float tileH = 0, total = 0;
			float[] tw = new float[n];
			for (int i = 0; i < n; i++) {
				Game.Effect e = view.get(i);
				float top = showIcon ? ic : c.textWidth(Fonts.SEMIBOLD, nf, title(e).isEmpty() ? Status.effectName(e.id) : title(e));
				float w = Math.max(top, timeOn ? c.textWidth(Fonts.MEDIUM, tf, e.infinite ? "Inf" : Status.time(e.ticks)) : 0) + 4 * s;
				tw[i] = w;
				total += w + (i > 0 ? gap : 0);
			}
			tileH = (showIcon ? ic : lh1) + (timeOn ? lh2 + s : 0) + (barOn ? 4 * s : 0);
			if (ui != null) {
				float tx = x + pad;
				for (int i = 0; i < n; i++) {
					Game.Effect e = view.get(i);
					boolean urgent = flash.value && !e.infinite && !e.ambient && Status.seconds(e.ticks) <= 10;
					int a = urgent ? pulse(now, 140, 0.45f) : 255;
					int accent = byType.value ? (Status.harmful(e.id) ? BAD : GOOD) : color.value;
					float cx = tx + tw[i] / 2, ty = y + pad;
					if (showIcon) {
						effectIcon(c, e.id, cx - ic / 2, ty, ic, a << 24 | 0xFFFFFF);
						if (level.value && e.amp > 0) {
							String l = Status.roman(e.amp + 1);
							float lf = 8.5f * s;
							text(c, Fonts.SEMIBOLD, lf, l, cx + ic / 2 - c.textWidth(Fonts.SEMIBOLD, lf, l), ty - s, Theme.alpha(0xFFFFFFFF, a / 255f));
						}
						ty += ic;
					} else {
						String t = title(e).isEmpty() ? Status.effectName(e.id) : title(e);
						text(c, Fonts.SEMIBOLD, nf, t, cx - c.textWidth(Fonts.SEMIBOLD, nf, t) / 2, ty, Theme.alpha(color.value, a / 255f));
						ty += lh1;
					}
					if (timeOn) {
						String t = e.infinite ? "Inf" : Status.time(e.ticks);
						text(c, Fonts.MEDIUM, tf, t, cx - c.textWidth(Fonts.MEDIUM, tf, t) / 2, ty + s, Theme.alpha(urgent ? BAD : Theme.alpha(color.value, 0.72f), a / 255f));
						ty += lh2 + s;
					}
					if (barOn) {
						float bh = Math.max(1.5f, 2 * s);
						c.round(tx + s, ty + 1.5f * s, tw[i] - 2 * s, bh, bh / 2, 0x33FFFFFF);
						c.round(tx + s, ty + 1.5f * s, Math.max(bh, (tw[i] - 2 * s) * fraction(g, e)), bh, bh / 2, Theme.alpha(accent, a / 255f));
					}
					tx += tw[i] + gap;
				}
			}
			done(total + 2 * pad + 4 * s, tileH + 2 * pad);
		}

		private static void effectIcon(Canvas c, String id, float x, float y, float size, int tint) {
			Image img = Vanilla.effect(id);
			if (img != null) {
				Vanilla.draw(c, img, x, y, size, size, tint);
			} else {
				c.round(x + 1, y + 1, size - 2, size - 2, size * 0.2f, Theme.alpha(Status.harmful(id) ? BAD : GOOD, 0.35f * ((tint >>> 24) / 255f)));
				String t = Status.effectName(id).isEmpty() ? "?" : Status.effectName(id).substring(0, 1);
				float fs = size * 0.58f;
				c.text(Fonts.SEMIBOLD, fs, t, x + (size - c.textWidth(Fonts.SEMIBOLD, fs, t)) / 2, y + (size - c.lineHeight(Fonts.SEMIBOLD, fs)) / 2, tint);
			}
		}
	}

	// ── armour status ────────────────────────────────────────────────────

	static final class Armor extends Box {
		final Setting.Choice layout = add(new Setting.Choice("layout", "Layout", 0, "List", "Row"));
		final Setting.Choice text = fixed(new Setting.Choice("text", "Durability text", 1, "Remaining", "Percent", "Remaining / max", "Off").shorts("Left", "Percent", "Left / max", "Off"));
		final Setting.Bool bar = fixed(new Setting.Bool("bar", "Durability bar on the icon", true));
		final Setting.Bool wear = fixed(new Setting.Bool("wear", "Colour text by wear", true));
		final Setting.Num warn = fixed(new Setting.Num("warn", "Flash red below", 15, 0, 50, 5, "%"));
		final Setting.Bool hand = fixed(new Setting.Bool("hand", "Show the item in your hand", true));
		final Setting.Bool off = fixed(new Setting.Bool("offhand", "Show the off-hand item", false));
		final Setting.Bool total = fixed(new Setting.Bool("total", "Count matching items in the inventory", true));
		final Setting.Bool empty = fixed(new Setting.Bool("empty", "Show empty armour slots", false));
		private final Game.Slot[] list = new Game.Slot[6];
		private final String[] texts = new String[6];

		Armor() {
			super("armor", "Armor Status", "Armour pieces and your held item with their durability, using the game's item icons (custom items too).", Theme.I_SHIELD, false, 0f, 0.7f);
			drop(labels);
			style.initial(STYLE_TEXT);
		}

		@Override
		public int needs() {
			return Game.NEED_ARMOR | (total.value ? Game.NEED_INVENTORY : 0);
		}

		private int collect(Game g) {
			int n = 0;
			for (int i = 0; i < 4; i++) {
				if (empty.value || !g.armor[i].empty()) {
					list[n++] = g.armor[i];
				}
			}
			if (hand.value && !g.hand.empty()) {
				list[n++] = g.hand;
			}
			if (off.value && !g.offhand.empty()) {
				list[n++] = g.offhand;
			}
			for (int i = 0; i < n; i++) {
				texts[i] = label(g, list[i]);
			}
			return n;
		}

		@Override
		public boolean visible(Game g) {
			return g.sample || collect(g) > 0;
		}

		private static boolean same(Game.Slot a, Game.Slot b) {
			return a.id.equals(b.id) && (a.model == null ? b.model == null : a.model.equals(b.model))
					&& (Float.isNaN(a.cmd) ? Float.isNaN(b.cmd) : a.cmd == b.cmd);
		}

		/** How many of this item the player carries (the held stack plus every matching stack in the inventory). */
		private int carried(Game g, Game.Slot sl) {
			if (!total.value || g.sample) {
				return sl.count;
			}
			int n = 0;
			boolean seen = false;
			for (int i = 0; i < g.inv.length; i++) {
				Game.Slot o = g.inv[i];
				if (!o.empty() && same(o, sl)) {
					n += o.count;
					seen |= i == g.selected;
				}
			}
			if (sl != g.offhand && !g.offhand.empty() && same(g.offhand, sl)) {
				n += g.offhand.count;
			}
			if (sl == g.offhand || !seen) {
				n = Math.max(n, sl.count); // the off-hand (or an armour slot) is not part of the 36 slots
			}
			return Math.max(1, n);
		}

		private String label(Game g, Game.Slot sl) {
			if (sl.empty()) {
				return "";
			}
			if (sl.max > 0) {
				switch (text.value) {
					case 0:
						return String.valueOf(sl.max - sl.damage);
					case 1:
						return Math.round(sl.durability() * 100) + "%";
					case 2:
						return (sl.max - sl.damage) + "/" + sl.max;
					default:
						return "";
				}
			}
			return String.valueOf(carried(g, sl));
		}

		private int textColor(Game.Slot sl, long now) {
			int col = color.value;
			if (sl.max > 0 && sl.damage > 0) {
				if (wear.value) {
					col = Status.wear(sl.durability());
				}
				if (warn.value > 0 && sl.durability() * 100 < warn.value) {
					col = Theme.mix(col, BAD, 0.5f + 0.5f * (float) Math.sin(now / 160.0));
				}
			}
			return col;
		}

		@Override
		void render(Canvas c, Ui ui, Game g, float x, float y) {
			float s = scale.value, pad = pad();
			int n = collect(g);
			if (n == 0) {
				done(1, 1);
				return;
			}
			long now = ui == null ? 0 : ui.now;
			float ic = 16 * s, tf = 11.5f * s, gap = 5 * s;
			float lh = c.lineHeight(Fonts.SEMIBOLD, tf);
			if (layout.value == 0) {
				// icon + number per row, like the classic armour HUDs; on the right half of the screen the numbers sit left of the icons
				boolean right = fx > 0.5f;
				float rowH = ic + 3 * s, tw = 0;
				for (int i = 0; i < n; i++) {
					tw = Math.max(tw, c.textWidth(Fonts.SEMIBOLD, tf, texts[i]));
				}
				float w = ic + (tw > 0 ? gap + tw : 0);
				if (ui != null) {
					for (int i = 0; i < n; i++) {
						Game.Slot sl = list[i];
						float ry = y + pad + i * rowH;
						float ix = right ? x + pad + w - ic : x + pad;
						drawSlot(c, sl, ix, ry, ic);
						String t = texts[i];
						if (!t.isEmpty()) {
							float t0 = c.textWidth(Fonts.SEMIBOLD, tf, t);
							float tx = right ? ix - gap - t0 : ix + ic + gap;
							text(c, Fonts.SEMIBOLD, tf, t, tx, ry + (ic - lh) / 2 + s * 0.5f, textColor(sl, now));
						}
					}
				}
				done(w + 2 * pad, n * rowH - 3 * s + 2 * pad);
				return;
			}
			float rowGap = 4 * s, totalW = 0;
			float[] tw = new float[n];
			boolean anyText = false;
			for (int i = 0; i < n; i++) {
				anyText |= !texts[i].isEmpty();
				tw[i] = Math.max(ic, c.textWidth(Fonts.SEMIBOLD, tf, texts[i]));
				totalW += tw[i] + (i > 0 ? rowGap : 0);
			}
			if (ui != null) {
				float tx = x + pad;
				for (int i = 0; i < n; i++) {
					Game.Slot sl = list[i];
					drawSlot(c, sl, tx + (tw[i] - ic) / 2, y + pad, ic);
					String t = texts[i];
					if (!t.isEmpty()) {
						text(c, Fonts.SEMIBOLD, tf, t, tx + (tw[i] - c.textWidth(Fonts.SEMIBOLD, tf, t)) / 2, y + pad + ic + 2 * s, textColor(sl, now));
					}
					tx += tw[i] + rowGap;
				}
			}
			done(totalW + 2 * pad, ic + (anyText ? lh + 2 * s : 0) + 2 * pad);
		}

		private void drawSlot(Canvas c, Game.Slot sl, float x, float y, float ic) {
			if (sl.empty()) {
				c.round(x + ic * 0.1f, y + ic * 0.1f, ic * 0.8f, ic * 0.8f, 3, 0x14FFFFFF);
				return;
			}
			item(c, sl, x, y, ic, 0xFFFFFFFF);
			if (bar.value) {
				durabilityBar(c, sl, x, y, ic);
			}
		}
	}

	// ── inventory ────────────────────────────────────────────────────────

	static final class InventoryGrid extends Box {
		final Setting.Choice layout = add(new Setting.Choice("layout", "Layout", 0, "Horizontal", "Vertical"));
		final Setting.Bool hotbar = fixed(new Setting.Bool("hotbar", "Show the hotbar row", false));
		final Setting.Bool slots = fixed(new Setting.Bool("slots", "Slot backgrounds", true));
		final Setting.Bool counts = fixed(new Setting.Bool("counts", "Item counts", true));
		final Setting.Bool bars = fixed(new Setting.Bool("bars", "Durability bars", true));
		final Setting.Bool selected = fixed(new Setting.Bool("selected", "Highlight the selected slot", true));
		final Setting.Bool hideEmpty = fixed(new Setting.Bool("hide", "Hide when the inventory is empty", false));

		InventoryGrid() {
			super("inventory", "Inventory", "Your backpack on screen without opening it, with the game's item icons and counts (custom items too).", Theme.I_BACKPACK, false, 1f, 0.8f);
			drop(labels, color);
		}

		@Override
		public int needs() {
			return Game.NEED_INVENTORY;
		}

		@Override
		public boolean visible(Game g) {
			if (g.sample || !hideEmpty.value) {
				return true;
			}
			for (int i = hotbar.value ? 0 : 9; i < 36; i++) {
				if (!g.inv[i].empty()) {
					return true;
				}
			}
			return false;
		}

		@Override
		float pad() {
			float s = scale.value;
			return style.value == STYLE_CARD ? 4 * s : style.value == STYLE_BRACKETS ? 4 * s : 0;
		}

		@Override
		void render(Canvas c, Ui ui, Game g, float x, float y) {
			float s = scale.value, pad = pad();
			float cell = 20 * s, step = 21 * s, ic = 16 * s, sep = 4 * s;
			int lines = 3 + (hotbar.value ? 1 : 0);
			boolean vertical = layout.value == 1;
			// horizontal: 9 columns x 3 (+ hotbar) rows like the inventory screen; vertical: the same grid turned on its side
			float along = 9 * step - (step - cell), across = lines * step - (step - cell) + (hotbar.value ? sep : 0);
			float w = (vertical ? across : along) + 2 * pad, h = (vertical ? along : across) + 2 * pad;
			if (ui != null) {
				float cr = 3 * s, fs = 10.5f * s;
				float flh = c.lineHeight(Fonts.BOLD, fs);
				for (int r = 0; r < lines; r++) {
					boolean hot = r == 3;
					float off = r * step + (hot ? sep : 0);
					for (int col = 0; col < 9; col++) {
						Game.Slot sl = g.inv[hot ? col : 9 + r * 9 + col];
						float sx = x + pad + (vertical ? off : col * step), sy = y + pad + (vertical ? col * step : off);
						if (slots.value) {
							c.round(sx, sy, cell, cell, cr, 0x66000000);
							c.fill(sx + cr, sy, cell - 2 * cr, Math.max(1f, s * 0.75f), 0x12FFFFFF);
						}
						if (hot && selected.value && col == g.selected) {
							c.outline(sx - s, sy - s, cell + 2 * s, cell + 2 * s, cr + s, Math.max(1f, 1.5f * s), 0xE6FFFFFF);
						}
						if (sl.empty()) {
							continue;
						}
						float ix = sx + (cell - ic) / 2, iy = sy + (cell - ic) / 2;
						item(c, sl, ix, iy, ic, 0xFFFFFFFF);
						if (bars.value) {
							durabilityBar(c, sl, ix, iy, ic);
						}
						if (counts.value && sl.count > 1) {
							String t = String.valueOf(sl.count);
							// bottom-right like the game, nudged slightly past the icon
							text(c, Fonts.BOLD, fs, t, sx + cell - c.textWidth(Fonts.BOLD, fs, t) - 0.5f * s, sy + cell - flh + 1.5f * s, 0xFFFFFFFF);
						}
					}
				}
			}
			done(w, h);
		}
	}
}
