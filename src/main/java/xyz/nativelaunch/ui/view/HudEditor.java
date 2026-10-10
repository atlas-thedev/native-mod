package xyz.nativelaunch.ui.view;

import xyz.nativelaunch.ui.McBridge;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.UiRuntime;
import xyz.nativelaunch.ui.Widgets;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.ui.gfx.Image;
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
	/** Corner being dragged (0 top-left, 1 top-right, 2 bottom-right, 3 bottom-left) and the fixed opposite one. */
	private int sizeCorner;
	private float sizeStart, anchorX, anchorY, startW, startH;
	/** The Done bar: where it is (centre x, top y as screen fractions; NaN = default) and whether it is being moved. */
	private static float barFx = Float.NaN, barFy = Float.NaN;
	private boolean barDrag;
	private float barGrabX, barGrabY;
	private float barX, barY, barW, barH;
	private float grabX, grabY;
	private float popX, popY, popW, popH;
	private final List<HudModule> shown = new ArrayList<HudModule>();
	private final List<float[]> rects = new ArrayList<float[]>();
	private float guideX = Float.NaN, guideY = Float.NaN;
	private Game sample = Game.sample();
	private long sampleAt;
	private Object lastScreen;
	private long shownAt;
	/** Real in-game shot, used as the backdrop when the editor is opened from the menus. */
	private Image backdrop;
	private boolean backdropLoaded;

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
		if (!inWorld) {
			if (!backdropLoaded) {
				backdropLoaded = true;
				backdrop = Image.resource("/assets/native/ui/hudbg.jpg");
			}
			if (backdrop != null) {
				c.imageCover(backdrop, 0, 0, W, H, 1.02f, 0.5f, 0.5f, Theme.alpha(0xFFFFFFFF, in));
			}
		}
		c.fill(0, 0, W, H, Theme.alpha(inWorld ? 0x66000000 : 0x4D000000, in));
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
		boolean overBar = barDrag || ui.hover(barX, barY, barW, barH);

		// pick: a resize corner of the module under the cursor first
		HudModule hover = null;
		int hoverIndex = -1;
		HudModule handle = null;
		int handleCorner = -1;
		if (!overPop && !overBar && drag == null && sizing == null) {
			for (int i = shown.size() - 1; i >= 0 && handle == null; i--) {
				float[] r = rects.get(i);
				for (int k = 0; k < 4; k++) {
					float hx = cornerX(r, k), hy = cornerY(r, k);
					if (ui.hover(hx - 8, hy - 8, 16, 16)) {
						handle = shown.get(i);
						handleCorner = k;
						hover = handle;
						hoverIndex = i;
						break;
					}
				}
			}
		}
		if (handle != null && ui.pressed) {
			float[] r = rects.get(hoverIndex);
			sizing = handle;
			sizeCorner = handleCorner;
			sizeStart = handle.scale.value;
			startW = Math.max(4, r[2]);
			startH = Math.max(4, r[3]);
			// the opposite corner stays where it is while this one is dragged
			anchorX = (handleCorner == 0 || handleCorner == 3) ? r[0] + r[2] : r[0];
			anchorY = (handleCorner == 0 || handleCorner == 1) ? r[1] + r[3] : r[1];
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
				boolean left = sizeCorner == 0 || sizeCorner == 3, up = sizeCorner == 0 || sizeCorner == 1;
				float dx = Math.max(0, left ? anchorX - ui.mx : ui.mx - anchorX);
				float dy = Math.max(0, up ? anchorY - ui.my : ui.my - anchorY);
				// how far along the box's own diagonal the cursor is: grows evenly whichever way you pull
				float ratio = (dx * startW + dy * startH) / (startW * startW + startH * startH);
				float v = Math.round(sizeStart * ratio / 0.01f) * 0.01f;
				if (Math.abs(v - sizing.scale.value) > 0.001f) {
					sizing.scale.set(v);
				}
				Game g = inWorld && live != null && sizing.visible(live) ? live : sample;
				float[] sz = sizing.measure(c, g);
				float nw = sz[0], nh = sz[1];
				float nx = left ? anchorX - nw : anchorX, ny = up ? anchorY - nh : anchorY;
				nx = Math.max(0, Math.min(W - nw, nx));
				ny = Math.max(0, Math.min(H - nh, ny));
				sizing.fx = HudModule.frac(nx, W, nw);
				sizing.fy = HudModule.frac(ny, H, nh);
				r[0] = HudModule.pos(sizing.fx, W, nw);
				r[1] = HudModule.pos(sizing.fy, H, nh);
				r[2] = nw;
				r[3] = nh;
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
				// resize handles on all four corners: drag one, the opposite corner stays put
				for (int k = 0; k < 4; k++) {
					float hx = cornerX(r, k), hy = cornerY(r, k);
					boolean on = m == sizing && k == sizeCorner;
					boolean near = ui.hover(hx - 8, hy - 8, 16, 16);
					float hs = on || near ? 9 : 7;
					c.fill(hx - hs / 2, hy - hs / 2, hs, hs, Theme.alpha(0xFFFFFFFF, a));
					c.fill(hx - hs / 2 + 1.5f, hy - hs / 2 + 1.5f, hs - 3, hs - 3, Theme.alpha(on ? Theme.ACCENT : 0xFF09090B, a));
				}
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

		// Done bar: small, square-ish, and movable by its grip (it remembers where you put it)
		float bh = 36, pad = 5, gripW = 22, bw0 = 0;
		String[] labels = {"Done", "Mods", "Reset"};
		int[] icons = {Theme.I_CHECK, Theme.I_PUZZLE, Theme.I_RESET};
		float[] widths = new float[3];
		for (int i = 0; i < 3; i++) {
			widths[i] = c.textWidth(Fonts.SEMIBOLD, 12, labels[i]) + 36;
			bw0 += widths[i];
		}
		float bw = pad + gripW + 4 + bw0 + 4 * 2 + pad;
		if (barDrag) {
			if (!ui.down) {
				barDrag = false;
			} else {
				float nx = Math.max(4, Math.min(W - bw - 4, ui.mx - barGrabX));
				float ny = Math.max(4, Math.min(H - bh - 4, ui.my - barGrabY));
				barFx = (nx + bw / 2) / W;
				barFy = ny / H;
			}
		}
		float bx = Float.isNaN(barFx) ? (W - bw) / 2 : barFx * W - bw / 2;
		float by = Float.isNaN(barFy) ? H - 130 : barFy * H;
		bx = Math.max(4, Math.min(W - bw - 4, bx));
		by = Math.max(4, Math.min(H - bh - 4, by)) + (1 - in) * 12;
		barX = bx;
		barY = by;
		barW = bw;
		barH = bh;
		c.shadow(bx, by + 2, bw, bh, 6, 14, 0x77000000);
		c.round(bx, by, bw, bh, 6, 0xF20A0B0E);
		c.outline(bx, by, bw, bh, 6, 1, Theme.HAIRLINE_STRONG);
		// grip
		float gx = bx + pad, gy = by + pad, gh = bh - pad * 2;
		boolean overGrip = ui.hover(gx, gy, gripW, gh);
		float ga = ui.anim("hud:grip", overGrip || barDrag, 16f);
		c.round(gx, gy, gripW, gh, 4, Theme.alpha(0x14FFFFFF, ga));
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 2; col++) {
				c.fill(gx + gripW / 2 - 3.5f + col * 5, gy + gh / 2 - 6 + row * 5, 2, 2, Theme.mix(0x66FFFFFF, 0xFFFFFFFF, ga));
			}
		}
		if (overGrip) {
			ui.cursorHand = true;
			if (ui.pressed) {
				barDrag = true;
				barGrabX = ui.mx - bx;
				barGrabY = ui.my - by;
			}
		}
		float x0 = gx + gripW + 4;
		for (int i = 0; i < 3; i++) {
			if (barButton(ui, "hud:bar:" + i, x0, gy, widths[i], gh, labels[i], icons[i], i == 0)) {
				if (i == 0) {
					Modules.save();
					UiRuntime.closeHost(screen);
					return;
				} else if (i == 1) {
					openMenu(screen);
					return;
				} else {
					Modules.resetPositions();
				}
			}
			x0 += widths[i] + 4;
		}
		String tip = "Drag to move  \u00B7  Drag a corner or scroll to resize  \u00B7  Right-click for settings";
		float tipY = by > H / 2 ? by - 20 : by + bh + 8;
		c.text(Fonts.MEDIUM, 11, tip, (W - c.textWidth(Fonts.MEDIUM, 11, tip)) / 2, tipY, Theme.alpha(0xFFFFFFFF, 0.6f * in));
	}

	private static boolean barButton(Ui ui, String id, float x, float y, float w, float h, String label, int icon, boolean primary) {
		Canvas c = ui.c;
		boolean over = ui.hover(x, y, w, h);
		boolean click = ui.clicked(id, x, y, w, h);
		float hv = ui.anim(id + "#h", over, 16f);
		float pr = ui.anim(id + "#p", ui.isPressing(id), 24f);
		int bg = primary ? Theme.mix(Theme.mix(0xFFF4F4F5, 0xFFFFFFFF, hv), 0xFFD4D4D8, pr) : Theme.alpha(0x1FFFFFFF, hv + pr * 0.5f);
		int fg = primary ? Theme.SOLID_FG : Theme.mix(Theme.TEXT_SECONDARY, Theme.TEXT_STRONG, hv);
		c.round(x, y, w, h, 4, bg);
		float tw = c.textWidth(Fonts.SEMIBOLD, 12, label);
		float tx = x + (w - tw - 18) / 2;
		c.icon(icon, 13, tx + 6, y + h / 2, fg);
		c.text(Fonts.SEMIBOLD, 12, label, tx + 18, y + (h - c.lineHeight(Fonts.SEMIBOLD, 12)) / 2, fg);
		return click;
	}

	private static float cornerX(float[] r, int k) {
		return k == 0 || k == 3 ? r[0] - 3 : r[0] + r[2] + 3;
	}

	private static float cornerY(float[] r, int k) {
		return k == 0 || k == 1 ? r[1] - 3 : r[1] + r[3] + 3;
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
