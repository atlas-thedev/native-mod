package xyz.nativelaunch.ui.view;

import xyz.nativelaunch.ui.McBridge;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.UiRuntime;
import xyz.nativelaunch.ui.Widgets;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.ui.mod.Game;
import xyz.nativelaunch.ui.mod.HudModule;
import xyz.nativelaunch.ui.mod.Modules;
import xyz.nativelaunch.ui.mod.Setting;

import java.util.ArrayList;
import java.util.List;

/** Drag HUD modules around, with snapping guides; scroll to resize, right-click for settings. */
public final class HudEditor {
	private static final float SNAP = 6;
	private HudModule drag, popover;
	private HudModule sizing;
	private float sizeStart, sizeDist;
	private float grabX, grabY;
	private float popX, popY, popW, popH;
	private final List<HudModule> shown = new ArrayList<HudModule>();
	private final List<float[]> rects = new ArrayList<float[]>();
	private float guideX = Float.NaN, guideY = Float.NaN;
	private Game sample = Game.sample();
	private long sampleAt;
	private Object lastScreen;
	private long shownAt;

	public boolean escape() {
		if (Widgets.escape()) {
			return true;
		}
		if (popover != null) {
			popover = null;
			return true;
		}
		return false;
	}

	public void draw(Ui ui, Object screen, boolean inWorld, Game live) {
		Canvas c = ui.c;
		if (screen != lastScreen) {
			lastScreen = screen;
			shownAt = ui.now;
			popover = null;
			drag = null;
		}
		if (ui.now - sampleAt > 120) {
			sample = Game.sample();
			sampleAt = ui.now;
		}
		float W = c.width(), H = c.height();
		float in = MenuView.clamp01((ui.now - shownAt) / 220f);
		c.fill(0, 0, W, H, Theme.alpha(inWorld ? 0x66000000 : 0x55000000, in));
		if (drag != null) {
			float step = 20;
			for (float gx = step; gx < W; gx += step) {
				c.fill(gx, 0, 1, H, 0x0CFFFFFF);
			}
			for (float gy = step; gy < H; gy += step) {
				c.fill(0, gy, W, 1, 0x0CFFFFFF);
			}
			c.fill(W / 2, 0, 1, H, 0x1AFFFFFF);
			c.fill(0, H / 2, W, 1, 0x1AFFFFFF);
		}

		// modules and their boxes
		shown.clear();
		rects.clear();
		for (HudModule m : Modules.hud()) {
			if (!m.enabled) {
				continue;
			}
			Game g = inWorld && live != null && m.visible(live) ? live : sample;
			float[] sz = m.measure(c, g);
			float w = sz[0], h = sz[1];
			shown.add(m);
			rects.add(new float[] {HudModule.pos(m.fx, W, w), HudModule.pos(m.fy, H, h), w, h});
		}
		boolean overPop = popover != null && ui.hover(popX, popY, popW, popH);
		boolean overBar = ui.hover(W / 2 - 190, H - 150, 380, 52);

		// pick
		HudModule hover = null;
		int hoverIndex = -1;
		HudModule handle = null;
		if (!overPop && !overBar && drag == null && sizing == null) {
			for (int i = shown.size() - 1; i >= 0; i--) {
				float[] r = rects.get(i);
				float hx = r[0] + r[2] + 3, hy = r[1] + r[3] + 3;
				if (ui.hover(hx - 9, hy - 9, 18, 18)) {
					handle = shown.get(i);
					hover = handle;
					hoverIndex = i;
					break;
				}
			}
		}
		if (handle != null && ui.pressed) {
			float[] r = rects.get(hoverIndex);
			sizing = handle;
			sizeStart = handle.scale.value;
			sizeDist = Math.max(8, (float) Math.hypot(ui.mx - (r[0] + r[2] / 2), ui.my - (r[1] + r[3] / 2)));
			popover = null;
			hover = null;
		}
		if (sizing != null) {
			int si = shown.indexOf(sizing);
			if (!ui.down || si < 0) {
				sizing = null;
				Modules.changed();
			} else {
				float[] r = rects.get(si);
				float d = (float) Math.hypot(ui.mx - (r[0] + r[2] / 2), ui.my - (r[1] + r[3] / 2));
				float v = Math.round(sizeStart * d / sizeDist / 0.05f) * 0.05f;
				if (Math.abs(v - sizing.scale.value) > 0.001f) {
					sizing.scale.set(v);
				}
				ui.cursorHand = true;
			}
		}
		if (!overPop && !overBar && drag == null && sizing == null && hover == null) {
			for (int i = shown.size() - 1; i >= 0; i--) {
				float[] r = rects.get(i);
				if (ui.hover(r[0] - 3, r[1] - 3, r[2] + 6, r[3] + 6)) {
					hover = shown.get(i);
					hoverIndex = i;
					break;
				}
			}
		}
		if (hover != null) {
			ui.cursorHand = true;
			if (ui.pressed) {
				drag = hover;
				float[] r = rects.get(hoverIndex);
				grabX = ui.mx - r[0];
				grabY = ui.my - r[1];
				popover = null;
			} else if (ui.rightPressed) {
				popover = popover == hover ? null : hover;
			} else if (ui.scroll != 0) {
				hover.scale.set(hover.scale.value + (ui.scroll > 0 ? 0.05f : -0.05f));
				Modules.changed();
			}
		} else if (ui.pressed && !overPop && !overBar) {
			popover = null;
		}
		guideX = guideY = Float.NaN;
		if (drag != null) {
			int di = shown.indexOf(drag);
			if (!ui.down || di < 0) {
				drag = null;
				Modules.changed();
			} else {
				float[] r = rects.get(di);
				float nx = ui.mx - grabX, ny = ui.my - grabY;
				float[] sx = snap(nx, r[2], W, di, true);
				float[] sy = snap(ny, r[3], H, di, false);
				nx = sx[0];
				ny = sy[0];
				guideX = sx[1];
				guideY = sy[1];
				nx = Math.max(0, Math.min(W - r[2], nx));
				ny = Math.max(0, Math.min(H - r[3], ny));
				drag.fx = HudModule.frac(nx, W, r[2]);
				drag.fy = HudModule.frac(ny, H, r[3]);
				r[0] = HudModule.pos(drag.fx, W, r[2]);
				r[1] = HudModule.pos(drag.fy, H, r[3]);
			}
		}
		if (!Float.isNaN(guideX)) {
			c.fill(guideX, 0, 1, H, Theme.alpha(Theme.ACCENT, 0.8f));
		}
		if (!Float.isNaN(guideY)) {
			c.fill(0, guideY, W, 1, Theme.alpha(Theme.ACCENT, 0.8f));
		}
		for (int i = 0; i < shown.size(); i++) {
			HudModule m = shown.get(i);
			float[] r = rects.get(i);
			Game g = inWorld && live != null && m.visible(live) ? live : sample;
			boolean active = m == hover || m == drag || m == popover || m == sizing;
			float a = ui.anim("hud:" + m.id + "#h", active, 16f);
			c.round(r[0] - 3, r[1] - 3, r[2] + 6, r[3] + 6, 6, Theme.alpha(0x14FFFFFF, a));
			m.paint(ui, r[0], r[1], g);
			if (a > 0.5f) {
				c.outline(r[0] - 3, r[1] - 3, r[2] + 6, r[3] + 6, 6, 1, Theme.alpha(0xFFFFFFFF, 0.7f * a));
				String label = m.name + "  " + Math.round(m.scale.value * 100) + "%";
				float lw = c.textWidth(Fonts.SEMIBOLD, 10.5f, label) + 12;
				float ly = r[1] - 24 < 0 ? r[1] + r[3] + 6 : r[1] - 24;
				float lx = Math.max(2, Math.min(W - lw - 2, r[0] - 3));
				c.round(lx, ly, lw, 18, 6, 0xF0141418);
				// resize handle (bottom-right corner): drag it to make the module bigger or smaller
				float hx = r[0] + r[2] + 3, hy = r[1] + r[3] + 3;
				c.circle(hx, hy, 5.5f, Theme.alpha(0xFFFFFFFF, a));
				c.circle(hx, hy, 3.5f, Theme.alpha(m == sizing ? Theme.ACCENT : 0xFF09090B, a));
				c.text(Fonts.SEMIBOLD, 10.5f, label, lx + 6, ly + (18 - c.lineHeight(Fonts.SEMIBOLD, 10.5f)) / 2, Theme.TEXT_STRONG);
			} else {
				dashed(c, r[0] - 3, r[1] - 3, r[2] + 6, r[3] + 6, 0x66FFFFFF);
			}
		}

		if (shown.isEmpty()) {
			float cw = 340, ch = 150, cx = (W - cw) / 2, cy = (H - ch) / 2;
			c.round(cx, cy, cw, ch, 14, 0xE008090C);
			c.outline(cx, cy, cw, ch, 14, 1, Theme.HAIRLINE_STRONG);
			c.icon(Theme.I_LAYOUT, 22, cx + cw / 2, cy + 32, Theme.TEXT_SECONDARY);
			String t = "No HUD modules are on";
			c.text(Fonts.SEMIBOLD, 14, t, cx + (cw - c.textWidth(Fonts.SEMIBOLD, 14, t)) / 2, cy + 54, Theme.TEXT_STRONG);
			String b = "Turn some on in the Mods tab first.";
			c.text(Fonts.REGULAR, 11.5f, b, cx + (cw - c.textWidth(Fonts.REGULAR, 11.5f, b)) / 2, cy + 78, Theme.TEXT_MUTED);
			if (ui.button("hud:empty:mods", cx + cw / 2 - 70, cy + ch - 46, 140, 34, "Open Mods", Theme.I_PUZZLE, Ui.BTN_PRIMARY)) {
				openMenu(screen);
				return;
			}
		}

		if (popover != null) {
			int pi = shown.indexOf(popover);
			if (pi < 0) {
				popover = null;
			} else {
				drawPopover(ui, popover, rects.get(pi), W, H);
			}
		}

		// toolbar
		float bw = 380, bh = 50, bx = (W - bw) / 2, by = H - 150 + (1 - in) * 20; // above the hotbar, clear of the top HUD (boss bar)
		c.shadow(bx, by + 2, bw, bh, 14, 16, 0x77000000);
		c.round(bx, by, bw, bh, 14, 0xF008090C);
		c.outline(bx, by, bw, bh, 14, 1, Theme.HAIRLINE_STRONG);
		if (ui.button("hud:done", bx + 8, by + 8, 100, 34, "Done", Theme.I_CHECK, Ui.BTN_PRIMARY)) {
			Modules.save();
			UiRuntime.closeHost(screen);
			return;
		}
		if (ui.button("hud:mods", bx + 116, by + 8, 100, 34, "Mods", Theme.I_PUZZLE, Ui.BTN_GLASS)) {
			openMenu(screen);
			return;
		}
		if (ui.button("hud:reset", bx + 224, by + 8, bw - 232, 34, "Reset positions", Theme.I_RESET, Ui.BTN_GHOST)) {
			Modules.resetPositions();
		}
		String tip = "Drag to move  \u00B7  Drag the corner or scroll to resize  \u00B7  Right-click for settings";
		c.text(Fonts.MEDIUM, 11, tip, (W - c.textWidth(Fonts.MEDIUM, 11, tip)) / 2, by - 22, Theme.alpha(0xFFFFFFFF, 0.6f * in));
	}

	private void openMenu(Object screen) {
		McBridge mc = UiRuntime.mc();
		Modules.save();
		mc.setScreen(mc.newHost(McBridge.MENU, mc.hostParent(screen)));
	}

	private void drawPopover(Ui ui, HudModule m, float[] r, float W, float H) {
		Canvas c = ui.c;
		List<Setting> all = m.allSettings();
		float w = 320, pad = 14;
		float h = pad + 30 + Widgets.ROW * (all.size() + 1) + pad;
		h = Math.min(h, H - 20);
		float x = r[0] + r[2] + 12;
		if (x + w > W - 8) {
			x = r[0] - w - 12;
		}
		if (x < 8) {
			x = Math.max(8, Math.min(W - w - 8, r[0]));
		}
		float y = Math.max(8, Math.min(H - h - 8, r[1]));
		popX = x;
		popY = y;
		popW = w;
		popH = h;
		c.shadow(x, y + 3, w, h, 12, 18, 0x88000000);
		c.round(x, y, w, h, 12, 0xF808090C);
		c.outline(x, y, w, h, 12, 1, Theme.HAIRLINE_STRONG);
		c.icon(m.icon, 14, x + pad + 8, y + pad + 10, Theme.TEXT_STRONG);
		c.text(Fonts.SEMIBOLD, 13.5f, m.name, x + pad + 24, y + pad + 1, Theme.TEXT_STRONG);
		if (ui.iconButton("hud:pop:close", x + w - pad - 24, y + pad - 2, 24, Theme.I_X, null)) {
			popover = null;
			return;
		}
		float ry = y + pad + 30;
		c.pushClip(x, ry, w, y + h - pad - ry + 4);
		float rw = w - pad * 2;
		c.text(Fonts.MEDIUM, 12.5f, "Enabled", x + pad, ry + (Widgets.ROW - c.lineHeight(Fonts.MEDIUM, 12.5f)) / 2, Theme.TEXT);
		if (ui.toggle("hud:pop:on", x + pad + rw - 34, ry + 10, m.enabled)) {
			m.setEnabled(false);
			Modules.changed();
			popover = null;
			c.popClip();
			return;
		}
		ry += Widgets.ROW;
		for (Setting s : all) {
			c.fill(x + pad, ry, rw, 1, Theme.HAIRLINE);
			Widgets.row(ui, "hud:pop:" + m.id + ":" + s.id, x + pad, ry, rw, s);
			ry += Widgets.ROW;
		}
		c.popClip();
	}

	/** Snaps one axis to the screen edges / centre / other modules. Returns {position, guide line or NaN}. */
	private float[] snap(float p, float size, float screen, int self, boolean xAxis) {
		float best = SNAP + 1, out = p, guide = Float.NaN;
		float m = HudModule.MARGIN;
		float[][] cands = {{m, m}, {screen - size - m, screen - m}, {screen / 2 - size / 2, screen / 2}};
		for (float[] cnd : cands) {
			float d = Math.abs(p - cnd[0]);
			if (d < best) {
				best = d;
				out = cnd[0];
				guide = cnd[1];
			}
		}
		for (int i = 0; i < rects.size(); i++) {
			if (i == self) {
				continue;
			}
			float[] r = rects.get(i);
			float o0 = xAxis ? r[0] : r[1], os = xAxis ? r[2] : r[3];
			float[][] edges = {{o0, o0}, {o0 + os - size, o0 + os}, {o0 + os + 4, o0 + os + 2}, {o0 - size - 4, o0 - 2},
					{o0 + os / 2 - size / 2, o0 + os / 2}};
			for (float[] e : edges) {
				float d = Math.abs(p - e[0]);
				if (d < best) {
					best = d;
					out = e[0];
					guide = e[1];
				}
			}
		}
		return new float[] {out, best <= SNAP ? guide : Float.NaN};
	}

	private static void dashed(Canvas c, float x, float y, float w, float h, int argb) {
		float dash = 4, gap = 3;
		for (float i = 0; i < w; i += dash + gap) {
			float l = Math.min(dash, w - i);
			c.fill(x + i, y, l, 1, argb);
			c.fill(x + i, y + h - 1, l, 1, argb);
		}
		for (float i = 0; i < h; i += dash + gap) {
			float l = Math.min(dash, h - i);
			c.fill(x, y + i, 1, l, argb);
			c.fill(x + w - 1, y + i, 1, l, argb);
		}
	}
}
