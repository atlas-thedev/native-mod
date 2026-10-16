package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.Input;
import xyz.nativelaunch.ui.McBridge;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;

import java.util.Locale;

/**
 * Shulker Tooltip: hover a shulker box in any inventory or chest and its 27 slots pop up next to the cursor, tinted to the
 * box's colour. Compact mode merges the same items into one slot with the total (76, 1.7K ...). Optionally the box you
 * hold shows as a HUD card too.
 */
public final class ShulkerTooltip extends StatusModules.Box {
	private static final int SLOTS = 27, COLS = 9;
	private static ShulkerTooltip instance;

	final Setting.Choice trigger = add(new Setting.Choice("trigger", "Show on hover", 0, "Always", "Hold Shift"));
	final Setting.Bool hideVanilla = add(new Setting.Bool("hide-vanilla", "Hide the game's own tooltip", true));
	final Setting.Key lock = add(new Setting.Key("lock", "Pin panel key", 342)); // left alt
	final Setting.Bool compact = add(new Setting.Bool("compact", "Compact (merge same items)", false));
	final Setting.Bool counts = add(new Setting.Bool("counts", "Item counts", true));
	final Setting.Bool bars = add(new Setting.Bool("bars", "Durability bars", true));
	final Setting.Bool tinted = add(new Setting.Bool("tint", "Tint to the box colour", true));
	final Setting.Bool header = add(new Setting.Bool("header", "Box name and total", true));
	final Setting.Num size = add(new Setting.Num("size", "Tooltip size", 100, 60, 160, 5, "%"));
	final Setting.Bool hand = add(new Setting.Bool("hand", "Also show the box in your hand", true));

	private final View tip = new View(), held = new View(), demo = new View();
	private final Game.Slot[] merged = slots();
	private final float[] dims = new float[2];
	private Object lastStack;
	private long nextTip, nextHand, tipSince;
	private boolean showTip, pinned, lockWas;
	/** Where the panel was last drawn (canvas units): a pinned panel stays there. */
	private float tipX, tipY;

	/** One shulker box's contents. */
	private static final class View {
		final Game.Slot[] raw = slots();
		String id, name;
		int stacks, total;

		boolean ok() {
			return id != null;
		}

		void fill(String id) {
			this.id = id;
			name = null;
			stacks = total = 0;
			for (Game.Slot s : raw) {
				if (id != null && !s.empty()) {
					stacks++;
					total += s.count;
				}
			}
		}
	}

	ShulkerTooltip() {
		super("shulker-preview", "Shulker Tooltip", "Hover a shulker box in any inventory or chest to see what is inside it, tinted to the box colour.",
				Theme.I_BACKPACK, false, 1f, 0.6f);
		drop(labels, color, style);
		style.initial(STYLE_TEXT);
		instance = this;
		String[] ids = {"diamond_sword", "golden_apple", "ender_pearl", "totem_of_undying", "end_crystal", "obsidian", "experience_bottle",
				"firework_rocket", "cooked_beef", "obsidian", "shield", "ender_pearl", "diamond", "obsidian", "golden_apple"};
		int[] cnt = {1, 12, 16, 3, 64, 64, 32, 64, 24, 64, 1, 16, 9, 64, 8};
		for (int i = 0; i < ids.length; i++) {
			demo.raw[(i * 7) % SLOTS].set(ids[i], cnt[i], i == 0 ? 640 : 0, i == 0 ? 1561 : 0);
		}
		demo.fill("purple_shulker_box");
	}

	private static Game.Slot[] slots() {
		Game.Slot[] s = new Game.Slot[SLOTS];
		for (int i = 0; i < SLOTS; i++) {
			s[i] = new Game.Slot();
		}
		return s;
	}

	@Override
	public int needs() {
		return Game.NEED_ARMOR;
	}

	@Override
	public void frame(Game g) {
		long now = System.currentTimeMillis();
		McBridge mc = HudModules.mc();
		boolean lockDown = lock.value >= 0 && Keys.isDown(g.window, lock.value);
		if (lockDown && !lockWas) {
			pinned = !pinned && showTip;
		}
		lockWas = lockDown;
		if (pinned && (!g.inWorld || mc.screen() == null)) {
			pinned = false;
		}
		Object st = g.inWorld ? mc.hoveredStack() : null;
		if (pinned) {
			lastStack = null;
		} else if (st != lastStack || now >= nextTip) {
			lastStack = st;
			nextTip = now + 150;
			tip.fill(st == null ? null : mc.shulkerContents(st, tip.raw));
			if (tip.ok()) {
				tip.name = mc.stackName(st);
			}
		}
		boolean want = pinned || tip.ok() && (trigger.value == 0 || Keys.isDown(g.window, 340) || Keys.isDown(g.window, 344));
		if (want && !showTip) {
			tipSince = now;
		}
		showTip = want;
		if (now >= nextHand) {
			nextHand = now + 100;
			held.fill(hand.value && g.inWorld ? mc.shulkerInHand(held.raw) : null);
		}
	}

	@Override
	protected void onDisable() {
		showTip = pinned = false;
		lastStack = null;
	}

	/** True while a hovered shulker box should show its contents (keeps the Native layer running over the screen). */
	public static boolean active() {
		return instance != null && instance.enabled && instance.showTip;
	}

	/** True while the game's own tooltip for the hovered box should be skipped (see ShulkerTooltipMixin). */
	public static boolean hidesVanilla() {
		return active() && instance.hideVanilla.value && !instance.pinned;
	}

	/**
	 * Draws the hovered box's contents next to the cursor: where the game's tooltip would be when that one is hidden,
	 * else above it (or left of the cursor when there is no room).
	 */
	public static void drawTip(Ui ui) {
		ShulkerTooltip m = instance;
		Canvas c = ui.c;
		float s = m.size.value / 100f;
		m.box(c, m.tip, s, 0, 0, false);
		float w = m.dims[0], h = m.dims[1], gap = 12;
		if (m.pinned) {
			m.box(c, m.tip, s, m.tipX, m.tipY, true);
			m.pinChip(c, s, m.tipX, m.tipY + h);
			return;
		}
		float mx = (float) (Input.mouseX / c.scale), my = (float) (Input.mouseY / c.scale);
		float x = mx + gap, y = my - gap - 4 - h;
		if (m.hideVanilla.value) {
			y = my - gap;
			if (x + w > c.width() - 4) {
				x = mx - gap - w;
			}
		} else if (y < 4) {
			x = mx - gap - w;
			y = my - h / 2;
		}
		x = Math.max(4, Math.min(x, c.width() - w - 4));
		y = Math.max(4, Math.min(y, c.height() - h - 4));
		m.tipX = x;
		m.tipY = y;
		float a = Math.min(1f, (System.currentTimeMillis() - m.tipSince) / 120f);
		c.pushAlpha(a);
		m.box(c, m.tip, s, x, y + (1 - a) * 6, true);
		c.popAlpha();
	}

	/** "Pinned · LAlt to unpin" under a pinned panel. */
	private void pinChip(Canvas c, float s, float x, float y) {
		String t = "Pinned  \u00b7  " + Keys.name(lock.value) + " to unpin";
		float fs = 9f * s, th = c.lineHeight(Fonts.MEDIUM, fs), cw = c.textWidth(Fonts.MEDIUM, fs, t) + 14 * s, ch = th + 6 * s;
		int col = tinted.value ? boxColor(tip.id) : 0xFF8B8B95;
		c.round(x, y + 4 * s, cw, ch, ch / 2, Theme.alpha(Theme.mix(0xFF0E0E12, col, 0.16f), 0.95f));
		c.outline(x, y + 4 * s, cw, ch, ch / 2, 1, Theme.alpha(col, 0.6f));
		c.circle(x + 7 * s, y + 4 * s + ch / 2, 2 * s, col);
		c.text(Fonts.MEDIUM, fs, t, x + 12 * s, y + 4 * s + 3 * s, Theme.TEXT_SECONDARY);
	}

	@Override
	public boolean visible(Game g) {
		return hand.value && (g.sample || held.ok());
	}

	@Override
	void render(Canvas c, Ui ui, Game g, float x, float y) {
		box(c, g.sample ? demo : held, scale.value, x, y, ui != null);
		done(dims[0], dims[1]);
	}

	@Override
	public boolean preview(Ui ui, Game g, float x, float y, float w, float h) {
		Canvas c = ui.c;
		c.gradientV(x, y, w, h, 0xFF2B2B33, 0xFF18181D);
		float s = Math.max(0.5f, Math.min(1.2f, (w - 32) / 220f));
		box(c, demo, s, 0, 0, false);
		box(c, demo, s, x + (w - dims[0]) / 2, y + (h - dims[1]) / 2, true);
		return true;
	}

	// ── the panel ───────────────────────────────────────────────────────────

	/** Lays out (and with {@code draw} paints) the panel for a box at x, y; its size lands in {@link #dims}. */
	private void box(Canvas c, View v, float s, float x, float y, boolean draw) {
		Game.Slot[] src = v.raw;
		int n = SLOTS;
		if (compact.value) {
			n = merge(v.raw);
			src = merged;
		}
		int cols = compact.value ? Math.max(1, Math.min(COLS, n)) : COLS;
		int rows = compact.value ? Math.max(1, (n + COLS - 1) / COLS) : SLOTS / COLS;
		float pad = 6 * s, cell = 20 * s, gap = 2 * s, step = cell + gap, ic = 16 * s;
		float tfs = 10.5f * s, sfs = 9.5f * s;
		String title = v.name != null && !v.name.isEmpty() ? v.name : title(v.id), summary = v.stacks == 0 ? "Empty" : v.stacks + "/" + SLOTS + "  \u00b7  " + amount(v.total);
		title = c.ellipsize(Fonts.SEMIBOLD, tfs, title, 200 * s);
		float headH = header.value ? c.lineHeight(Fonts.SEMIBOLD, tfs) + 6 * s : 0;
		float headW = header.value ? 12 * s + c.textWidth(Fonts.SEMIBOLD, tfs, title) + 14 * s + c.textWidth(Fonts.MEDIUM, sfs, summary) : 0;
		boolean empty = compact.value && n == 0;
		float gridW = empty ? 90 * s : cols * step - gap, gridH = rows * step - gap;
		float w = Math.max(gridW, headW) + 2 * pad, h = headH + gridH + 2 * pad;
		dims[0] = w;
		dims[1] = h;
		if (!draw) {
			return;
		}
		int col = tinted.value ? boxColor(v.id) : 0xFF8B8B95;
		float r = 6 * s;
		c.shadow(x, y, w, h, r, 14 * s, 0x90000000);
		c.round(x, y, w, h, r, Theme.alpha(Theme.mix(0xFF0E0E12, col, 0.16f), 0.95f));
		c.round(x + r, y, w - 2 * r, 2 * s, s, Theme.alpha(col, 0.9f));
		c.outline(x, y, w, h, r, 1.25f * s, Theme.alpha(col, 0.75f));
		if (header.value) {
			float hy = y + pad, lh = c.lineHeight(Fonts.SEMIBOLD, tfs);
			c.round(x + pad, hy + (lh - 7 * s) / 2, 7 * s, 7 * s, 2 * s, col);
			c.text(Fonts.SEMIBOLD, tfs, title, x + pad + 12 * s, hy, 0xFFF4F4F5);
			float sw = c.textWidth(Fonts.MEDIUM, sfs, summary);
			c.text(Fonts.MEDIUM, sfs, summary, x + w - pad - sw, hy + (lh - c.lineHeight(Fonts.MEDIUM, sfs)) / 2, Theme.TEXT_SECONDARY);
		}
		float gx = x + pad, gy = y + pad + headH;
		if (empty) {
			c.text(Fonts.MEDIUM, sfs, "Nothing inside", gx, gy + (cell - c.lineHeight(Fonts.MEDIUM, sfs)) / 2, Theme.TEXT_SECONDARY);
			return;
		}
		int cellBg = Theme.alpha(Theme.mix(0xFF000000, col, 0.14f), 0.55f), cellLine = Theme.alpha(col, 0.16f);
		float fs = 9f * s, flh = c.lineHeight(Fonts.BOLD, fs);
		for (int i = 0; i < n; i++) {
			float sx = gx + (i % cols) * step, sy = gy + (i / cols) * step;
			c.round(sx, sy, cell, cell, 3 * s, cellBg);
			c.outline(sx, sy, cell, cell, 3 * s, 1, cellLine);
			Game.Slot sl = src[i];
			if (sl.empty()) {
				continue;
			}
			float ix = sx + (cell - ic) / 2f, iy = sy + (cell - ic) / 2f;
			StatusModules.item(c, sl, ix, iy, ic, 0xFFFFFFFF);
			if (bars.value) {
				StatusModules.durabilityBar(c, sl, ix, iy, ic);
			}
			if (counts.value && sl.count > 1) {
				String t = amount(sl.count);
				float tx = sx + cell - c.textWidth(Fonts.BOLD, fs, t) - 1f * s, ty = sy + cell - flh + 1.5f * s;
				c.text(Fonts.BOLD, fs, t, tx + 1f * s, ty + 1f * s, 0xE0000000);
				c.text(Fonts.BOLD, fs, t, tx, ty, 0xFFFFFFFF);
			}
		}
	}

	/** Compact mode: one slot per kind of item (worn tools stay apart) with the total, most first. Returns how many. */
	private int merge(Game.Slot[] raw) {
		int n = 0;
		for (Game.Slot s : raw) {
			if (s.empty()) {
				continue;
			}
			int j = 0;
			while (j < n && !(s.max <= 0 && same(merged[j], s))) {
				j++;
			}
			if (j < n) {
				merged[j].count += s.count;
				continue;
			}
			Game.Slot m = merged[n++];
			m.set(s.id, s.count, s.damage, s.max);
			m.custom(s.model, s.cmd);
			m.setTint(s.tint);
		}
		for (int i = 1; i < n; i++) {
			for (int k = i; k > 0 && merged[k].count > merged[k - 1].count; k--) {
				Game.Slot t = merged[k];
				merged[k] = merged[k - 1];
				merged[k - 1] = t;
			}
		}
		return n;
	}

	private static boolean same(Game.Slot a, Game.Slot b) {
		return a.max <= 0 && a.id.equals(b.id) && a.tint == b.tint && Float.compare(a.cmd, b.cmd) == 0
				&& (a.model == null ? b.model == null : a.model.equals(b.model));
	}

	/** 76, 999, 1.7K, 12K, 1.2M: short enough for a slot corner. */
	static String amount(int n) {
		if (n < 1000) {
			return String.valueOf(n);
		}
		if (n < 1_000_000) {
			return n < 10_000 ? tenths(n / 100) + "K" : n / 1000 + "K";
		}
		return tenths(n / 100_000) + "M";
	}

	private static String tenths(int t) {
		return t % 10 == 0 ? String.valueOf(t / 10) : t / 10 + "." + t % 10;
	}

	/** "purple_shulker_box" -> "Purple Shulker Box". */
	private static String title(String id) {
		if (id == null) {
			return "Shulker Box";
		}
		String bare = id.indexOf(':') >= 0 ? id.substring(id.indexOf(':') + 1) : id;
		StringBuilder b = new StringBuilder();
		for (String part : bare.split("_")) {
			if (!part.isEmpty()) {
				b.append(b.length() == 0 ? "" : " ").append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
			}
		}
		return b.toString();
	}

	/** The colour of a shulker box's shell (the plain one is the classic lilac). */
	static int boxColor(String id) {
		String c = id == null ? "" : id.replace("minecraft:", "").replace("shulker_box", "");
		switch (c) {
			case "white_": return 0xFFE4E8E8;
			case "orange_": return 0xFFF07613;
			case "magenta_": return 0xFFBD44B3;
			case "light_blue_": return 0xFF3AAFD9;
			case "yellow_": return 0xFFF8C527;
			case "lime_": return 0xFF70B919;
			case "pink_": return 0xFFED8DAC;
			case "gray_": return 0xFF5A6064;
			case "light_gray_": return 0xFF9D9D97;
			case "cyan_": return 0xFF158991;
			case "purple_": return 0xFF8932B8;
			case "blue_": return 0xFF3C44AA;
			case "brown_": return 0xFF8A5A33;
			case "green_": return 0xFF5E7C16;
			case "red_": return 0xFFB02E26;
			case "black_": return 0xFF3A3A40;
			default: return 0xFF976997;
		}
	}
}
