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

	// ── armour status (Armor Stats) ──────────────────────────────────────

	static final class Armor extends Box {
		private static final String[] EMPTY_ITEMS = {
			"leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots"
		};

		final Setting.Choice layout = add(new Setting.Choice("layout", "Orientation", 0, "Vertical", "Horizontal").shorts("Vertical", "Horizontal"));
		final Setting.Bool durability = fixed(new Setting.Bool("durability", "Show durability", true));
		final Setting.Bool empty = fixed(new Setting.Bool("empty", "Show empty slots", false));

		Armor() {
			super("armor", "Armor Stats", "Displays your equipped armor pieces and their durability bars.", Theme.I_SHIELD, false, 0f, 0.7f);
			drop(labels);
			style.initial(STYLE_CARD);
		}

		@Override
		public int needs() {
			return Game.NEED_ARMOR;
		}

		@Override
		public boolean visible(Game g) {
			if (g.sample) {
				return true;
			}
			for (int i = 0; i < 4; i++) {
				if (!g.armor[i].empty()) {
					return true;
				}
			}
			return empty.value;
		}

		@Override
		void render(Canvas c, Ui ui, Game g, float x, float y) {
			float s = scale.value;
			float pad = pad();
			float itemSize = 16 * s;
			float itemSpacing = 4 * s;
			float durabilityBarHeight = 3 * s; // 2px bar + 1px gap
			float slotHeight = durability.value ? itemSize + durabilityBarHeight : itemSize;

			int visibleCount = 0;
			for (int i = 0; i < 4; i++) {
				if (empty.value || !g.armor[i].empty()) {
					visibleCount++;
				}
			}

			if (visibleCount == 0) {
				done(1, 1);
				return;
			}

			boolean isVertical = layout.value == 0;
			float width, height;
			if (isVertical) {
				width = itemSize + pad * 2;
				height = visibleCount * slotHeight + (visibleCount - 1) * itemSpacing + pad * 2;
			} else {
				width = visibleCount * itemSize + (visibleCount - 1) * itemSpacing + pad * 2;
				height = slotHeight + pad * 2;
			}

			if (ui != null) {
				float ox = 0;
				float oy = 0;
				for (int i = 0; i < 4; i++) {
					Game.Slot sl = g.armor[i];
					boolean hasItem = !sl.empty();
					if (!hasItem && !empty.value) {
						continue;
					}

					float ix = isVertical ? x + pad : x + pad + ox;
					float iy = isVertical ? y + pad + oy : y + pad;

					if (hasItem) {
						item(c, sl, ix, iy, itemSize, 0xFFFFFFFF);
						if (durability.value && sl.max > 0 && sl.damage > 0) {
							float pct = sl.durability();
							float barX = ix + 2 * s;
							float barY = iy + itemSize + 1 * s;
							float barWidth = 13 * s;
							float barHeight = Math.max(1f, 2 * s);
							float filledWidth = Math.max(1f, Math.round(barWidth * pct));

							c.fill(barX, barY, barWidth, barHeight, 0xFF000000);
							c.fill(barX, barY, filledWidth, barHeight, Status.wear(pct));
						}
					} else {
						// Empty slot placeholder (leather armor icons like Fast Client)
						Image placeholder = Vanilla.item(EMPTY_ITEMS[i]);
						if (placeholder != null) {
							Vanilla.draw(c, placeholder, ix, iy, itemSize, itemSize, 0x88FFFFFF);
						} else {
							c.round(ix + itemSize * 0.1f, iy + itemSize * 0.1f, itemSize * 0.8f, itemSize * 0.8f, 3 * s, 0x14FFFFFF);
						}
					}

					if (isVertical) {
						oy += slotHeight + itemSpacing;
					} else {
						ox += itemSize + itemSpacing;
					}
				}
			}

			done(width, height);
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
			style.initial(STYLE_CARD);
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
			return style.value == STYLE_CARD ? 6 * s : style.value == STYLE_BRACKETS ? 4 * s : 2 * s;
		}

		@Override
		void render(Canvas c, Ui ui, Game g, float x, float y) {
			float s = scale.value, pad = pad();
			float cell = 20 * s, gap = 2 * s, step = cell + gap, ic = 16 * s, sep = 6 * s;
			int lines = 3 + (hotbar.value ? 1 : 0);
			boolean vertical = layout.value == 1;
			float along = 9 * step - gap;
			float across = lines * step - gap + (hotbar.value ? sep - gap : 0);
			float w = (vertical ? across : along) + 2 * pad, h = (vertical ? along : across) + 2 * pad;
			if (ui != null) {
				float cr = 4 * s, fs = 10f * s;
				float flh = c.lineHeight(Fonts.BOLD, fs);
				if (hotbar.value) {
					if (!vertical) {
						float divY = y + pad + 3 * step - gap + sep / 2f;
						c.fill(x + pad + 2 * s, divY, along - 4 * s, 1, 0x1AFFFFFF);
					} else {
						float divX = x + pad + 3 * step - gap + sep / 2f;
						c.fill(divX, y + pad + 2 * s, 1, along - 4 * s, 0x1AFFFFFF);
					}
				}
				for (int r = 0; r < lines; r++) {
					boolean hot = r == 3;
					float off = r * step + (hot ? sep - gap : 0);
					for (int col = 0; col < 9; col++) {
						Game.Slot sl = g.inv[hot ? col : 9 + r * 9 + col];
						float sx = x + pad + (vertical ? off : col * step), sy = y + pad + (vertical ? col * step : off);
						if (slots.value) {
							c.round(sx, sy, cell, cell, cr, 0x40000000);
							c.outline(sx, sy, cell, cell, cr, 1, 0x14FFFFFF);
							c.fill(sx + cr, sy + 1, cell - 2 * cr, Math.max(1f, s * 0.75f), 0x0AFFFFFF);
						}
						if (hot && selected.value && col == g.selected) {
							c.round(sx, sy, cell, cell, cr, 0x1AFFFFFF);
							c.outline(sx - 0.5f * s, sy - 0.5f * s, cell + s, cell + s, cr + 0.5f * s, Math.max(1f, 1.5f * s), 0xCCFFFFFF);
						}
						if (sl.empty()) {
							continue;
						}
						float ix = sx + (cell - ic) / 2f, iy = sy + (cell - ic) / 2f;
						item(c, sl, ix, iy, ic, 0xFFFFFFFF);
						if (bars.value && sl.max > 0 && sl.damage > 0) {
							float pct = sl.durability();
							float barX = sx + 2.5f * s;
							float barY = sy + cell - 3 * s;
							float barW = cell - 5 * s;
							float barH = Math.max(1.5f, 2 * s);
							c.fill(barX, barY, barW, barH, 0xCC000000);
							c.fill(barX, barY, Math.max(1f, Math.round(barW * pct)), barH, Status.wear(pct));
						}
						if (counts.value && sl.count > 1) {
							String t = String.valueOf(sl.count);
							float tw = c.textWidth(Fonts.BOLD, fs, t);
							float tx = sx + cell - tw - 1.5f * s;
							float ty = sy + cell - flh + 1f * s;
							c.text(Fonts.BOLD, fs, t, tx + 1f, ty + 1f, 0xDD000000);
							c.text(Fonts.BOLD, fs, t, tx, ty, 0xFFFFFFFF);
						}
					}
				}
			}
			done(w, h);
		}
	}
}
