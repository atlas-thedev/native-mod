package xyz.nativelaunch.ui.view;

import xyz.nativelaunch.ui.McBridge;
import xyz.nativelaunch.ui.Scroll;
import xyz.nativelaunch.ui.TextField;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.UiConfig;
import xyz.nativelaunch.ui.UiRuntime;
import xyz.nativelaunch.ui.Widgets;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.ui.gfx.Image;
import xyz.nativelaunch.ui.mod.Game;
import xyz.nativelaunch.ui.mod.HudModule;
import xyz.nativelaunch.ui.mod.Module;
import xyz.nativelaunch.ui.mod.Modules;
import xyz.nativelaunch.ui.mod.Setting;
import xyz.nativelaunch.ui.mod.Wardrobe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Native menu (Right Shift): a floating icon rail on the left, a small toolbar and one big panel with the
 * client mods, the cosmetics locker and the UI settings. Same glass look as the launcher.
 */
public final class MenuView {
	private static final String[] TABS = {"Mods", "Cosmetics", "Settings"};
	private static final int[] TAB_ICONS = {Theme.I_PUZZLE, Theme.I_SHIRT, Theme.I_SETTINGS};
	private static final String[] CHIPS = {"All", "Favorites", Module.HUD, Module.MECHANIC, Module.VISUAL, Module.PERFORMANCE};
	private static final int[] SLOT_ICONS = {Theme.I_LAYERS, Theme.I_HAT, Theme.I_GLASSES, Theme.I_BACKPACK, Theme.I_FOOTPRINTS, Theme.I_HAND};
	private static final int PANEL_BG = 0xF205060A;
	private static final int CARD = 0xB808090C, CARD_HOVER = 0xD80E1014;

	private int tab;
	private int chip;
	private int slot;
	private boolean listView;
	private Module selected;
	/** Locker selection in the current slot: null = nothing picked yet, "" = the "Nothing" card, else an item id. */
	private String pick;
	private final TextField search = new TextField("menu:search");
	private final Scroll grid = new Scroll(), detail = new Scroll(), locker = new Scroll(), prefs = new Scroll();
	private Object lastScreen;
	private long shownAt, pageAt;
	private Game sample = Game.sample();
	private long sampleAt;
	private final PlayerPreview preview = new PlayerPreview();
	private final Image logo = Image.resource("/assets/native/ui/logo.png");
	private final Setting.Bool sTitle = new Setting.Bool("title", "Native title screen", true);
	private final Setting.Bool sNotify = new Setting.Bool("notify", "Message notifications in game", true);
	private final Setting.Num sScale = new Setting.Num("scale", "UI size", 1f, 0.75f, 1.5f, 0.05f, "x");
	private final Setting.Key sMenu = new Setting.Key("menukey", "Menu key", 344);
	private final Setting.Key sChat = new Setting.Key("chatkey", "Chat key", 89);
	private boolean confirmReset;

	public MenuView() {
		search.maxLength = 40;
	}

	/** Esc: closes the innermost thing first. True when it was consumed (the menu stays open). */
	public boolean escape(Ui ui) {
		if (Widgets.escape()) {
			return true;
		}
		if (ui != null && search.focused(ui)) {
			ui.focus = null;
			if (search.value().length() > 0) {
				search.clear();
			}
			return true;
		}
		if (confirmReset) {
			confirmReset = false;
			return true;
		}
		if (selected != null) {
			selected = null;
			pageAt = System.currentTimeMillis();
			return true;
		}
		return false;
	}

	public void openTab(int t) {
		setTab(t);
	}

	private void setTab(int t) {
		if (tab == t) {
			return;
		}
		tab = t;
		Widgets.listening = null;
		confirmReset = false;
		pageAt = System.currentTimeMillis();
		if (t == 1) {
			Wardrobe.refresh(false);
		}
	}

	public void draw(Ui ui, Object screen, boolean inWorld) {
		Canvas c = ui.c;
		if (screen != lastScreen) {
			lastScreen = screen;
			shownAt = pageAt = ui.now;
			Widgets.listening = null;
			confirmReset = false;
			if (tab == 1) {
				Wardrobe.refresh(false);
			}
		}
		if (ui.now - sampleAt > 120) {
			sample = Game.sample();
			sampleAt = ui.now;
		}
		float W = c.width(), H = c.height();
		float e = ease(clamp01((ui.now - shownAt) / 260f));
		if (inWorld) {
			c.fill(0, 0, W, H, Theme.alpha(0xA0000000, e));
		}
		float ww = Math.min(1060, W - 32), wh = Math.min(640, H - 32);
		float x0 = (W - ww) / 2, y0 = (H - wh) / 2 + (1 - e) * 18;
		c.pushAlpha(e);

		// ── icon rail
		float rw = 80;
		panel(c, x0, y0, rw, wh);
		if (logo != null) {
			c.stamp("menu:logo", logo, x0 + 24, y0 + 16, 32, 32, true, 0xFFFFFFFF);
		}
		c.fill(x0 + 16, y0 + 62, rw - 32, 1, Theme.HAIRLINE);
		float ty = y0 + 74;
		for (int i = 0; i < TABS.length; i++) {
			if (railTile(ui, "menu:tab:" + i, x0 + 8, ty, TAB_ICONS[i], TABS[i], tab == i)) {
				setTab(i);
			}
			ty += 66;
		}
		if (railTile(ui, "menu:rail:hud", x0 + 8, y0 + wh - 132, Theme.I_MOVE, "Edit HUD", false)) {
			openEditor(screen);
		}
		if (railTile(ui, "menu:rail:close", x0 + 8, y0 + wh - 68, Theme.I_X, "Close", false)) {
			UiRuntime.closeHost(screen);
			c.popAlpha();
			return;
		}

		// ── toolbar pill
		float mx = x0 + rw + 12, mw = ww - rw - 12;
		float tbH = 40;
		float tbW = toolbar(ui, mx, y0, tbH, screen);
		String who = UiRuntime.mc().username();
		if (who != null) {
			String hint = who;
			float hw = c.textWidth(Fonts.SEMIBOLD, 12, hint);
			float pw = hw + 40, px = mx + mw - pw;
			if (px > mx + tbW + 20) {
				c.shadow(px, y0 + 3, pw, tbH, tbH / 2, 18, 0x66000000);
				c.round(px, y0, pw, tbH, tbH / 2, PANEL_BG);
				c.outline(px, y0, pw, tbH, tbH / 2, 1, Theme.HAIRLINE_STRONG);
				c.circle(px + 18, y0 + tbH / 2, 3.5f, Theme.ONLINE);
				c.text(Fonts.SEMIBOLD, 12, hint, px + 28, y0 + (tbH - c.lineHeight(Fonts.SEMIBOLD, 12)) / 2, Theme.TEXT);
			}
		}

		// ── main panel
		float py = y0 + tbH + 10, ph = wh - tbH - 10;
		panel(c, mx, py, mw, ph);
		float hh = 60;
		c.fill(mx + 1, py + hh, mw - 2, 1, Theme.HAIRLINE);
		float bx = mx + 20, by = py + hh + 16, bw = mw - 40, bh = ph - hh - 32;
		float pe = ease(clamp01((ui.now - pageAt) / 220f));
		if (tab == 0) {
			if (selected == null) {
				float tx = header(c, "Mods", mx, py, hh);
				float sw = Math.min(220, mw * 0.26f);
				float chipsEnd = chips(ui, tx + 18, py + (hh - 28) / 2, mx + mw - 20 - sw - 14);
				drawSearch(ui, Math.max(chipsEnd + 12, mx + mw - 20 - sw), py + (hh - 34) / 2, sw, 34);
				c.pushAlpha(pe);
				mods(ui, bx, by + (1 - pe) * 10, bw, bh);
				c.popAlpha();
			} else {
				modPage(ui, selected, mx, py, mw, hh, bx, by, bw, bh, pe, screen);
			}
		} else if (tab == 1) {
			header(c, "Cosmetics", mx, py, hh);
			String store = "Get more in the Native Store";
			float sw = c.textWidth(Fonts.MEDIUM, 11.5f, store);
			c.icon(Theme.I_STORE, 14, mx + mw - 20 - sw - 12, py + hh / 2, Theme.TEXT_MUTED);
			c.text(Fonts.MEDIUM, 11.5f, store, mx + mw - 20 - sw, py + (hh - c.lineHeight(Fonts.MEDIUM, 11.5f)) / 2, Theme.TEXT_MUTED);
			c.pushAlpha(pe);
			cosmetics(ui, bx, by + (1 - pe) * 10, bw, bh);
			c.popAlpha();
		} else {
			header(c, "Settings", mx, py, hh);
			c.pushAlpha(pe);
			settings(ui, bx, by + (1 - pe) * 10, bw, bh, screen);
			c.popAlpha();
		}
		c.popAlpha();
	}

	/** Top-down sheen inside a rounded box; the top corners follow the radius so nothing pokes out of the outline. */
	private static void glow(Canvas c, float x, float y, float w, float gh, float r, int top) {
		float cap = r + 1;
		c.pushClip(x, y, w, cap);
		c.round(x + 1, y + 1, w - 2, cap * 2 + 2, r - 1, top);
		c.popClip();
		c.gradientV(x + 1, y + cap, w - 2, gh - cap, Theme.alpha(top, 1 - cap / gh), 0x00FFFFFF);
	}

	private static void panel(Canvas c, float x, float y, float w, float h) {
		c.shadow(x, y + 4, w, h, 16, 24, 0x88000000);
		c.round(x, y, w, h, 16, PANEL_BG);
		c.outline(x, y, w, h, 16, 1, Theme.HAIRLINE_STRONG);
	}

	private static float header(Canvas c, String title, float x, float y, float h) {
		c.text(Fonts.SEMIBOLD, 18, title, x + 20, y + (h - c.lineHeight(Fonts.SEMIBOLD, 18)) / 2, Theme.TEXT_STRONG);
		return x + 20 + c.textWidth(Fonts.SEMIBOLD, 18, title);
	}

	/** One square tab on the rail: icon with a tiny caption, a white tile when selected. */
	private boolean railTile(Ui ui, String id, float x, float y, int icon, String label, boolean on) {
		Canvas c = ui.c;
		float w = 64, h = 58;
		boolean over = ui.hover(x, y, w, h);
		boolean click = ui.clicked(id, x, y, w, h);
		float hv = ui.anim(id + "#h", over, 14f);
		float sel = ui.anim(id + "#on", on, 16f);
		c.round(x, y, w, h, 12, Theme.mix(Theme.alpha(Theme.SUBTLE_HOVER, hv), 0xFFF4F4F5, sel));
		int fg = Theme.mix(Theme.mix(Theme.TEXT_MUTED, Theme.TEXT_STRONG, hv), Theme.SOLID_FG, sel);
		c.icon(icon, 18, x + w / 2, y + 22, fg);
		String text = c.ellipsize(Fonts.SEMIBOLD, 10, label, w - 6);
		float lw = c.textWidth(Fonts.SEMIBOLD, 10, text);
		c.text(Fonts.SEMIBOLD, 10, text, x + (w - lw) / 2, y + 37, fg);
		return click;
	}

	/** Floating pill above the panel. Returns its width. */
	private float toolbar(Ui ui, float x, float y, float h, Object screen) {
		Canvas c = ui.c;
		float w = tab == 0 && selected == null ? 206 : 120;
		c.shadow(x, y + 3, w, h, h / 2, 18, 0x66000000);
		c.round(x, y, w, h, h / 2, PANEL_BG);
		c.outline(x, y, w, h, h / 2, 1, Theme.HAIRLINE_STRONG);
		if (ui.button("menu:tb:hud", x + 4, y + 4, 112, h - 8, "Edit HUD", Theme.I_MOVE, Ui.BTN_GHOST)) {
			openEditor(screen);
		}
		if (tab == 0 && selected == null) {
			c.fill(x + 122, y + 10, 1, h - 20, Theme.HAIRLINE_STRONG);
			if (pillIcon(ui, "menu:tb:grid", x + 130, y + 4, h - 8, Theme.I_GRID, !listView, "Grid")) {
				listView = false;
			}
			if (pillIcon(ui, "menu:tb:list", x + 130 + h - 4, y + 4, h - 8, Theme.I_LIST, listView, "List")) {
				listView = true;
			}
		}
		return w;
	}

	private boolean pillIcon(Ui ui, String id, float x, float y, float s, int icon, boolean on, String tip) {
		Canvas c = ui.c;
		boolean over = ui.hover(x, y, s, s);
		boolean click = ui.clicked(id, x, y, s, s);
		float hv = ui.anim(id + "#h", over, 14f);
		float sel = ui.anim(id + "#on", on, 16f);
		c.round(x, y, s, s, s / 2, Theme.mix(Theme.alpha(Theme.SUBTLE_HOVER, hv), 0xFFF4F4F5, sel));
		c.icon(icon, 14, x + s / 2, y + s / 2, Theme.mix(Theme.mix(Theme.TEXT_MUTED, Theme.TEXT_STRONG, hv), Theme.SOLID_FG, sel));
		return click;
	}

	private void drawSearch(Ui ui, float x, float y, float w, float h) {
		Canvas c = ui.c;
		boolean f = search.focused(ui);
		float fa = ui.anim("menu:search#f", f, 14f);
		c.round(x, y, w, h, h / 2, Theme.mix(0xB808090C, 0xE00E1014, fa));
		c.outline(x, y, w, h, h / 2, 1, Theme.mix(Theme.HAIRLINE_STRONG, Theme.BORDER_HOVER, fa));
		c.icon(Theme.I_SEARCH, 14, x + 18, y + h / 2, Theme.TEXT_MUTED);
		c.pushClip(x + 30, y + 1, w - 42, h - 2);
		search.draw(ui, x + 20, y, w - 24, h, "Search mods", 12.5f);
		c.popClip();
	}

	/** Category chips in the header. Returns where they end. */
	private float chips(Ui ui, float x, float y, float maxX) {
		Canvas c = ui.c;
		float chx = x;
		for (int i = 0; i < CHIPS.length; i++) {
			int n = 0;
			for (Module m : Modules.all()) {
				if (matches(m, i)) {
					n++;
				}
			}
			String label = CHIPS[i];
			String count = String.valueOf(n);
			float lw = c.textWidth(Fonts.SEMIBOLD, 11.5f, label), nw = c.textWidth(Fonts.MEDIUM, 10.5f, count);
			float cwid = lw + nw + 32;
			if (chx + cwid > maxX) {
				break;
			}
			String id = "menu:chip:" + i;
			boolean over = ui.hover(chx, y, cwid, 28);
			if (ui.clicked(id, chx, y, cwid, 28) && chip != i) {
				chip = i;
				grid.target = grid.offset = 0;
				pageAt = ui.now;
			}
			float on = ui.anim(id + "#on", chip == i, 16f);
			float hv = ui.anim(id + "#h", over, 14f);
			c.round(chx, y, cwid, 28, 14, Theme.mix(Theme.mix(Theme.SUBTLE, Theme.SUBTLE_HOVER, hv), 0xFFF4F4F5, on));
			if (on < 0.99f) {
				c.outline(chx, y, cwid, 28, 14, 1, Theme.alpha(Theme.HAIRLINE, 1 - on));
			}
			int fg = Theme.mix(Theme.mix(Theme.TEXT_SECONDARY, Theme.TEXT_STRONG, hv), Theme.SOLID_FG, on);
			c.text(Fonts.SEMIBOLD, 11.5f, label, chx + 13, y + (28 - c.lineHeight(Fonts.SEMIBOLD, 11.5f)) / 2, fg);
			c.text(Fonts.MEDIUM, 10.5f, count, chx + 19 + lw, y + (28 - c.lineHeight(Fonts.MEDIUM, 10.5f)) / 2,
					Theme.mix(Theme.TEXT_MUTED, 0xFF71717A, on));
			chx += cwid + 6;
		}
		return chx;
	}

	private static boolean matches(Module m, int chip) {
		if (chip == 0) {
			return true;
		}
		if (chip == 1) {
			return m.favorite;
		}
		return m.category.equals(CHIPS[chip]);
	}

	// ── Mods ─────────────────────────────────────────────────────────────

	private void mods(Ui ui, float x, float y, float w, float h) {
		Canvas c = ui.c;
		List<Module> list = filtered();
		if (list.isEmpty()) {
			if (chip == 1 && search.value().trim().isEmpty()) {
				empty(c, x, y, w, Math.min(h, 260), Theme.I_HEART, "No favorites yet", "Tap the heart on a mod to pin it here.");
			} else {
				empty(c, x, y, w, Math.min(h, 260), Theme.I_SEARCH, "No mods found", "Try another search or category.");
			}
			return;
		}
		float gap = 12;
		int cols;
		float cardW, cardH;
		if (listView) {
			cols = w > 640 ? 2 : 1;
			cardW = (w - gap * (cols - 1)) / cols;
			cardH = 60;
		} else {
			cols = Math.max(2, (int) ((w + gap) / (148 + gap)));
			cardW = (w - gap * (cols - 1)) / cols;
			cardH = Math.max(124, Math.min(150, cardW * 0.92f));
		}
		float off = grid.begin(ui, x, y, w + 6, h);
		for (int i = 0; i < list.size(); i++) {
			Module m = list.get(i);
			float px = x + (i % cols) * (cardW + gap);
			float py = y + (i / cols) * (cardH + gap) - off;
			if (py + cardH < y - 4 || py > y + h + 4) {
				continue;
			}
			if (listView) {
				listRow(ui, m, px, py, cardW, cardH, i);
			} else {
				card(ui, m, px, py, cardW, cardH, i);
			}
		}
		int rows = (list.size() + cols - 1) / cols;
		grid.end(ui, rows * (cardH + gap) - gap);
	}

	private List<Module> filtered() {
		String q = search.value().trim().toLowerCase(Locale.ROOT);
		List<Module> out = new ArrayList<Module>();
		for (Module m : Modules.all()) {
			if (!matches(m, chip)) {
				continue;
			}
			if (!q.isEmpty() && !m.name.toLowerCase(Locale.ROOT).contains(q) && !m.description.toLowerCase(Locale.ROOT).contains(q)) {
				continue;
			}
			out.add(m);
		}
		return out;
	}

	/** Feather-style tile: big icon in the middle, name and switch along the bottom, a heart to pin it. */
	private void card(Ui ui, Module m, float x, float y, float w, float h, int index) {
		Canvas c = ui.c;
		String id = "menu:card:" + m.id;
		float e = ease(clamp01((ui.now - pageAt - 40 - index * 22) / 300f));
		c.pushAlpha(e);
		y += (1 - e) * 12;
		boolean over = ui.hover(x, y, w, h);
		boolean click = ui.clicked(id, x, y, w, h - 40);
		float hv = ui.anim(id + "#h", over, 14f);
		float on = ui.anim(id + "#on", m.enabled, 14f);
		c.round(x, y, w, h, 14, Theme.mix(CARD, CARD_HOVER, hv));
		if (on > 0.01f) {
			glow(c, x, y, w, (h - 40) * 0.9f, 14, Theme.alpha(0x12FFFFFF, on));
		}
		c.outline(x, y, w, h, 14, 1, Theme.mix(Theme.mix(Theme.HAIRLINE, Theme.HAIRLINE_STRONG, on), Theme.BORDER_HOVER, hv));
		float iy = y + (h - 40) / 2 + 3;
		c.icon(m.icon, 30, x + w / 2, iy, Theme.mix(Theme.mix(0xFF52525B, Theme.TEXT_SECONDARY, hv), Theme.TEXT_STRONG, on));
		if (heart(ui, id + ":fav", m, x + w - 32, y + 8, over)) {
			click = false;
		}
		// bottom bar
		c.fill(x + 1, y + h - 40, w - 2, 1, Theme.HAIRLINE);
		c.text(Fonts.SEMIBOLD, 12.5f, c.ellipsize(Fonts.SEMIBOLD, 12.5f, m.name, w - 66), x + 13,
				y + h - 20 - c.lineHeight(Fonts.SEMIBOLD, 12.5f) / 2, Theme.mix(Theme.TEXT, Theme.TEXT_STRONG, on));
		if (ui.toggle(id + ":t", x + w - 46, y + h - 30, m.enabled)) {
			m.setEnabled(!m.enabled);
			Modules.changed();
		}
		if (hv > 0.01f && m.allSettings().size() > 0) {
			c.icon(Theme.I_SLIDERS, 12, x + 18, y + 18, Theme.alpha(Theme.TEXT_MUTED, hv));
		}
		if (click) {
			open(m, ui);
		}
		c.popAlpha();
	}

	private void listRow(Ui ui, Module m, float x, float y, float w, float h, int index) {
		Canvas c = ui.c;
		String id = "menu:row:" + m.id;
		float e = ease(clamp01((ui.now - pageAt - 40 - index * 18) / 280f));
		c.pushAlpha(e);
		y += (1 - e) * 10;
		boolean over = ui.hover(x, y, w, h);
		boolean click = ui.clicked(id, x, y, w - 96, h);
		float hv = ui.anim(id + "#h", over, 14f);
		float on = ui.anim(id + "#on", m.enabled, 14f);
		c.round(x, y, w, h, 12, Theme.mix(CARD, CARD_HOVER, hv));
		c.outline(x, y, w, h, 12, 1, Theme.mix(Theme.HAIRLINE, Theme.BORDER_HOVER, hv));
		c.round(x + 12, y + 12, 36, 36, 10, Theme.mix(Theme.SUBTLE_HOVER, 0xFFF4F4F5, on));
		c.icon(m.icon, 17, x + 30, y + 30, Theme.mix(Theme.TEXT, Theme.SOLID_FG, on));
		float tw = w - 160;
		c.text(Fonts.SEMIBOLD, 13, c.ellipsize(Fonts.SEMIBOLD, 13, m.name, tw), x + 60, y + 12, Theme.TEXT_STRONG);
		c.text(Fonts.REGULAR, 11, c.ellipsize(Fonts.REGULAR, 11, m.description, tw), x + 60, y + 31, Theme.TEXT_MUTED);
		heart(ui, id + ":fav", m, x + w - 84, y + 18, true);
		if (ui.toggle(id + ":t", x + w - 46, y + 20, m.enabled)) {
			m.setEnabled(!m.enabled);
			Modules.changed();
		}
		if (click) {
			open(m, ui);
		}
		c.popAlpha();
	}

	/** The pin heart; visible on hover or when pinned. True when it took the click. */
	private boolean heart(Ui ui, String id, Module m, float x, float y, boolean visible) {
		Canvas c = ui.c;
		float s = 24;
		boolean over = ui.hover(x, y, s, s);
		boolean click = (visible || m.favorite) && ui.clicked(id, x, y, s, s);
		if (click) {
			m.favorite = !m.favorite;
			Modules.changed();
		}
		float v = ui.anim(id + "#v", visible || m.favorite, 14f);
		float hv = ui.anim(id + "#h", over, 16f);
		float fav = ui.anim(id + "#f", m.favorite, 14f);
		if (v > 0.01f) {
			c.round(x, y, s, s, s / 2, Theme.alpha(Theme.SUBTLE_HOVER, hv * v));
			c.icon(Theme.I_HEART, 13, x + s / 2, y + s / 2,
					Theme.alpha(Theme.mix(Theme.mix(Theme.TEXT_MUTED, Theme.TEXT_STRONG, hv), Theme.ACCENT, fav), v));
		}
		return click || (over && ui.pressed);
	}

	private void open(Module m, Ui ui) {
		selected = m;
		detail.target = detail.offset = 0;
		pageAt = ui.now;
		Widgets.listening = null;
	}

	/** A mod's own page: breadcrumb header, a hero card, settings in grouped cards, a live preview stage on the right. */
	private void modPage(Ui ui, Module m, float mx, float py, float mw, float hh, float x, float y, float w, float h, float pe, Object screen) {
		Canvas c = ui.c;
		float by = py + (hh - 32) / 2;
		if (ui.iconButton("menu:page:back", mx + 14, by, 32, Theme.I_ARROW_LEFT, "Back")) {
			selected = null;
			pageAt = ui.now;
			return;
		}
		// breadcrumb: Mods > Name
		float bx = mx + 58;
		float cw = c.textWidth(Fonts.MEDIUM, 13.5f, "Mods");
		boolean crumbOver = ui.hover(bx - 6, by, cw + 12, 32);
		if (crumbOver) {
			ui.cursorHand = true;
		}
		c.text(Fonts.MEDIUM, 13.5f, "Mods", bx, py + (hh - c.lineHeight(Fonts.MEDIUM, 13.5f)) / 2, crumbOver ? Theme.TEXT_STRONG : Theme.TEXT_MUTED);
		if (ui.clicked("menu:page:crumb", bx - 6, by, cw + 12, 32)) {
			selected = null;
			pageAt = ui.now;
			return;
		}
		c.icon(Theme.I_CHEVRON_RIGHT, 13, bx + cw + 14, py + hh / 2, Theme.TEXT_MUTED);
		c.text(Fonts.SEMIBOLD, 15, c.ellipsize(Fonts.SEMIBOLD, 15, m.name, mw * 0.35f), bx + cw + 28, py + (hh - c.lineHeight(Fonts.SEMIBOLD, 15)) / 2, Theme.TEXT_STRONG);
		// header actions
		float ax = mx + mw - 20;
		ax -= 104;
		if (ui.button("menu:page:hud", ax, py + (hh - 34) / 2, 104, 34, m.isHud() ? "Move" : "Edit HUD", Theme.I_MOVE, Ui.BTN_GLASS)) {
			if (m.isHud() && !m.enabled) {
				m.setEnabled(true);
				Modules.changed();
			}
			openEditor(screen);
		}
		ax -= 96;
		if (ui.button("menu:page:reset", ax, py + (hh - 34) / 2, 88, 34, "Reset", Theme.I_RESET, Ui.BTN_GHOST)) {
			m.resetSettings();
			if (m instanceof HudModule) {
				((HudModule) m).resetPosition();
			}
			Modules.changed();
		}
		ax -= 40;
		heart(ui, "menu:page:fav", m, ax + 4, py + (hh - 24) / 2, true);

		c.pushAlpha(pe);
		y += (1 - pe) * 10;
		float pw = Math.min(340, w * 0.42f);
		float lw = w - pw - 20;
		float off = detail.begin(ui, x, y, lw + 8, h);
		float ry = y - off;
		float cardW = lw - 6;
		// hero card
		float heroH = 92;
		c.round(x, ry, cardW, heroH, 14, CARD);
		c.outline(x, ry, cardW, heroH, 14, 1, Theme.HAIRLINE);
		float ic = 52;
		c.round(x + 16, ry + (heroH - ic) / 2, ic, ic, 14, m.enabled ? 0xFFF4F4F5 : Theme.SUBTLE_HOVER);
		c.icon(m.icon, 22, x + 16 + ic / 2, ry + heroH / 2, m.enabled ? Theme.SOLID_FG : Theme.TEXT_STRONG);
		float tx = x + 16 + ic + 16;
		float stateW = 86;
		c.text(Fonts.SEMIBOLD, 16, c.ellipsize(Fonts.SEMIBOLD, 16, m.name, cardW - (tx - x) - stateW - 16), tx, ry + 16, Theme.TEXT_STRONG);
		float catW = c.textWidth(Fonts.MEDIUM, 10, m.category.toUpperCase(java.util.Locale.ROOT)) + 14;
		c.round(tx, ry + 42, catW, 18, 9, Theme.SUBTLE_HOVER);
		c.text(Fonts.MEDIUM, 10, m.category.toUpperCase(java.util.Locale.ROOT), tx + 7, ry + 42 + (18 - c.lineHeight(Fonts.MEDIUM, 10)) / 2, Theme.TEXT_SECONDARY);
		wrap(c, Fonts.REGULAR, 11, m.description, tx + catW + 10, ry + 43, cardW - (tx - x) - catW - 10 - stateW - 16, 14, Theme.TEXT_MUTED, 2);
		String st = m.enabled ? "On" : "Off";
		float sw2 = c.textWidth(Fonts.SEMIBOLD, 11.5f, st);
		c.text(Fonts.SEMIBOLD, 11.5f, st, x + cardW - 16 - 38 - 8 - sw2, ry + heroH / 2 - c.lineHeight(Fonts.SEMIBOLD, 11.5f) / 2, m.enabled ? Theme.ONLINE : Theme.TEXT_MUTED);
		if (ui.toggle("menu:page:on:" + m.id, x + cardW - 16 - 38, ry + heroH / 2 - 10, m.enabled)) {
			m.setEnabled(!m.enabled);
			Modules.changed();
		}
		ry += heroH + 18;
		// settings card(s)
		if (!m.settings.isEmpty()) {
			ry = section(c, "SETTINGS", x + 2, ry);
			float ch = 0;
			for (Setting s : m.settings) {
				ch += Widgets.rowHeight(s);
			}
			c.round(x, ry, cardW, ch + 4, 14, CARD);
			c.outline(x, ry, cardW, ch + 4, 14, 1, Theme.HAIRLINE);
			float cy2 = ry + 2;
			boolean first = true;
			for (Setting s : m.settings) {
				if (!first) {
					c.fill(x + 16, cy2, cardW - 32, 1, Theme.HAIRLINE);
				}
				first = false;
				Widgets.row(ui, "menu:set:" + m.id + ":" + s.id, x + 16, cy2, cardW - 32, s);
				cy2 += Widgets.rowHeight(s);
			}
			ry += ch + 4 + 18;
		}
		if (m instanceof HudModule) {
			ry = section(c, "APPEARANCE", x + 2, ry);
			float ch = 0;
			for (Setting s : ((HudModule) m).appearance) {
				ch += Widgets.rowHeight(s);
			}
			c.round(x, ry, cardW, ch + 4, 14, CARD);
			c.outline(x, ry, cardW, ch + 4, 14, 1, Theme.HAIRLINE);
			float cy2 = ry + 2;
			boolean first = true;
			for (Setting s : ((HudModule) m).appearance) {
				if (!first) {
					c.fill(x + 16, cy2, cardW - 32, 1, Theme.HAIRLINE);
				}
				first = false;
				Widgets.row(ui, "menu:set:" + m.id + ":" + s.id, x + 16, cy2, cardW - 32, s);
				cy2 += Widgets.rowHeight(s);
			}
			ry += ch + 4 + 18;
		}
		detail.end(ui, ry + off - y + 8);

		// live preview stage
		float px = x + w - pw;
		c.round(px, y, pw, h, 14, 0x66000000);
		glow(c, px, y, pw, h * 0.5f, 14, 0x0CFFFFFF);
		c.outline(px, y, pw, h, 14, 1, Theme.HAIRLINE);
		c.circle(px + 20, y + 20, 3.5f, Theme.ONLINE);
		c.text(Fonts.SEMIBOLD, 10.5f, "LIVE PREVIEW", px + 32, y + 14, Theme.TEXT_MUTED);
		float wy = y + 40, wh = Math.min(h * 0.5f, 200);
		c.round(px + 12, wy, pw - 24, wh, 12, 0x55000000);
		c.pushClip(px + 12, wy, pw - 24, wh);
		for (float dx = 10; dx < pw - 24; dx += 16) {
			for (float dy = 10; dy < wh; dy += 16) {
				c.fill(px + 12 + dx, wy + dy, 1.5f, 1.5f, 0x1FFFFFFF);
			}
		}
		c.popClip();
		if (m instanceof HudModule) {
			HudModule hm = (HudModule) m;
			c.pushClip(px + 13, wy + 1, pw - 26, wh - 2);
			float keep = hm.scale.value;
			float[] sz = hm.measure(c, sample);
			float k = Math.min(1.6f, Math.min((pw - 60) / Math.max(1, sz[0]), (wh - 30) / Math.max(1, sz[1])));
			hm.scale.value = keep * Math.min(k, Math.max(1f, k * 0.6f)); // fit the box without touching the saved size
			sz = hm.measure(c, sample);
			c.pushAlpha(m.enabled ? 1f : 0.35f);
			hm.paint(ui, px + (pw - sz[0]) / 2, wy + (wh - sz[1]) / 2, sample);
			c.popAlpha();
			hm.scale.value = keep;
			c.popClip();
		} else {
			c.circle(px + pw / 2, wy + wh / 2, 38, m.enabled ? 0xFFF4F4F5 : Theme.SUBTLE_HOVER);
			c.icon(m.icon, 30, px + pw / 2, wy + wh / 2, m.enabled ? Theme.SOLID_FG : Theme.TEXT_STRONG);
		}
		c.outline(px + 12, wy, pw - 24, wh, 12, 1, Theme.HAIRLINE);
		float ty = wy + wh + 18;
		c.text(Fonts.SEMIBOLD, 13.5f, m.name, px + 16, ty, Theme.TEXT_STRONG);
		ty += 22;
		wrap(c, Fonts.REGULAR, 11.5f, m.description, px + 16, ty, pw - 32, 17, Theme.TEXT_SECONDARY, 4);
		c.fill(px + 16, y + h - 44, pw - 32, 1, Theme.HAIRLINE);
		c.circle(px + 20, y + h - 22, 3.5f, m.enabled ? Theme.ONLINE : Theme.TEXT_MUTED);
		c.text(Fonts.MEDIUM, 11, (m.enabled ? "On" : "Off") + (m.isHud() ? "  \u00b7  drag it around in Edit HUD" : ""), px + 30,
				y + h - 22 - c.lineHeight(Fonts.MEDIUM, 11) / 2, Theme.TEXT_MUTED);
		c.popAlpha();
	}

	private void openEditor(Object screen) {
		McBridge mc = UiRuntime.mc();
		Modules.save();
		mc.setScreen(mc.newHost(McBridge.HUD, mc.hostParent(screen)));
	}

	// ── Cosmetics (same layout as the launcher locker) ───────────────────

	private static Wardrobe.Item wornIn(int slot) {
		return slot == 0 ? Wardrobe.item(Wardrobe.equipped) : Wardrobe.item(Wardrobe.wearing.get(Wardrobe.SLOTS[slot]));
	}

	private void cosmetics(Ui ui, float x, float y, float w, float h) {
		Canvas c = ui.c;
		float sw = Math.min(320, w * 0.36f);
		Wardrobe.Item worn = wornIn(slot);
		Wardrobe.Item picked = pick == null || pick.isEmpty() ? null : Wardrobe.item(pick);
		boolean nothing = "".equals(pick);

		// stage
		c.round(x, y, sw, h, 14, 0x66000000);
		glow(c, x, y, sw, h * 0.6f, 14, 0x10FFFFFF);
		c.outline(x, y, sw, h, 14, 1, Theme.HAIRLINE);
		c.text(Fonts.SEMIBOLD, 10.5f, "PREVIEW", x + 16, y + 16, Theme.TEXT_MUTED);
		String title = nothing ? "No " + singular(slot) : picked != null ? name(picked) : "Your look";
		c.text(Fonts.SEMIBOLD, 15, c.ellipsize(Fonts.SEMIBOLD, 15, title, sw - 32), x + 16, y + 32, Theme.TEXT_STRONG);
		preview.bare = nothing ? Wardrobe.SLOTS[slot] : null;
		c.pushClip(x + 1, y + 56, sw - 2, h - 56 - 96);
		preview.draw(ui, "menu:preview", x, y + 60, sw, h - 60 - 104, picked);
		c.popClip();
		preview.bare = null;
		String hint = "Drag to turn";
		c.text(Fonts.REGULAR, 11, hint, x + (sw - c.textWidth(Fonts.REGULAR, 11, hint)) / 2, y + h - 92, Theme.TEXT_MUTED);
		c.fill(x + 16, y + h - 70, sw - 32, 1, Theme.HAIRLINE);
		float bx = x + 16, by = y + h - 54, bw = sw - 32, bh = 38;
		boolean ready = Wardrobe.state == Wardrobe.State.READY;
		if (Wardrobe.busy != null) {
			c.round(bx, by, bw, bh, 10, Theme.SUBTLE_HOVER);
			ui.dots(bx + bw / 2, by + bh / 2, Theme.TEXT_STRONG);
		} else if (!ready || (picked == null && !nothing)) {
			disabled(c, bx, by, bw, bh, ready ? "Pick something to try on" : "Equip");
		} else if (nothing) {
			if (worn != null) {
				if (ui.button("menu:locker:off", bx, by, bw, bh, "Take off " + singular(slot), Theme.I_BAN, Ui.BTN_PRIMARY)) {
					Wardrobe.toggle(worn);
				}
			} else {
				disabled(c, bx, by, bw, bh, "Nothing worn");
			}
		} else if (Wardrobe.isWorn(picked)) {
			if (ui.button("menu:locker:off", bx, by, bw, bh, "Take off", Theme.I_X, Ui.BTN_GLASS)) {
				Wardrobe.toggle(picked);
			}
		} else if (ui.button("menu:locker:on", bx, by, bw, bh, "Equip", Theme.I_CHECK, Ui.BTN_PRIMARY)) {
			Wardrobe.toggle(picked);
		}

		// locker
		float lx = x + sw + 20, lw = w - sw - 20;
		Wardrobe.State st = Wardrobe.state;
		if (st == Wardrobe.State.IDLE || st == Wardrobe.State.LOADING) {
			ui.dots(lx + lw / 2, y + h / 2, Theme.TEXT_SECONDARY);
			return;
		}
		if (st == Wardrobe.State.NO_ACCOUNT) {
			empty(c, lx, y, lw, h, Theme.I_LOCK, "Not signed in", "Start Minecraft from the Native Client to use your cosmetics.");
			return;
		}
		if (st == Wardrobe.State.ERROR) {
			empty(c, lx, y, lw, h - 60, Theme.I_WIFI_OFF, "Could not load your locker", "Check your connection and try again.");
			if (ui.button("menu:wardrobe:retry", lx + lw / 2 - 60, y + h / 2 + 20, 120, 36, "Retry", Theme.I_REFRESH, Ui.BTN_GLASS)) {
				Wardrobe.refresh(true);
			}
			return;
		}
		// slot tabs
		float tx = lx, tyy = y;
		for (int i = 0; i < Wardrobe.SLOTS.length; i++) {
			String label = Wardrobe.SLOT_NAMES[i].replace("Wings & Backpacks", "Back").replace("Hand Items", "Hand");
			float tw = c.textWidth(Fonts.SEMIBOLD, 11.5f, label) + 44;
			if (tx + tw > lx + lw) {
				tx = lx;
				tyy += 36;
			}
			String id = "menu:slot:" + i;
			boolean over = ui.hover(tx, tyy, tw, 30);
			if (ui.clicked(id, tx, tyy, tw, 30) && slot != i) {
				slot = i;
				pick = null;
				locker.target = locker.offset = 0;
				pageAt = ui.now;
			}
			float on = ui.anim(id + "#on", slot == i, 16f);
			float hv = ui.anim(id + "#h", over, 14f);
			c.round(tx, tyy, tw, 30, 15, Theme.mix(Theme.mix(Theme.SUBTLE, Theme.SUBTLE_HOVER, hv), 0xFFF4F4F5, on));
			if (on < 0.99f) {
				c.outline(tx, tyy, tw, 30, 15, 1, Theme.alpha(Theme.HAIRLINE, 1 - on));
			}
			int fg = Theme.mix(Theme.mix(Theme.TEXT_SECONDARY, Theme.TEXT_STRONG, hv), Theme.SOLID_FG, on);
			c.icon(SLOT_ICONS[i], 13, tx + 19, tyy + 15, fg);
			c.text(Fonts.SEMIBOLD, 11.5f, label, tx + 32, tyy + (30 - c.lineHeight(Fonts.SEMIBOLD, 11.5f)) / 2, fg);
			tx += tw + 6;
		}
		List<Wardrobe.Item> items = Wardrobe.ownedIn(Wardrobe.SLOTS[slot]);
		float hy = tyy + 50;
		c.text(Fonts.SEMIBOLD, 10.5f, "YOUR LOCKER", lx, hy, Theme.TEXT_MUTED);
		c.text(Fonts.SEMIBOLD, 17, Wardrobe.SLOT_NAMES[slot], lx, hy + 16, Theme.TEXT_STRONG);
		String owned = items.size() == 1 ? "1 owned" : items.size() + " owned";
		c.text(Fonts.MEDIUM, 11.5f, owned, lx + lw - c.textWidth(Fonts.MEDIUM, 11.5f, owned), hy + 22, Theme.TEXT_MUTED);
		float gy = hy + 52, gh = y + h - gy - (Wardrobe.error != null ? 26 : 0);
		float gap = 12;
		int cols = Math.max(2, (int) ((lw + gap) / (124 + gap)));
		float tw = (lw - gap * (cols - 1)) / cols, th = tw * 0.8f + 38;
		float off = locker.begin(ui, lx, gy, lw + 6, gh);
		int n = items.size() + 1;
		for (int i = 0; i < n; i++) {
			float ix = lx + (i % cols) * (tw + gap), iy = gy + (i / cols) * (th + gap) - off;
			if (iy + th < gy - 4 || iy > gy + gh + 4) {
				continue;
			}
			float e = ease(clamp01((ui.now - pageAt - 40 - i * 22) / 300f));
			c.pushAlpha(e);
			tile(ui, i == 0 ? null : items.get(i - 1), ix, iy + (1 - e) * 10, tw, th, worn);
			c.popAlpha();
		}
		locker.end(ui, ((n + cols - 1) / cols) * (th + gap) - gap);
		if (items.isEmpty() && gh > th + 120) {
			String msg = "Get " + Wardrobe.SLOT_NAMES[slot].toLowerCase(Locale.ROOT) + " in the Native Store in the launcher.";
			c.icon(Theme.I_STORE, 14, lx + 8, gy + th + 30, Theme.TEXT_MUTED);
			c.text(Fonts.REGULAR, 11.5f, c.ellipsize(Fonts.REGULAR, 11.5f, msg, lw - 24), lx + 22, gy + th + 30 - c.lineHeight(Fonts.REGULAR, 11.5f) / 2,
					Theme.TEXT_MUTED);
		}
		if (Wardrobe.error != null) {
			String err = c.ellipsize(Fonts.MEDIUM, 11.5f, Wardrobe.error, lw);
			c.text(Fonts.MEDIUM, 11.5f, err, lx, y + h - 16, 0xFFFCA5A5);
		}
	}

	/** One locker card; it == null is the "Nothing" card that empties the slot. */
	private void tile(Ui ui, Wardrobe.Item it, float x, float y, float w, float h, Wardrobe.Item worn) {
		Canvas c = ui.c;
		String key = it == null ? "" : it.id;
		String id = "menu:item:" + slot + ":" + key;
		boolean over = ui.hover(x, y, w, h);
		if (ui.clicked(id, x, y, w, h)) {
			pick = key;
		}
		boolean isWorn = it == null ? worn == null : Wardrobe.isWorn(it);
		boolean sel = key.equals(pick);
		float hv = ui.anim(id + "#h", over, 14f);
		float sl = ui.anim(id + "#s", sel, 16f);
		c.round(x, y, w, h, 12, Theme.mix(CARD, CARD_HOVER, Math.max(hv, sl * 0.7f)));
		c.outline(x, y, w, h, 12, sel ? 1.5f : 1, Theme.mix(Theme.mix(Theme.HAIRLINE, Theme.BORDER_HOVER, hv), 0xE6FFFFFF, sl));
		float meta = 34;
		float ix = x + 8, iy = y + 8, iw = w - 16, ih = h - meta - 12;
		c.round(ix, iy, iw, ih, 8, 0x40000000);
		if (it == null) {
			c.icon(Theme.I_BAN, 26, ix + iw / 2, iy + ih / 2, Theme.mix(Theme.TEXT_MUTED, Theme.TEXT_SECONDARY, hv));
		} else if (it.isCape()) {
			Image img = Wardrobe.url(it.stillUrl, true, 0);
			if (img != null && img != Wardrobe.NONE) {
				float dh = ih - 12, dw = dh * 10f / 16f;
				c.image(img, ix + (iw - dw) / 2, iy + 6, dw, dh, 1f / 64, 1f / 32, 11f / 64, 17f / 32, 0xFFFFFFFF);
			} else if (img == null) {
				ui.dots(ix + iw / 2, iy + ih / 2, Theme.TEXT_MUTED);
			}
		} else {
			Image img = Wardrobe.url(it.stillUrl, false, 256);
			if (img != null && img != Wardrobe.NONE) {
				float k = Math.min((iw - 8) / img.width, (ih - 8) / img.height);
				float dw = img.width * k, dh = img.height * k;
				c.image(img, ix + (iw - dw) / 2, iy + (ih - dh) / 2, dw, dh, 0, 0, 1, 1, 0xFFFFFFFF);
			} else if (img == null) {
				ui.dots(ix + iw / 2, iy + ih / 2, Theme.TEXT_MUTED);
			} else {
				c.icon(SLOT_ICONS[slot], 22, ix + iw / 2, iy + ih / 2, Theme.TEXT_MUTED);
			}
		}
		float on = ui.anim(id + "#on", isWorn, 14f);
		if (on > 0.01f) {
			c.circle(x + w - 17, y + 17, 9, Theme.alpha(0xFFF4F4F5, on));
			c.icon(Theme.I_CHECK, 11, x + w - 17, y + 17, Theme.alpha(Theme.SOLID_FG, on));
		}
		// card meta
		c.fill(x + 1, y + h - meta, w - 2, 1, Theme.HAIRLINE);
		float my = y + h - meta / 2;
		if (it != null && it.id.equals(Wardrobe.busy)) {
			ui.dots(x + w / 2, my, Theme.TEXT_STRONG);
		} else {
			String n = it == null ? "Nothing" : name(it);
			String tag = isWorn ? "Worn" : null;
			float tagW = tag == null ? 0 : c.textWidth(Fonts.MEDIUM, 10, tag) + 8;
			n = c.ellipsize(Fonts.SEMIBOLD, 11.5f, n, w - 22 - tagW);
			c.text(Fonts.SEMIBOLD, 11.5f, n, x + 11, my - c.lineHeight(Fonts.SEMIBOLD, 11.5f) / 2, isWorn || sel ? Theme.TEXT_STRONG : Theme.TEXT);
			if (tag != null) {
				c.text(Fonts.MEDIUM, 10, tag, x + w - 11 - tagW + 8, my - c.lineHeight(Fonts.MEDIUM, 10) / 2, Theme.ACCENT);
			}
		}
	}

	private static String name(Wardrobe.Item it) {
		return it.name == null ? it.id : it.name;
	}

	private static String singular(int slot) {
		String[] s = {"cape", "hat", "glasses", "back item", "shoes", "hand item"};
		return s[slot];
	}

	private static void disabled(Canvas c, float x, float y, float w, float h, String label) {
		c.round(x, y, w, h, 10, Theme.SUBTLE);
		c.outline(x, y, w, h, 10, 1, Theme.HAIRLINE);
		float tw = c.textWidth(Fonts.SEMIBOLD, 12.5f, label);
		c.text(Fonts.SEMIBOLD, 12.5f, label, x + (w - tw) / 2, y + (h - c.lineHeight(Fonts.SEMIBOLD, 12.5f)) / 2, Theme.TEXT_MUTED);
	}

	/** Word-wraps text into at most maxLines lines; the last one is ellipsized. */
	static float wrap(Canvas c, int face, float size, String text, float x, float y, float w, float lh, int color, int maxLines) {
		String[] words = text.split(" ");
		StringBuilder line = new StringBuilder();
		int lines = 0;
		for (int i = 0; i < words.length; i++) {
			String next = line.length() == 0 ? words[i] : line + " " + words[i];
			if (c.textWidth(face, size, next) > w && line.length() > 0) {
				if (lines == maxLines - 1) {
					StringBuilder rest = new StringBuilder(line);
					for (int j = i; j < words.length; j++) {
						rest.append(' ').append(words[j]);
					}
					c.text(face, size, c.ellipsize(face, size, rest.toString(), w), x, y, color);
					return y + lh;
				}
				c.text(face, size, line.toString(), x, y, color);
				y += lh;
				lines++;
				line.setLength(0);
				line.append(words[i]);
			} else {
				line.setLength(0);
				line.append(next);
			}
		}
		if (line.length() > 0) {
			c.text(face, size, line.toString(), x, y, color);
			y += lh;
		}
		return y;
	}

	// ── Settings ─────────────────────────────────────────────────────────

	private void settings(Ui ui, float x, float y, float w, float h, Object screen) {
		Canvas c = ui.c;
		UiConfig cfg = UiRuntime.config();
		sTitle.value = cfg.customTitle;
		sNotify.value = cfg.notifications;
		if (!ui.isPressing("menu:pref:scale")) {
			sScale.value = cfg.scale;
		}
		sMenu.value = cfg.menuKey;
		sChat.value = cfg.relayKey;
		float rw = Math.min(560, w);
		float off = prefs.begin(ui, x, y, w + 6, h);
		float ry = y - off;
		ry = section(c, "GENERAL", x, ry);
		Setting[] general = {sTitle, sNotify, sScale};
		for (Setting s : general) {
			Widgets.row(ui, "menu:pref:" + s.id, x, ry, rw, s);
			ry += Widgets.ROW;
			c.fill(x, ry, rw, 1, Theme.HAIRLINE);
		}
		ry = section(c, "KEYS", x, ry + 18);
		for (Setting s : new Setting[] {sMenu, sChat}) {
			Widgets.row(ui, "menu:pref:" + s.id, x, ry, rw, s);
			ry += Widgets.ROW;
			c.fill(x, ry, rw, 1, Theme.HAIRLINE);
		}
		ry = section(c, "HUD", x, ry + 18);
		float bw = (rw - 16) / 3;
		if (ui.button("menu:pref:edit", x, ry, bw, 36, "Edit HUD layout", Theme.I_MOVE, Ui.BTN_GLASS)) {
			openEditor(screen);
		}
		if (ui.button("menu:pref:positions", x + bw + 8, ry, bw, 36, "Reset positions", Theme.I_RESET, Ui.BTN_GLASS)) {
			Modules.resetPositions();
		}
		if (confirmReset) {
			if (ui.button("menu:pref:reset-yes", x + 2 * (bw + 8), ry, bw, 36, "Reset everything?", 0, Ui.BTN_DANGER)) {
				for (Module m : Modules.all()) {
					m.resetSettings();
					m.setEnabled(m.defaultEnabled);
				}
				Modules.resetPositions();
				confirmReset = false;
			}
		} else if (ui.button("menu:pref:reset", x + 2 * (bw + 8), ry, bw, 36, "Reset all mods", Theme.I_POWER, Ui.BTN_GHOST)) {
			confirmReset = true;
		}
		ry += 52;
		c.text(Fonts.REGULAR, 11, "Settings are saved to config/native-ui.json and config/native-modules.json.", x, ry, Theme.TEXT_MUTED);
		ry += 24;
		prefs.end(ui, ry + off - y);

		// write back
		boolean save = false;
		if (sTitle.value != cfg.customTitle) {
			cfg.customTitle = sTitle.value;
			save = true;
		}
		if (sNotify.value != cfg.notifications) {
			cfg.notifications = sNotify.value;
			save = true;
		}
		if (!ui.isPressing("menu:pref:scale") && Math.abs(sScale.value - cfg.scale) > 0.001f) {
			cfg.scale = sScale.value; // applied on release so the window doesn't jump under the cursor
			save = true;
		}
		if (sMenu.value != cfg.menuKey && sMenu.value != -1) {
			cfg.menuKey = sMenu.value;
			save = true;
		}
		if (sChat.value != cfg.relayKey && sChat.value != -1) {
			cfg.relayKey = sChat.value;
			save = true;
		}
		if (save) {
			cfg.save();
		}
	}

	private static float section(Canvas c, String label, float x, float y) {
		c.text(Fonts.SEMIBOLD, 10.5f, label, x, y, Theme.TEXT_MUTED);
		return y + 20;
	}

	static void empty(Canvas c, float x, float y, float w, float h, int icon, String title, String body) {
		float cy = y + h / 2 - 34;
		c.circle(x + w / 2, cy, 22, Theme.SUBTLE_HOVER);
		c.icon(icon, 18, x + w / 2, cy, Theme.TEXT_SECONDARY);
		c.text(Fonts.SEMIBOLD, 13.5f, title, x + (w - c.textWidth(Fonts.SEMIBOLD, 13.5f, title)) / 2, cy + 32, Theme.TEXT_STRONG);
		String b = c.ellipsize(Fonts.REGULAR, 11.5f, body, w - 24);
		c.text(Fonts.REGULAR, 11.5f, b, x + (w - c.textWidth(Fonts.REGULAR, 11.5f, b)) / 2, cy + 54, Theme.TEXT_MUTED);
	}

	static float ease(float t) {
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}

	static float clamp01(float v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}
}
