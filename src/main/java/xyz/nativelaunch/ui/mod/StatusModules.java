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
 * Status modules: potion effects, armour, inventory, health and food, and the health of whatever you are looking at. They
 * draw the game's own textures (mob effect icons, item icons, heart / food / armour sprites), read straight from the
 * running game's assets, so they follow the resource pack the player uses.
 */
public final class StatusModules {
	private StatusModules() {
	}

	static void register() {
		Modules.register(new Effects());
		Modules.register(new Armor());
		Modules.register(new Vitals());
		Modules.register(new Target());
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
	static void item(Canvas c, String id, float x, float y, float size, int tint) {
		Image img = Vanilla.item(id);
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
		final Setting.Bool icons = add(new Setting.Bool("icons", "Show icons", true));
		final Setting.Bool names = add(new Setting.Bool("names", "Show names", true));
		final Setting.Bool level = add(new Setting.Bool("level", "Show level", true));
		final Setting.Bool time = add(new Setting.Bool("time", "Show time left", true));
		final Setting.Bool bar = add(new Setting.Bool("bar", "Time bar", true));
		final Setting.Bool byType = add(new Setting.Bool("type", "Colour good / bad effects", true));
		final Setting.Bool beacon = add(new Setting.Bool("beacon", "Show beacon effects", true));
		final Setting.Bool flash = add(new Setting.Bool("flash", "Flash when about to run out", true));
		final Setting.Choice sort = add(new Setting.Choice("sort", "Sort by", 0, "Time left", "Level", "Name"));
		final Setting.Num max = add(new Setting.Num("max", "Most effects shown", 8, 1, 16, 1, ""));
		final Setting.Bool hide = add(new Setting.Bool("hide", "Hide the vanilla effect icons", true));
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
			while (view.size() > (int) max.value) {
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
				done(inner + 2 * pad, n * rowH + (n - 1) * gap + 2 * pad);
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
			done(total + 2 * pad, tileH + 2 * pad);
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
		final Setting.Choice text = add(new Setting.Choice("text", "Durability text", 0, "Remaining", "Percent", "Remaining / max", "Off"));
		final Setting.Bool bar = add(new Setting.Bool("bar", "Durability bar on the icon", true));
		final Setting.Bool wear = add(new Setting.Bool("wear", "Colour text by wear", true));
		final Setting.Num warn = add(new Setting.Num("warn", "Flash red below", 15, 0, 50, 5, "%"));
		final Setting.Bool hand = add(new Setting.Bool("hand", "Show the item in your hand", true));
		final Setting.Bool off = add(new Setting.Bool("offhand", "Show the off-hand item", false));
		final Setting.Bool empty = add(new Setting.Bool("empty", "Show empty armour slots", false));
		private final Game.Slot[] list = new Game.Slot[6];

		Armor() {
			super("armor", "Armor Status", "Armour pieces and your held item with their durability, using the game's item icons.", Theme.I_SHIELD, false, 0f, 0.7f);
			drop(labels);
		}

		@Override
		public int needs() {
			return Game.NEED_ARMOR;
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
			return n;
		}

		@Override
		public boolean visible(Game g) {
			return g.sample || collect(g) > 0;
		}

		private String label(Game.Slot sl) {
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
			return sl.count > 1 ? "x" + sl.count : "";
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
			float ic = 18 * s, tf = 12 * s;
			float lh = c.lineHeight(Fonts.SEMIBOLD, tf);
			if (layout.value == 0) {
				float tw = 0;
				for (int i = 0; i < n; i++) {
					tw = Math.max(tw, c.textWidth(Fonts.SEMIBOLD, tf, label(list[i])));
				}
				float rowH = ic + 2 * s, w = ic + (tw > 0 ? 6 * s + tw : 0);
				if (ui != null) {
					float ry = y + pad;
					for (int i = 0; i < n; i++) {
						Game.Slot sl = list[i];
						drawSlot(c, sl, x + pad, ry + (rowH - ic) / 2, ic);
						String t = label(sl);
						if (!t.isEmpty()) {
							text(c, Fonts.SEMIBOLD, tf, t, x + pad + ic + 6 * s, ry + (rowH - lh) / 2, textColor(sl, now));
						}
						ry += rowH;
					}
				}
				done(w + 2 * pad, n * rowH + 2 * pad);
				return;
			}
			float gap = 4 * s, total = 0;
			float[] tw = new float[n];
			boolean anyText = false;
			for (int i = 0; i < n; i++) {
				String t = label(list[i]);
				anyText |= !t.isEmpty();
				tw[i] = Math.max(ic, c.textWidth(Fonts.SEMIBOLD, tf, t)) + 2 * s;
				total += tw[i] + (i > 0 ? gap : 0);
			}
			if (ui != null) {
				float tx = x + pad;
				for (int i = 0; i < n; i++) {
					Game.Slot sl = list[i];
					drawSlot(c, sl, tx + (tw[i] - ic) / 2, y + pad, ic);
					String t = label(sl);
					if (!t.isEmpty()) {
						text(c, Fonts.SEMIBOLD, tf, t, tx + (tw[i] - c.textWidth(Fonts.SEMIBOLD, tf, t)) / 2, y + pad + ic + s, textColor(sl, now));
					}
					tx += tw[i] + gap;
				}
			}
			done(total + 2 * pad, ic + (anyText ? lh + s : 0) + 2 * pad);
		}

		private void drawSlot(Canvas c, Game.Slot sl, float x, float y, float ic) {
			if (sl.empty()) {
				c.round(x + ic * 0.1f, y + ic * 0.1f, ic * 0.8f, ic * 0.8f, 3, 0x14FFFFFF);
				return;
			}
			item(c, sl.id, x, y, ic, 0xFFFFFFFF);
			if (bar.value) {
				durabilityBar(c, sl, x, y, ic);
			}
		}
	}

	// ── inventory ────────────────────────────────────────────────────────

	static final class InventoryGrid extends Box {
		final Setting.Bool hotbar = add(new Setting.Bool("hotbar", "Show the hotbar row", false));
		final Setting.Bool slots = add(new Setting.Bool("slots", "Slot backgrounds", true));
		final Setting.Bool counts = add(new Setting.Bool("counts", "Item counts", true));
		final Setting.Bool bars = add(new Setting.Bool("bars", "Durability bars", true));
		final Setting.Bool selected = add(new Setting.Bool("selected", "Highlight the selected slot", true));
		final Setting.Bool hideEmpty = add(new Setting.Bool("hide", "Hide when the inventory is empty", false));

		InventoryGrid() {
			super("inventory", "Inventory", "Your backpack on screen without opening it, with the game's item icons and counts.", Theme.I_BACKPACK, false, 1f, 0.8f);
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
		void render(Canvas c, Ui ui, Game g, float x, float y) {
			float s = scale.value, pad = pad();
			float p = 19 * s, ic = 16 * s, gap = 4 * s;
			int rows = 3 + (hotbar.value ? 1 : 0);
			float w = 9 * p + 2 * pad - 2 * s, h = rows * p + (hotbar.value ? gap : 0) + 2 * pad - 2 * s;
			if (ui != null) {
				for (int r = 0; r < rows; r++) {
					boolean hot = r == 3;
					float ry = y + pad + r * p + (hot ? gap : 0);
					for (int col = 0; col < 9; col++) {
						Game.Slot sl = g.inv[hot ? col : 9 + r * 9 + col];
						float sx = x + pad + col * p;
						if (slots.value) {
							c.round(sx, ry, p - 2 * s, p - 2 * s, 3 * s, 0x26FFFFFF);
						}
						if (hot && selected.value && col == g.selected) {
							c.outline(sx - s, ry - s, p, p, 4 * s, Math.max(1f, 1.5f * s), 0xE0FFFFFF);
						}
						if (sl.empty()) {
							continue;
						}
						float ix = sx + (p - 2 * s - ic) / 2, iy = ry + (p - 2 * s - ic) / 2;
						item(c, sl.id, ix, iy, ic, 0xFFFFFFFF);
						if (bars.value) {
							durabilityBar(c, sl, ix, iy, ic);
						}
						if (counts.value && sl.count > 1) {
							String t = String.valueOf(sl.count);
							float fs = 9.5f * s;
							text(c, Fonts.SEMIBOLD, fs, t, sx + p - 2 * s - c.textWidth(Fonts.SEMIBOLD, fs, t) - s, ry + p - 2 * s - c.lineHeight(Fonts.SEMIBOLD, fs), 0xFFFFFFFF);
						}
					}
				}
			}
			done(w, h);
		}
	}

	// ── health + food (AppleSkin style) ──────────────────────────────────

	static final class Vitals extends Box {
		final Setting.Choice display = add(new Setting.Choice("display", "Show as", 0, "Icons", "Numbers", "Icons + numbers"));
		final Setting.Bool health = add(new Setting.Bool("health", "Health", true));
		final Setting.Bool absorb = add(new Setting.Bool("absorption", "Absorption (golden hearts)", true));
		final Setting.Bool food = add(new Setting.Bool("food", "Hunger", true));
		final Setting.Bool armor = add(new Setting.Bool("armor", "Armour points", true));
		final Setting.Bool saturation = add(new Setting.Bool("saturation", "Saturation bar", true));
		final Setting.Bool preview = add(new Setting.Bool("preview", "Preview the food in your hand", true));
		final Setting.Bool low = add(new Setting.Bool("low", "Pulse hearts when low", true));
		final Setting.Num perRow = add(new Setting.Num("row", "Hearts per row", 10, 5, 20, 1, ""));

		Vitals() {
			super("health", "Health & Food", "Hearts, hunger with saturation, armour and absorption. Hover a food item to see what eating it restores.",
					Theme.I_HEART_PULSE, false, 0.5f, 0.78f);
		}

		@Override
		public int needs() {
			return Game.NEED_VITALS | Game.NEED_EFFECTS | (preview.value ? Game.NEED_ARMOR : 0);
		}

		private static boolean has(Game g, String id) {
			for (int i = 0; i < g.effectCount; i++) {
				if (id.equals(g.effects[i].id)) {
					return true;
				}
			}
			return false;
		}

		private static void pip(Canvas c, String name, float x, float y, float size, int tint, int fallback) {
			Image img = Vanilla.pip(name);
			if (img != null) {
				Vanilla.draw(c, img, x, y, size, size, tint);
			} else if (!name.endsWith("empty") && !name.endsWith("container")) {
				c.round(x + 1, y + 1, size - 2, size - 2, size * 0.3f, Theme.alpha(fallback, (tint >>> 24) / 255f));
			}
		}

		private float rowW;

		@Override
		void render(Canvas c, Ui ui, Game g, float x, float y) {
			float s = scale.value, pad = pad();
			float ps = 10.5f * s, pitch = 8.8f * s, gap = 3.5f * s, tf = 11.5f * s;
			boolean icons = display.value != 1, nums = display.value != 0, draw = ui != null;
			long now = draw ? ui.now : 0;
			float lh = c.lineHeight(Fonts.SEMIBOLD, tf);
			float cy = y + pad;
			rowW = 0;
			boolean any = false;
			if (armor.value && (g.armorValue > 0 || g.sample)) {
				cy += rowArmor(c, draw, x + pad, cy, g, ps, pitch, tf, lh, icons, nums);
				any = true;
			}
			if (health.value) {
				cy += any ? gap : 0;
				cy += rowHealth(c, draw, x + pad, cy, g, ps, pitch, gap, tf, lh, icons, nums, now);
				any = true;
			}
			if (food.value) {
				cy += any ? gap : 0;
				cy += rowFood(c, draw, x + pad, cy, g, ps, pitch, tf, lh, icons, nums, now);
			}
			done(rowW + 2 * pad, cy - y + pad);
		}

		private float rowArmor(Canvas c, boolean draw, float x, float y, Game g, float ps, float pitch, float tf, float lh, boolean icons, boolean nums) {
			int pts = g.armorValue;
			float w = 0, h = icons ? ps : lh;
			if (icons) {
				for (int i = 0; i < 10; i++) {
					if (draw) {
						pip(c, "armor_empty", x + i * pitch, y, ps, 0xFFFFFFFF, 0xFF71717A);
						if (pts >= 2 * i + 2) {
							pip(c, "armor_full", x + i * pitch, y, ps, 0xFFFFFFFF, 0xFFD4D4D8);
						} else if (pts == 2 * i + 1) {
							pip(c, "armor_half", x + i * pitch, y, ps, 0xFFFFFFFF, 0xFFD4D4D8);
						}
					}
				}
				w = pitch * 9 + ps;
			}
			if (nums) {
				String t = labels.value ? "Armor " + pts : String.valueOf(pts);
				if (draw) {
					text(c, Fonts.SEMIBOLD, tf, t, x + (icons ? w + 5 * scale.value : 0), y + (h - lh) / 2, color.value);
				}
				w += (icons ? 5 * scale.value : 0) + c.textWidth(Fonts.SEMIBOLD, tf, t);
			}
			rowW = Math.max(rowW, w);
			return nums ? Math.max(h, lh) : h;
		}

		private float rowHealth(Canvas c, boolean draw, float x, float y, Game g, float ps, float pitch, float gap, float tf, float lh, boolean icons, boolean nums, long now) {
			float w = 0, h = 0;
			int per = (int) perRow.value;
			int hearts = Math.max(1, Math.min(60, (int) Math.ceil(g.maxHealth / 2f)));
			int gold = absorb.value ? (int) Math.ceil(g.absorption / 2f) : 0;
			String kind = has(g, "wither") ? "wither" : has(g, "poison") ? "poison" : null;
			int alpha = low.value && g.health <= 4 && !g.sample ? pulse(now, 120, 0.55f) : 255;
			int tint = alpha << 24 | 0xFFFFFF;
			if (icons) {
				int rows = (hearts + per - 1) / per, gRows = (gold + per - 1) / per;
				float rowStep = ps * 0.95f;
				float oy = 0;
				// golden absorption hearts first (above), then health, row 0 at the bottom like the game
				for (int r = gRows - 1; r >= 0; r--) {
					for (int i = r * per; i < Math.min(gold, (r + 1) * per); i++) {
						if (draw) {
							float hx = x + (i % per) * pitch;
							pip(c, "heart_container", hx, y + oy, ps, 0xFFFFFFFF, 0xFF3F3F46);
							pip(c, g.absorption >= 2 * i + 2 ? "heart_absorb_full" : "heart_absorb_half", hx, y + oy, ps, 0xFFFFFFFF, GOLD);
						}
					}
					oy += rowStep;
				}
				for (int r = rows - 1; r >= 0; r--) {
					for (int i = r * per; i < Math.min(hearts, (r + 1) * per); i++) {
						if (draw) {
							float hx = x + (i % per) * pitch;
							pip(c, "heart_container", hx, y + oy, ps, 0xFFFFFFFF, 0xFF3F3F46);
							if (g.health >= 2 * i + 2) {
								pip(c, kind == null ? "heart_full" : "heart_" + kind + "_full", hx, y + oy, ps, tint, 0xFFEF4444);
							} else if (g.health > 2 * i) {
								pip(c, kind == null ? "heart_half" : "heart_" + kind + "_half", hx, y + oy, ps, tint, 0xFFEF4444);
							}
						}
					}
					oy += rowStep;
				}
				w = pitch * (Math.min(Math.max(hearts, gold), per) - 1) + ps;
				h = oy - rowStep + ps;
			}
			if (nums) {
				String t = (labels.value ? "Health " : "") + Status.hp(g.health) + "/" + Status.hp(g.maxHealth);
				if (absorb.value && g.absorption > 0) {
					t += " +" + Status.hp(g.absorption);
				}
				float tx = x + (icons ? w + 5 * scale.value : 0);
				float ty = y + (icons ? (h - lh) / 2 : 0);
				if (draw) {
					text(c, Fonts.SEMIBOLD, tf, t, tx, ty, color.value);
				}
				w += (icons ? 5 * scale.value : 0) + c.textWidth(Fonts.SEMIBOLD, tf, t);
				h = Math.max(h, lh);
			}
			rowW = Math.max(rowW, w);
			return h;
		}

		private float rowFood(Canvas c, boolean draw, float x, float y, Game g, float ps, float pitch, float tf, float lh, boolean icons, boolean nums, long now) {
			float w = 0, h = icons ? ps : lh;
			String hand = g.sample ? "golden_apple" : g.hand.id;
			int restore = preview.value ? Status.foodHunger(hand) : 0;
			int after = Math.min(20, g.food + restore);
			boolean hunger = has(g, "hunger");
			if (icons) {
				for (int i = 0; i < 10; i++) {
					int cur = g.food >= 2 * i + 2 ? 2 : g.food == 2 * i + 1 ? 1 : 0;
					int nxt = after >= 2 * i + 2 ? 2 : after == 2 * i + 1 ? 1 : 0;
					if (draw) {
						float fx = x + i * pitch;
						pip(c, "food_empty", fx, y, ps, 0xFFFFFFFF, 0xFF3F3F46);
						if (cur > 0) {
							pip(c, (hunger ? "food_hunger_" : "food_") + (cur == 2 ? "full" : "half"), fx, y, ps, 0xFFFFFFFF, 0xFFD97706);
						}
						if (nxt > cur) {
							pip(c, (nxt == 2 ? "food_full" : "food_half"), fx, y, ps, pulse(now, 170, 0.25f) / 2 << 24 | 0xFFFFFF, 0xFFD97706);
						}
					}
				}
				w = pitch * 9 + ps;
				if (saturation.value) {
					float bh = Math.max(1.5f, 2 * scale.value), by = y + ps + 1.5f * scale.value;
					if (draw) {
						c.round(x, by, w, bh, bh / 2, 0x26FFFFFF);
						c.round(x, by, Math.max(bh, w * Math.min(1f, g.saturation / 20f)), bh, bh / 2, GOLD);
					}
					h += 1.5f * scale.value + bh;
				}
			}
			if (nums) {
				String t = (labels.value ? "Food " : "") + g.food + (restore > 0 ? " +" + (after - g.food) : "");
				if (saturation.value) {
					t += "  Sat " + Status.hp(g.saturation);
				}
				if (draw) {
					text(c, Fonts.SEMIBOLD, tf, t, x + (icons ? w + 5 * scale.value : 0), y + ((icons ? ps : lh) - lh) / 2, color.value);
				}
				w += (icons ? 5 * scale.value : 0) + c.textWidth(Fonts.SEMIBOLD, tf, t);
				h = Math.max(h, lh);
			}
			rowW = Math.max(rowW, w);
			return h;
		}
	}

	// ── target health ────────────────────────────────────────────────────

	static final class Target extends Box {
		final Setting.Choice display = add(new Setting.Choice("display", "Show as", 0, "Hearts", "Bar"));
		final Setting.Bool name = add(new Setting.Bool("name", "Show the name", true));
		final Setting.Bool numbers = add(new Setting.Bool("numbers", "Show health numbers", true));
		final Setting.Bool colour = add(new Setting.Bool("colour", "Colour the bar by health", true));
		final Setting.Num perRow = add(new Setting.Num("row", "Hearts per row", 10, 5, 20, 1, ""));
		final Setting.Num hold = add(new Setting.Num("hold", "Keep showing after looking away", 2, 0, 10, 0.5f, "s"));
		private String seenName = "";
		private float seenHealth, seenMax = 20;
		private long seenAt = -1_000_000;

		Target() {
			super("target", "Health Indicator", "Shows the name and health of the mob or player you are looking at.", Theme.I_HEART, false, 0.5f, 0.2f);
			drop(labels);
		}

		@Override
		public int needs() {
			return Game.NEED_TARGET;
		}

		@Override
		public void frame(Game g) {
			if (g.hasTarget) {
				seenName = g.targetName;
				seenHealth = g.targetHealth;
				seenMax = g.targetMax;
				seenAt = System.currentTimeMillis();
			}
		}

		@Override
		public boolean visible(Game g) {
			return g.sample || g.hasTarget || System.currentTimeMillis() - seenAt <= hold.value * 1000;
		}

		@Override
		void render(Canvas c, Ui ui, Game g, float x, float y) {
			float s = scale.value, pad = pad();
			String nm = g.sample ? g.targetName : seenName;
			float hp = g.sample ? g.targetHealth : seenHealth, mx = Math.max(1f, g.sample ? g.targetMax : seenMax);
			boolean bar = display.value == 1 || mx > 40f;
			float nf = 12f * s, lh = c.lineHeight(Fonts.SEMIBOLD, nf);
			String num = Status.hp(hp) + " / " + Status.hp(mx);
			boolean top = name.value || numbers.value;
			float nameW = name.value ? c.textWidth(Fonts.SEMIBOLD, nf, nm) : 0;
			float numW = numbers.value ? c.textWidth(Fonts.MEDIUM, 11 * s, num) : 0;
			float ps = 10.5f * s, pitch = 8.8f * s, per = Math.min(perRow.value, Math.max(1, (float) Math.ceil(mx / 2)));
			int hearts = (int) Math.ceil(mx / 2);
			float dispW = bar ? Math.max(110 * s, nameW + numW + 14 * s) : pitch * (per - 1) + ps;
			float w = Math.max(dispW, nameW + (name.value && numbers.value ? 14 * s : 0) + numW);
			int rows = bar ? 1 : (int) Math.ceil(hearts / per);
			float dispH = bar ? 6 * s : ps + (rows - 1) * ps * 0.95f;
			float h = (top ? lh + 3 * s : 0) + dispH;
			if (ui != null) {
				float ty = y + pad;
				if (top) {
					if (name.value) {
						text(c, Fonts.SEMIBOLD, nf, nm, x + pad, ty, color.value);
					}
					if (numbers.value) {
						text(c, Fonts.MEDIUM, 11 * s, num, x + pad + w - numW, ty + (lh - c.lineHeight(Fonts.MEDIUM, 11 * s)) / 2, Theme.alpha(color.value, 0.75f));
					}
					ty += lh + 3 * s;
				}
				if (bar) {
					float f = Math.max(0f, Math.min(1f, hp / mx));
					int col = colour.value ? Status.wear(f) : 0xFFEF4444;
					c.round(x + pad, ty, w, 6 * s, 3 * s, 0x40FFFFFF);
					c.round(x + pad, ty, Math.max(6 * s * (f > 0 ? 1 : 0), w * f), 6 * s, 3 * s, col);
				} else {
					int pr = (int) per;
					for (int r = rows - 1; r >= 0; r--) {
						float oy = (rows - 1 - r) * ps * 0.95f;
						for (int i = r * pr; i < Math.min(hearts, (r + 1) * pr); i++) {
							float hx = x + pad + (i % pr) * pitch;
							Vitals.pip(c, "heart_container", hx, ty + oy, ps, 0xFFFFFFFF, 0xFF3F3F46);
							if (hp >= 2 * i + 2) {
								Vitals.pip(c, "heart_full", hx, ty + oy, ps, 0xFFFFFFFF, 0xFFEF4444);
							} else if (hp > 2 * i) {
								Vitals.pip(c, "heart_half", hx, ty + oy, ps, 0xFFFFFFFF, 0xFFEF4444);
							}
						}
					}
				}
			}
			done(w + 2 * pad, h + 2 * pad);
		}
	}
}
