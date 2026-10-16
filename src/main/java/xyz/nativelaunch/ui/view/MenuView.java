package xyz.nativelaunch.ui.view;

import xyz.nativelaunch.ui.Avatars;
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
	private static final float RAIL = 54;
	/** Corner radius of the floating top bars — squared off a little so square avatars sit well. */
	private static final float PILL_R = 13;
	private static final int[] TAB_ICONS = {Theme.I_NAV_MODS, Theme.I_NAV_LOCKER, Theme.I_NAV_SETTINGS};
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
	/** The UI size slider was held last frame: its value is committed once it is let go. */
	private boolean scaleDragged;
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
		if (t == 1) {
			Wardrobe.refresh(false); // also when the tab was already open
		}
	}

	/** Jumps to a module's own page (settings + live preview) in the mods tab. */
	public void openMod(Module m) {
		setTab(0);
		selected = m;
		detail.target = detail.offset = 0;
		pageAt = System.currentTimeMillis();
		Widgets.listening = null;
	}

	private void setTab(int t) {
		if (tab == t) {
			return;
		}
		tab = t;
		selected = null;
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
		float rw = 72;
		panel(c, x0, y0, rw, wh);
		if (logo != null) {
			c.stamp("menu:logo", logo, x0 + (rw - 32) / 2, y0 + 16, 32, 32, true, 0xFFFFFFFF);
		}
		c.fill(x0 + 16, y0 + 60, rw - 32, 1, Theme.HAIRLINE);
		float rx = x0 + (rw - RAIL) / 2;
		float ty = y0 + 74;
		for (int i = 0; i < TABS.length; i++) {
			if (railTile(ui, "menu:tab:" + i, rx, ty, TAB_ICONS[i], TABS[i], tab == i)) {
				setTab(i);
			}
			ty += RAIL + 8;
		}
		if (railTile(ui, "menu:rail:close", rx, y0 + wh - RAIL - 16, Theme.I_X, "Close", false)) {
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
			float hw = c.textWidth(Fonts.SEMIBOLD, 12, who);
			float av = tbH - 14;
			float pw = hw + av + 30, px = mx + mw - pw;
			if (px > mx + tbW + 20) {
				c.shadow(px, y0 + 3, pw, tbH, PILL_R, 18, 0x66000000);
				c.round(px, y0, pw, tbH, PILL_R, PANEL_BG);
				c.outline(px, y0, pw, tbH, PILL_R, 1, Theme.HAIRLINE_STRONG);
				float ax = px + 7, ay = y0 + (tbH - av) / 2;
				Avatars.draw(c, Avatars.api(), who, Avatars.self(who), ax, ay, av, 7f);
				// online dot, bottom-right of the head
				c.circle(ax + av - 3, ay + av - 3, 5f, 0xFF05060A);
				c.circle(ax + av - 3, ay + av - 3, 3.5f, Theme.ONLINE);
				c.text(Fonts.SEMIBOLD, 12, who, ax + av + 11, y0 + (tbH - c.lineHeight(Fonts.SEMIBOLD, 12)) / 2, Theme.TEXT);
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
			if (selected != null) {
				c.pushAlpha(pe);
				modPage(ui, selected, mx, py, mw, ph, screen);
				c.popAlpha();
			} else {
				float tx = header(c, "Mods", mx, py, hh);
				float sw = Math.max(160, Math.min(340, mx + mw - 20 - tx - 30));
				drawSearch(ui, mx + mw - 20 - sw, py + (hh - 34) / 2, sw, 34);
				float cw = Math.min(184, bw * 0.24f);
				categories(ui, bx, by, cw, bh);
				c.fill(bx + cw + 12, by, 1, bh, Theme.HAIRLINE);
				float gx = bx + cw + 26, gw = bw - cw - 26;
				c.pushAlpha(pe);
				mods(ui, gx, by + (1 - pe) * 10, gw, bh);
				c.popAlpha();
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
	/** Square icon tile with an active marker; the label shows up as a tooltip. */
	private boolean railTile(Ui ui, String id, float x, float y, int icon, String label, boolean on) {
		Canvas c = ui.c;
		float s = RAIL;
		boolean over = ui.hover(x, y, s, s);
		boolean click = ui.clicked(id, x, y, s, s);
		float hv = ui.anim(id + "#h", over, 14f);
		float sel = ui.anim(id + "#on", on, 16f);
		c.round(x, y, s, s, 15, Theme.mix(Theme.alpha(Theme.SUBTLE_HOVER, hv * 0.85f), 0x16FFFFFF, sel));
		if (sel > 0.01f) {
			c.outline(x, y, s, s, 15, 1, Theme.alpha(Theme.HAIRLINE_STRONG, sel));
			float bh = 18 * sel;
			c.round(x - 5, y + (s - bh) / 2, 3, bh, 1.5f, Theme.alpha(Theme.WHITE, sel));
		}
		int fg = Theme.mix(Theme.mix(Theme.TEXT_MUTED, Theme.TEXT_SECONDARY, hv), Theme.TEXT_STRONG, sel);
		c.icon(icon, 24, x + s / 2, y + s / 2, fg);
		if (over) {
			ui.tip(label);
		}
		return click;
	}

	/** Floating pill above the panel. Returns its width. */
	private float toolbar(Ui ui, float x, float y, float h, Object screen) {
		Canvas c = ui.c;
		float w = tab == 0 && selected == null ? 206 : 120;
		c.shadow(x, y + 3, w, h, PILL_R, 18, 0x66000000);
		c.round(x, y, w, h, PILL_R, PANEL_BG);
		c.outline(x, y, w, h, PILL_R, 1, Theme.HAIRLINE_STRONG);
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
		c.round(x, y, s, s, 9, Theme.mix(Theme.alpha(Theme.SUBTLE_HOVER, hv), 0xFFF4F4F5, sel));
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
			cols = Math.max(2, (int) ((w + gap) / (150 + gap)));
			cardW = (w - gap * (cols - 1)) / cols;
			cardH = 156;
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

	/** Feather-style tile: icon and name on top, a wide ENABLED / DISABLED bar and a settings gear along the bottom. */
	private void card(Ui ui, Module m, float x, float y, float w, float h, int index) {
		Canvas c = ui.c;
		String id = "menu:card:" + m.id;
		float e = ease(clamp01((ui.now - pageAt - 40 - index * 18) / 300f));
		c.pushAlpha(e);
		y += (1 - e) * 12;
		boolean over = ui.hover(x, y, w, h);
		boolean click = ui.clicked(id, x, y, w, h - 44);
		float hv = ui.anim(id + "#h", over, 14f);
		float on = ui.anim(id + "#on", m.enabled, 14f);
		c.round(x, y, w, h, 12, Theme.mix(CARD, CARD_HOVER, hv));
		if (on > 0.01f) {
			glow(c, x, y, w, (h - 44) * 0.9f, 12, Theme.alpha(0x14FFFFFF, on));
		}
		c.outline(x, y, w, h, 12, 1, Theme.mix(Theme.mix(Theme.HAIRLINE, Theme.HAIRLINE_STRONG, on), Theme.BORDER_HOVER, hv));
		float iy = y + (h - 44) * 0.42f;
		c.icon(m.icon, 30, x + w / 2, iy - hv * 2, Theme.mix(Theme.mix(0xFF52525B, Theme.TEXT_SECONDARY, hv), Theme.TEXT_STRONG, on));
		String name = c.ellipsize(Fonts.SEMIBOLD, 12.5f, m.name, w - 20);
		float nw = c.textWidth(Fonts.SEMIBOLD, 12.5f, name);
		c.text(Fonts.SEMIBOLD, 12.5f, name, x + (w - nw) / 2, y + h - 44 - 26, Theme.mix(Theme.TEXT, Theme.TEXT_STRONG, on));
		if (heart(ui, id + ":fav", m, x + w - 32, y + 8, over)) {
			click = false;
		}
		float bx = x + 10, bw = w - 20, by = y + h - 38;
		if (!m.allSettings().isEmpty()) {
			if (ui.iconButton(id + ":gear", bx, by, 28, Theme.I_SETTINGS, "Settings")) {
				click = true;
			}
			bx += 32;
			bw -= 32;
		}
		if (stateButton(ui, id + ":t", bx, by, bw, 28, m.enabled, 10.5f)) {
			m.setEnabled(!m.enabled);
			Modules.changed();
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
			int col = Theme.alpha(Theme.mix(Theme.mix(Theme.TEXT_MUTED, Theme.TEXT_STRONG, hv), Theme.TEXT_STRONG, fav), v);
			// pinned: a solid white heart (the icon font only has outlines, so stack shrinking copies to fill it)
			if (fav > 0.01f) {
				int fill = Theme.alpha(Theme.TEXT_STRONG, fav * v);
				for (float fs = 11.5f; fs >= 2.5f; fs -= 1.5f) {
					c.icon(Theme.I_HEART, fs, x + s / 2, y + s / 2, fill);
				}
			}
			c.icon(Theme.I_HEART, 13, x + s / 2, y + s / 2, col);
		}
		return click || (over && ui.pressed);
	}

	private void open(Module m, Ui ui) {
		selected = m;
		detail.target = detail.offset = 0;
		pageAt = ui.now;
		Widgets.listening = null;
	}

	private static final int[] CHIP_ICONS = {Theme.I_GRID, Theme.I_HEART, Theme.I_LAYOUT, Theme.I_MOUSE, Theme.I_EYE, Theme.I_GAUGE};
	/** Card state colours (everything else stays monochrome like the rest of the Native UI). */
	private static final int ON = 0xFF22C55E, OFF = 0xFFEF4444;

	/** Category list on the left of the mods grid (Feather style): icon, name, count, a blue marker on the active one. */
	private void categories(Ui ui, float x, float y, float w, float h) {
		Canvas c = ui.c;
		c.text(Fonts.SEMIBOLD, 10, "CATEGORIES", x + 6, y + 2, Theme.TEXT_MUTED);
		float ry = y + 24, rh = 38;
		for (int i = 0; i < CHIPS.length; i++) {
			int n = 0;
			for (Module m : Modules.all()) {
				if (matches(m, i)) {
					n++;
				}
			}
			String id = "menu:cat:" + i;
			boolean over = ui.hover(x, ry, w, rh);
			if (ui.clicked(id, x, ry, w, rh) && chip != i) {
				chip = i;
				grid.target = grid.offset = 0;
				pageAt = ui.now;
			}
			float on = ui.anim(id + "#on", chip == i, 16f);
			float hv = ui.anim(id + "#h", over, 14f);
			c.round(x, ry, w, rh, 10, Theme.mix(Theme.alpha(Theme.SUBTLE_HOVER, hv * 0.7f), 0x16FFFFFF, on));
			if (on > 0.01f) {
				c.outline(x, ry, w, rh, 10, 1, Theme.alpha(Theme.HAIRLINE_STRONG, on));
				c.round(x - 5, ry + (rh - 18 * on) / 2, 3, 18 * on, 1.5f, Theme.alpha(Theme.WHITE, on));
			}
			int fg = Theme.mix(Theme.mix(Theme.TEXT_SECONDARY, Theme.TEXT_STRONG, hv), Theme.TEXT_STRONG, on);
			c.icon(CHIP_ICONS[i], 15, x + 22, ry + rh / 2, fg);
			c.text(Fonts.SEMIBOLD, 12.5f, CHIPS[i], x + 40, ry + (rh - c.lineHeight(Fonts.SEMIBOLD, 12.5f)) / 2, fg);
			String count = String.valueOf(n);
			float nw = c.textWidth(Fonts.MEDIUM, 10.5f, count);
			c.round(x + w - nw - 22, ry + (rh - 20) / 2, nw + 12, 20, 10, Theme.mix(Theme.SUBTLE, 0xFFF4F4F5, on));
			c.text(Fonts.MEDIUM, 10.5f, count, x + w - nw - 16, ry + (rh - c.lineHeight(Fonts.MEDIUM, 10.5f)) / 2, Theme.mix(Theme.TEXT_MUTED, Theme.SOLID_FG, on));
			ry += rh + 4;
		}
		int active = 0;
		for (Module m : Modules.all()) {
			if (m.enabled) {
				active++;
			}
		}
		String a = active + " of " + Modules.all().size() + " enabled";
		c.circle(x + 10, y + h - 12, 3.5f, ON);
		c.text(Fonts.MEDIUM, 11, a, x + 20, y + h - 12 - c.lineHeight(Fonts.MEDIUM, 11) / 2, Theme.TEXT_MUTED);
	}

	/** The wide ENABLED / DISABLED bar of a Feather card. True when clicked. */
	private static boolean stateButton(Ui ui, String id, float x, float y, float w, float h, boolean enabled, float fs) {
		Canvas c = ui.c;
		boolean over = ui.hover(x, y, w, h);
		boolean click = ui.clicked(id, x, y, w, h);
		float hv = ui.anim(id + "#h", over, 16f);
		float on = ui.anim(id + "#on", enabled, 14f);
		int tint = Theme.mix(OFF, ON, on);
		c.round(x, y, w, h, 8, Theme.alpha(tint, 0.16f + 0.1f * hv));
		c.outline(x, y, w, h, 8, 1, Theme.alpha(tint, 0.35f + 0.25f * hv));
		String t = enabled ? "ENABLED" : "DISABLED";
		float tw = c.textWidth(Fonts.BOLD, fs, t);
		c.text(Fonts.BOLD, fs, t, x + (w - tw) / 2, y + (h - c.lineHeight(Fonts.BOLD, fs)) / 2, Theme.mix(0xFFFCA5A5, 0xFF86EFAC, on));
		if (over) {
			ui.cursorHand = true;
		}
		return click;
	}

	/** A mod's settings on a dedicated full page: header with back button, enable switch, live preview, settings, and appearance. */
	private void modPage(Ui ui, Module m, float x, float y, float w, float h, Object screen) {
		Canvas c = ui.c;
		float hh = 60;

		// ── Top Header Bar ──
		float backW = 92, backH = 34;
		float backX = x + 20, backY = y + (hh - backH) / 2;
		if (ui.button("menu:page:back", backX, backY, backW, backH, "Back", Theme.I_ARROW_LEFT, Ui.BTN_GHOST)) {
			selected = null;
			pageAt = ui.now;
			return;
		}

		float divX = backX + backW + 14;
		c.fill(divX, y + 16, 1, hh - 32, Theme.HAIRLINE);

		float ic = 36;
		float ix = divX + 16, iy = y + (hh - ic) / 2;
		c.round(ix, iy, ic, ic, 10, m.enabled ? 0xFFF4F4F5 : Theme.SUBTLE_HOVER);
		c.icon(m.icon, 18, ix + ic / 2, iy + ic / 2, m.enabled ? Theme.SOLID_FG : Theme.TEXT_STRONG);

		float tx = ix + ic + 12;
		c.text(Fonts.SEMIBOLD, 15, m.name, tx, y + 15, Theme.TEXT_STRONG);
		c.text(Fonts.MEDIUM, 10, m.category.toUpperCase(Locale.ROOT), tx, y + 34, Theme.TEXT_MUTED);

		float heartX = tx + c.textWidth(Fonts.SEMIBOLD, 15, m.name) + 12;
		heart(ui, "menu:page:fav", m, heartX, y + 18, true);

		// Header Action Buttons (Right Aligned)
		float pad = 20;
		float stateW = 104, stateH = 34;
		float stateX = x + w - pad - stateW, stateY = y + (hh - stateH) / 2;
		if (stateButton(ui, "menu:page:on:" + m.id, stateX, stateY, stateW, stateH, m.enabled, 10)) {
			m.setEnabled(!m.enabled);
			Modules.changed();
		}

		float curRight = stateX;
		if (m.isHud()) {
			float hudW = 116;
			float hudX = curRight - 10 - hudW;
			if (ui.button("menu:page:hud", hudX, stateY, hudW, stateH, "Edit HUD", Theme.I_MOVE, Ui.BTN_PRIMARY)) {
				if (!m.enabled) {
					m.setEnabled(true);
					Modules.changed();
				}
				openEditor(screen);
			}
			curRight = hudX;
		}

		float resetW = 138;
		float resetX = curRight - 10 - resetW;
		if (ui.button("menu:page:reset", resetX, stateY, resetW, stateH, "Reset Settings", Theme.I_RESET, Ui.BTN_GHOST)) {
			m.resetSettings();
			if (m instanceof HudModule) {
				((HudModule) m).resetPosition();
			}
			Modules.changed();
		}

		// ── Content: settings on the left, a tall live preview on the right (stacked on narrow windows) ──
		float bx = x + pad, by = y + hh + 4, bw = w - pad * 2, bh = h - hh - 20;
		float gap = 16;
		if (bw >= 640) {
			float lw = Math.max(340, Math.min(bw * 0.56f, 600));
			settingsPanel(ui, m, bx, by, lw, bh);
			stage(ui, m, bx + lw + gap, by, bw - lw - gap, bh);
		} else {
			float stageH = Math.max(120, Math.min(200, bh * 0.38f));
			stage(ui, m, bx, by, bw, stageH);
			settingsPanel(ui, m, bx, by + stageH + gap, bw, bh - stageH - gap);
		}
	}

	/** Sections the player folded on a module's page (module id + title). */
	private final java.util.Set<String> folded = new java.util.HashSet<String>();
	private Image stageBackdrop;
	private boolean stageBackdropTried;

	/** The live preview: the module drawn large over a game scene, inside its own card. */
	private void stage(Ui ui, Module m, float x, float y, float w, float h) {
		Canvas c = ui.c;
		c.round(x, y, w, h, 12, CARD);
		c.outline(x, y, w, h, 12, 1, Theme.HAIRLINE);
		float in = 6, ix = x + in, iy = y + in, iw = w - in * 2, ih = h - in * 2;
		if (!stageBackdropTried) {
			stageBackdropTried = true;
			stageBackdrop = Image.resource("/assets/native/ui/hudbg.jpg");
		}
		c.pushClip(ix, iy, iw, ih);
		if (stageBackdrop != null) {
			c.imageCover(stageBackdrop, ix, iy, iw, ih, 1f, 0.5f, 0.5f, 0xFFFFFFFF);
			c.fill(ix, iy, iw, ih, 0x40000000);
		} else {
			c.fill(ix, iy, iw, ih, 0xFF0B0C10);
		}
		if (m instanceof HudModule) {
			HudModule hm = (HudModule) m;
			float keep = hm.scale.value;
			float[] sz = hm.measure(c, sample);
			float k = Math.min(2.2f, Math.min((iw - 48) / Math.max(1, sz[0]), (ih - 56) / Math.max(1, sz[1])));
			hm.scale.value = Math.max(0.5f, keep * k);
			sz = hm.measure(c, sample);
			float fit = Math.min(1f, Math.min((iw - 24) / Math.max(1, sz[0]), (ih - 40) / Math.max(1, sz[1])));
			if (fit < 1f) {
				hm.scale.value = Math.max(0.3f, hm.scale.value * fit);
				sz = hm.measure(c, sample);
			}
			c.pushAlpha(m.enabled ? 1f : 0.4f);
			hm.paint(ui, ix + (iw - sz[0]) / 2, iy + (ih - sz[1]) / 2, sample);
			c.popAlpha();
			hm.scale.value = keep;
		} else if (!m.preview(ui, sample, ix, iy, iw, ih)) {
			float cy = iy + ih / 2;
			c.circle(ix + iw / 2, cy, 32, m.enabled ? 0xFFF4F4F5 : Theme.SUBTLE_HOVER);
			c.icon(m.icon, 24, ix + iw / 2, cy, m.enabled ? Theme.SOLID_FG : Theme.TEXT_STRONG);
		}
		c.popClip();
		// tag in the corner
		String tag = m.enabled ? "LIVE PREVIEW" : "PREVIEW  \u00b7  OFF";
		float tw = c.textWidth(Fonts.SEMIBOLD, 9.5f, tag) + 18;
		c.round(ix + 10, iy + 10, tw, 20, 10, 0xB0000000);
		c.outline(ix + 10, iy + 10, tw, 20, 10, 1, Theme.HAIRLINE);
		c.text(Fonts.SEMIBOLD, 9.5f, tag, ix + 19, iy + 10 + (20 - c.lineHeight(Fonts.SEMIBOLD, 9.5f)) / 2, Theme.TEXT_SECONDARY);
	}

	/** What the module does, then its options in foldable sections (like a game client's module screen). */
	private void settingsPanel(Ui ui, Module m, float x, float y, float w, float h) {
		Canvas c = ui.c;
		c.round(x, y, w, h, 12, CARD);
		c.outline(x, y, w, h, 12, 1, Theme.HAIRLINE);
		float pad = 18;
		// info row
		float textW = w - pad * 2 - 26;
		float descH = wrapHeight(c, Fonts.REGULAR, 11.5f, m.description, textW, 16, 2);
		float infoH = Math.max(34, descH + 20);
		float iy = y + infoH / 2;
		c.circle(x + pad + 8, iy, 8, Theme.SUBTLE_HOVER);
		c.text(Fonts.BOLD, 10, "i", x + pad + 8 - c.textWidth(Fonts.BOLD, 10, "i") / 2, iy - c.lineHeight(Fonts.BOLD, 10) / 2, Theme.TEXT_SECONDARY);
		wrap(c, Fonts.REGULAR, 11.5f, m.description, x + pad + 26, y + (infoH - descH) / 2, textW, 16, Theme.TEXT_SECONDARY, 2);
		c.fill(x + 1, y + infoH, w - 2, 1, Theme.HAIRLINE);

		float top = y + infoH + 1, ph = h - infoH - 2;
		float off = detail.begin(ui, x + 1, top, w - 2, ph);
		float cy = top - off + 6;
		boolean any = false;
		cy = section(ui, m, "Options", m.shownSettings(), x, cy, w);
		any |= !m.shownSettings().isEmpty();
		if (m instanceof HudModule) {
			float before = cy;
			cy = section(ui, m, "Appearance", ((HudModule) m).shownAppearance(), x, cy, w);
			any |= cy != before;
		}
		if (!any) {
			String t = "Nothing to set up: this works as soon as it is on.";
			float my = top + ph / 2 - 30;
			c.circle(x + w / 2, my, 22, Theme.SUBTLE_HOVER);
			c.icon(m.icon, 18, x + w / 2, my, Theme.TEXT_SECONDARY);
			c.text(Fonts.MEDIUM, 12, t, x + (w - c.textWidth(Fonts.MEDIUM, 12, t)) / 2, my + 36, Theme.TEXT_MUTED);
		}
		detail.end(ui, cy - (top - off) + 8);
	}

	/** One foldable group: a header row (chevron, title, option count) and, when open, the option rows. Returns the y below it. */
	private float section(Ui ui, Module m, String title, List<Setting> rows, float x, float y, float w) {
		if (rows.isEmpty()) {
			return y;
		}
		Canvas c = ui.c;
		String key = m.id + ":" + title;
		boolean open = !folded.contains(key);
		float pad = 18, hh = 34;
		String id = "menu:sec:" + key;
		boolean over = ui.hover(x + 6, y, w - 12, hh);
		float hv = ui.anim(id + "#h", over, 14f);
		if (hv > 0.01f) {
			c.round(x + 6, y + 1, w - 12, hh - 2, 8, Theme.alpha(0xFFFFFFFF, 0.04f * hv));
		}
		float rot = ui.anim(id + "#o", open, 14f);
		c.icon(rot > 0.5f ? Theme.I_CHEVRON_DOWN : Theme.I_CHEVRON_RIGHT, 12, x + pad + 6, y + hh / 2, Theme.mix(Theme.TEXT_MUTED, Theme.TEXT_STRONG, hv));
		c.text(Fonts.SEMIBOLD, 12.5f, title, x + pad + 22, y + (hh - c.lineHeight(Fonts.SEMIBOLD, 12.5f)) / 2, Theme.TEXT_STRONG);
		String count = rows.size() + (rows.size() == 1 ? " option" : " options");
		c.text(Fonts.REGULAR, 10.5f, count, x + w - pad - 8 - c.textWidth(Fonts.REGULAR, 10.5f, count), y + (hh - c.lineHeight(Fonts.REGULAR, 10.5f)) / 2,
				Theme.TEXT_MUTED);
		if (over) {
			ui.cursorHand = true;
		}
		if (ui.clicked(id, x + 6, y, w - 12, hh)) {
			if (open) {
				folded.add(key);
			} else {
				folded.remove(key);
			}
		}
		y += hh;
		if (!open) {
			return y + 4;
		}
		float rx = x + pad + 4, rw = w - pad * 2 - 14;
		for (int i = 0; i < rows.size(); i++) {
			Setting s = rows.get(i);
			if (i > 0) {
				c.fill(rx, y, rw, 1, Theme.HAIRLINE);
			}
			Widgets.row(ui, "menu:set:" + m.id + ":" + s.id, rx, y, rw, s);
			y += Widgets.rowHeight(s);
		}
		return y + 10;
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
		if (SessionLock.locked()) {
			// Guest, expired or offline: no locker to show (and nothing stale to equip).
			SessionLock.draw(ui, "menu:locker:lock", lx, y, lw, h);
			return;
		}
		if (st == Wardrobe.State.NO_ACCOUNT) {
			Wardrobe.refresh(false); // signed in again: reload the locker
		}
		if (st == Wardrobe.State.IDLE || st == Wardrobe.State.LOADING || st == Wardrobe.State.NO_ACCOUNT) {
			ui.dots(lx + lw / 2, y + h / 2, Theme.TEXT_SECONDARY);
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
			Image strip = it.animated() ? Wardrobe.url(it.stripUrl, true, 0) : null;
			if (strip != null && strip != Wardrobe.NONE) {
				// animated cape: the current frame of the strip (frames are stacked top to bottom)
				float dh = ih - 12, dw = dh * 10f / 16f;
				float fr = it.frame(), n = it.frames;
				c.image(strip, ix + (iw - dw) / 2, iy + 6, dw, dh, 1f / 64, (fr * 32 + 1) / (32 * n), 11f / 64, (fr * 32 + 17) / (32 * n), 0xFFFFFFFF);
			} else if (img != null && img != Wardrobe.NONE) {
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
		if (it != null && it.partnerName != null) {
			partnerTag(c, it, ix + 5, iy + 5, iw - 34);
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

	/** "Aurex" with the partner's logo: the item was made with a Native partner. */
	private static void partnerTag(Canvas c, Wardrobe.Item it, float x, float y, float maxW) {
		String label = it.partnerName.replaceAll("(?i)\\s+(hosting|host|network|studios?)$", "");
		float h = 18, logo = 14;
		label = c.ellipsize(Fonts.SEMIBOLD, 10, label, Math.max(20, maxW - logo - 14));
		float w = c.textWidth(Fonts.SEMIBOLD, 10, label) + logo + 12;
		c.round(x, y, w, h, 9, Theme.mix(it.partnerAccent, 0xFF000000, 0.15f));
		c.circle(x + 2 + logo / 2, y + h / 2, logo / 2, 0xFFFFFFFF);
		Image img = it.partnerLogo == null ? Wardrobe.NONE : Wardrobe.url(it.partnerLogo, false, 64);
		if (img != null && img != Wardrobe.NONE) {
			c.image(img, x + 3, y + 3, logo - 2, logo - 2, 0, 0, 1, 1, 0xFFFFFFFF);
		}
		c.text(Fonts.SEMIBOLD, 10, label, x + logo + 6, y + (h - c.lineHeight(Fonts.SEMIBOLD, 10)) / 2, 0xFFFFFFFF);
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

	static float wrapHeight(Canvas c, int face, float size, String text, float w, float lh, int maxLines) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		String[] words = text.split(" ");
		StringBuilder line = new StringBuilder();
		int lines = 0;
		for (int i = 0; i < words.length; i++) {
			String next = line.length() == 0 ? words[i] : line + " " + words[i];
			if (c.textWidth(face, size, next) > w) {
				lines++;
				if (lines >= maxLines) {
					return lines * lh;
				}
				line.setLength(0);
				line.append(words[i]);
			} else {
				line.setLength(0);
				line.append(next);
			}
		}
		if (line.length() > 0) {
			lines++;
		}
		return lines * lh;
	}

	// ── Settings ─────────────────────────────────────────────────────────

	private void settings(Ui ui, float x, float y, float w, float h, Object screen) {
		Canvas c = ui.c;
		UiConfig cfg = UiRuntime.config();
		sTitle.value = cfg.customTitle;
		sNotify.value = cfg.notifications;
		// While the slider is held (and on the frame it is let go) the slider owns the value; reading the saved
		// size back here on the release frame used to throw the new size away, so it always snapped back.
		boolean scaleHeld = ui.isPressing("menu:pref:scale");
		if (!scaleHeld && !scaleDragged) {
			sScale.value = cfg.scale;
		}
		sMenu.value = cfg.menuKey;
		sChat.value = cfg.relayKey;

		boolean wide = w >= 760;
		float sideW = wide ? Math.min(320, w * 0.36f) : 0;
		float lw = wide ? w - sideW - 20 : w;
		float off = prefs.begin(ui, x, y, lw + 8, h);
		float ry = y - off;
		float cw = lw - 6;
		ry = group(ui, "general", x, ry, cw, Theme.I_SLIDERS, "General", "How Native looks and talks to you",
				new Setting[] {sTitle, sNotify, sScale});
		ry = group(ui, "keys", x, ry, cw, Theme.I_KEYBOARD, "Controls", "Click a key, then press the one you want",
				new Setting[] {sMenu, sChat});
		ry = hudCard(ui, x, ry, cw, screen);
		if (!wide) {
			ry = profileCard(ui, x, ry, cw) + 16;
			ry = aboutCard(ui, x, ry, cw) + 16;
		}
		c.text(Fonts.REGULAR, 11, "Saved automatically to config/native-ui.json and config/native-modules.json.", x + 4, ry, Theme.TEXT_MUTED);
		ry += 28;
		prefs.end(ui, ry + off - y);

		if (wide) {
			float sx = x + w - sideW;
			float sy = profileCard(ui, sx, y, sideW);
			aboutCard(ui, sx, sy + 16, sideW);
		}

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
		if (scaleHeld) {
			scaleDragged = true;
		} else if (scaleDragged) {
			scaleDragged = false;
			if (Math.abs(sScale.value - cfg.scale) > 0.001f) {
				cfg.scale = sScale.value; // applied on release so the window doesn't jump under the cursor
				save = true;
			}
		} else if (Math.abs(sScale.value - cfg.scale) > 0.001f) {
			cfg.scale = sScale.value; // reset button
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

	/** A settings card: icon tile, title and a hint on top, then the rows. Returns the y below it. */
	private float group(Ui ui, String id, float x, float y, float w, int icon, String title, String hint, Setting[] rows) {
		Canvas c = ui.c;
		float head = 62;
		float rh = 0;
		for (Setting s : rows) {
			rh += Widgets.rowHeight(s);
		}
		float h = head + rh + 8;
		cardFrame(ui, "menu:pref:card:" + id, x, y, w, h);
		cardHead(c, x, y, w, icon, title, hint);
		float cy = y + head;
		for (int i = 0; i < rows.length; i++) {
			c.fill(x + 18, cy, w - 36, 1, Theme.HAIRLINE);
			Widgets.row(ui, "menu:pref:" + rows[i].id, x + 18, cy, w - 36, rows[i]);
			cy += Widgets.rowHeight(rows[i]);
		}
		return y + h + 16;
	}

	private void cardFrame(Ui ui, String id, float x, float y, float w, float h) {
		Canvas c = ui.c;
		float hv = ui.anim(id, ui.hover(x, y, w, h), 10f);
		c.round(x, y, w, h, 16, Theme.mix(CARD, CARD_HOVER, hv));
		glow(c, x, y, w, Math.min(h, 70), 16, 0x0AFFFFFF);
		c.outline(x, y, w, h, 16, 1, Theme.mix(Theme.HAIRLINE, Theme.HAIRLINE_STRONG, hv));
	}

	private static void cardHead(Canvas c, float x, float y, float w, int icon, String title, String hint) {
		float ic = 34;
		c.round(x + 18, y + 14, ic, ic, 10, Theme.SUBTLE_HOVER);
		c.outline(x + 18, y + 14, ic, ic, 10, 1, Theme.HAIRLINE);
		c.icon(icon, 16, x + 18 + ic / 2, y + 14 + ic / 2, Theme.TEXT_STRONG);
		float tx = x + 18 + ic + 14;
		c.text(Fonts.SEMIBOLD, 14, title, tx, y + 15, Theme.TEXT_STRONG);
		c.text(Fonts.REGULAR, 11, c.ellipsize(Fonts.REGULAR, 11, hint, w - (tx - x) - 18), tx, y + 34, Theme.TEXT_MUTED);
	}

	private float hudCard(Ui ui, float x, float y, float w, Object screen) {
		Canvas c = ui.c;
		float h = 62 + 74 + 14;
		cardFrame(ui, "menu:pref:card:hud", x, y, w, h);
		cardHead(c, x, y, w, Theme.I_LAYOUT, "HUD", "Where your HUD mods sit on screen");
		float ty = y + 62, gap = 10;
		float tw = (w - 36 - gap * 2) / 3, th = 74;
		if (tile(ui, "menu:pref:edit", x + 18, ty, tw, th, Theme.I_MOVE, "Edit layout", "Drag and resize", false)) {
			openEditor(screen);
		}
		if (tile(ui, "menu:pref:positions", x + 18 + tw + gap, ty, tw, th, Theme.I_RESET, "Reset positions", "Back to defaults", false)) {
			Modules.resetPositions();
		}
		String label = confirmReset ? "Click to confirm" : "Reset all mods";
		if (tile(ui, "menu:pref:reset", x + 18 + (tw + gap) * 2, ty, tw, th, Theme.I_POWER, label, confirmReset ? "Settings + on/off" : "Every setting", true)) {
			if (confirmReset) {
				for (Module m : Modules.all()) {
					m.resetSettings();
					m.setEnabled(m.defaultEnabled);
				}
				Modules.resetPositions();
				confirmReset = false;
			} else {
				confirmReset = true;
			}
		}
		if (confirmReset && ui.pressed && !ui.hover(x + 18 + (tw + gap) * 2, ty, tw, th)) {
			confirmReset = false; // clicking anywhere else cancels
		}
		return y + h + 16;
	}

	/** A square-ish action tile with an icon, a title and a hint. */
	private boolean tile(Ui ui, String id, float x, float y, float w, float h, int icon, String title, String hint, boolean danger) {
		Canvas c = ui.c;
		boolean over = ui.hover(x, y, w, h);
		boolean click = ui.clicked(id, x, y, w, h);
		float hv = ui.anim(id + "#h", over, 14f);
		float pr = ui.anim(id + "#p", ui.isPressing(id), 20f);
		boolean armed = danger && confirmReset;
		int bg = armed ? Theme.mix(0x26EF4444, 0x40EF4444, hv) : Theme.mix(Theme.SUBTLE, Theme.SUBTLE_HOVER, hv);
		float sy = y + pr * 1.5f;
		c.round(x, sy, w, h, 12, bg);
		c.outline(x, sy, w, h, 12, 1, armed ? 0x4DEF4444 : Theme.mix(Theme.HAIRLINE, Theme.HAIRLINE_STRONG, hv));
		int fg = armed ? 0xFFFCA5A5 : (danger ? Theme.mix(Theme.TEXT_SECONDARY, 0xFFFCA5A5, hv) : Theme.TEXT_STRONG);
		c.icon(icon, 15, x + 22, sy + 22, fg);
		c.text(Fonts.SEMIBOLD, 12, c.ellipsize(Fonts.SEMIBOLD, 12, title, w - 24), x + 12, sy + h - 36, fg);
		c.text(Fonts.REGULAR, 10.5f, c.ellipsize(Fonts.REGULAR, 10.5f, hint, w - 24), x + 12, sy + h - 19, Theme.TEXT_MUTED);
		if (over) {
			ui.cursorHand = true;
		}
		return click;
	}

	private float profileCard(Ui ui, float x, float y, float w) {
		Canvas c = ui.c;
		float h = 196;
		cardFrame(ui, "menu:pref:card:me", x, y, w, h);
		glow(c, x, y, w, 90, 16, 0x10FFFFFF);
		String who = UiRuntime.mc().username();
		xyz.nativelaunch.relay.RelayClient rc = xyz.nativelaunch.relay.RelayClient.get();
		boolean signed = rc != null && xyz.nativelaunch.core.Session.signedIn();
		float av = 56;
		Avatars.draw(c, Avatars.api(), who == null ? "Steve" : who, who == null ? null : Avatars.self(who), x + 18, y + 18, av, 12f);
		int dot = !signed ? 0xFF71717A : rc.state == xyz.nativelaunch.relay.RelayClient.State.ONLINE ? Theme.ONLINE : Theme.IDLE;
		c.circle(x + 18 + av - 4, y + 18 + av - 4, 7f, 0xFF05060A);
		c.circle(x + 18 + av - 4, y + 18 + av - 4, 5f, dot);
		float tx = x + 18 + av + 14;
		c.text(Fonts.SEMIBOLD, 16, c.ellipsize(Fonts.SEMIBOLD, 16, who == null ? "Player" : who, w - (tx - x) - 16), tx, y + 24, Theme.TEXT_STRONG);
		String status = !signed ? (xyz.nativelaunch.core.Session.status() == xyz.nativelaunch.core.Session.Status.GUEST
				? "Guest \u00b7 start from the Native Client to sign in" : xyz.nativelaunch.core.Session.lockTitle())
				: rc.state == xyz.nativelaunch.relay.RelayClient.State.ONLINE ? "Signed in \u00b7 Relay online"
				: rc.state == xyz.nativelaunch.relay.RelayClient.State.CONNECTING ? "Signed in \u00b7 connecting\u2026" : "Signed in \u00b7 Relay offline";
		c.text(Fonts.REGULAR, 11, c.ellipsize(Fonts.REGULAR, 11, status, w - (tx - x) - 16), tx, y + 48, Theme.TEXT_MUTED);
		// stats
		int on = 0, all = 0;
		for (Module m : Modules.all()) {
			all++;
			if (m.enabled) {
				on++;
			}
		}
		float sy = y + 92, sw = (w - 36 - 10) / 2;
		stat(c, x + 18, sy, sw, String.valueOf(on), "of " + all + " mods on");
		stat(c, x + 18 + sw + 10, sy, sw, String.valueOf(Wardrobe.owned.size()), "cosmetics owned");
		float by = y + h - 18 - 34;
		if (ui.button("menu:pref:locker", x + 18, by, w - 36, 34, "Open locker", Theme.I_SHIRT, Ui.BTN_PRIMARY)) {
			setTab(1);
		}
		return y + h;
	}

	private static void stat(Canvas c, float x, float y, float w, String value, String label) {
		c.round(x, y, w, 44, 10, Theme.SUBTLE);
		c.text(Fonts.BOLD, 15, value, x + 12, y + 6, Theme.TEXT_STRONG);
		c.text(Fonts.REGULAR, 10.5f, c.ellipsize(Fonts.REGULAR, 10.5f, label, w - 24), x + 12, y + 25, Theme.TEXT_MUTED);
	}

	private float aboutCard(Ui ui, float x, float y, float w) {
		Canvas c = ui.c;
		float h = 112;
		cardFrame(ui, "menu:pref:card:about", x, y, w, h);
		float lg = 34;
		c.image(logo, x + 18, y + 16, lg, lg, 0, 0, 1, 1, 0xFFFFFFFF);
		c.text(Fonts.SEMIBOLD, 14, "Native Client", x + 18 + lg + 12, y + 17, Theme.TEXT_STRONG);
		c.text(Fonts.REGULAR, 11, "Mod " + modVersion() + "  \u00b7  Minecraft " + gameVersion(), x + 18 + lg + 12, y + 36, Theme.TEXT_MUTED);
		c.fill(x + 18, y + 64, w - 36, 1, Theme.HAIRLINE);
		c.text(Fonts.REGULAR, 11, c.ellipsize(Fonts.REGULAR, 11, "Updates install from the launcher. playnative.net", w - 36), x + 18, y + 80, Theme.TEXT_SECONDARY);
		return y + h;
	}

	private static String modVersion() {
		return fabricVersion("native");
	}

	private static String gameVersion() {
		return fabricVersion("minecraft");
	}

	private static String fabricVersion(String id) {
		try {
			return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(id)
					.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
		} catch (Throwable t) {
			return "?";
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
