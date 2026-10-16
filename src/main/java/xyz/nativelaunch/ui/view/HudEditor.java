package xyz.nativelaunch.ui.view;

import xyz.nativelaunch.ui.McBridge;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.UiRuntime;
import xyz.nativelaunch.ui.Widgets;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.voice.Voice;
import xyz.nativelaunch.ui.gfx.Image;
import xyz.nativelaunch.ui.mod.Game;
import xyz.nativelaunch.ui.mod.HudModule;
import xyz.nativelaunch.ui.mod.Modules;
import xyz.nativelaunch.ui.mod.Setting;

import java.util.ArrayList;
import java.util.List;

/**
 * The screen Right Shift opens: the game dimmed, every HUD module in place with a name tag, a gear (its settings) and a minus (take
 * it off the screen), and a hub in the middle with the way into the rest of Native. Drag a module to move it (it snaps to the edges,
 * the middle and other modules), drag a corner or scroll over it to resize, right-click for settings.
 */
public final class HudEditor {
	private static final float SNAP = 6;
	private static final int BLUE = 0xFF3B82F6, RED = 0xFFEF4444;
	private HudModule drag, popover;
	private HudModule sizing;
	/** Corner being dragged (0 top-left, 1 top-right, 2 bottom-right, 3 bottom-left) and the fixed opposite one. */
	private int sizeCorner;
	private float sizeStart, anchorX, anchorY, startW, startH;
	private float grabX, grabY;
	private float popX, popY, popW, popH;
	/** Voice overlay: the player whose volume bubble is open (click a face), its anchor and box. */
	private String volPick;
	private float volX, volY, pressX, pressY;
	private final float[] volRect = new float[4];
	private final List<HudModule> shown = new ArrayList<HudModule>();
	private final List<float[]> rects = new ArrayList<float[]>();
	/** Per shown module: {tag x, tag y, tag w, gear x, minus x}. */
	private final List<float[]> tags = new ArrayList<float[]>();
	private float guideX = Float.NaN, guideY = Float.NaN;
	private Game sample = Game.sample();
	private long sampleAt;
	private Object lastScreen;
	private long shownAt;
	/** Real in-game shot, used as the backdrop when the editor is opened from the menus. */
	private Image backdrop;
	private boolean backdropLoaded;
	private Image logo;
	private boolean drawerOpen;
	private float drawerScroll, drawerX, drawerY, grabDX, grabDY;
	private boolean drawerMoved;
	private final float[] drawerRect = new float[4], hubRect = new float[4], noticeRect = new float[4];
	private String notice;
	private HudModule removed;
	private long noticeAt;

	public boolean escape() {
		if (Widgets.escape()) {
			return true;
		}
		if (popover != null) {
			popover = null;
			return true;
		}
		if (drawerOpen) {
			drawerOpen = false;
			return true;
		}
		return false;
	}

	private static boolean in(float[] r, Ui ui) {
		return r[2] > 0 && ui.hover(r[0], r[1], r[2], r[3]);
	}

	private void say(Ui ui, String text, HudModule undo) {
		notice = text;
		removed = undo;
		noticeAt = ui.now;
	}

	public void draw(Ui ui, Object screen, boolean inWorld, Game live) {
		Canvas c = ui.c;
		if (screen != lastScreen) {
			lastScreen = screen;
			shownAt = ui.now;
			popover = null;
			drag = null;
			sizing = null;
			drawerOpen = false;
			notice = null;
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
		c.fill(0, 0, W, H, Theme.alpha(inWorld ? 0x80000000 : 0x59000000, in));
		c.gradientV(0, 0, W, H * 0.25f, Theme.alpha(0x66000000, in), 0);
		c.gradientV(0, H * 0.7f, W, H * 0.3f, 0, Theme.alpha(0x73000000, in));
		if (drag != null || sizing != null) {
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

		// where the hub and the drawer sit (they block the modules under them)
		float hw = 280, hh = 172;
		hubRect[0] = (W - hw) / 2;
		hubRect[1] = (H - hh) / 2 - 18 + (1 - in) * 14;
		hubRect[2] = hw;
		hubRect[3] = hh;
		float dt = ui.anim("hud:drawer", drawerOpen, 14f);
		float dw = Math.min(310, W * 0.34f);
		if (dt > 0.01f) {
			float dh = H - 32;
			if (drawerMoved) {
				drawerX = Math.max(0, Math.min(W - dw, drawerX));
				drawerY = Math.max(0, Math.min(H - dh, drawerY));
			}
			drawerRect[0] = (drawerMoved ? drawerX : W - dw - 16) + (1 - dt) * (dw + 30);
			drawerRect[1] = drawerMoved ? drawerY : 16;
			drawerRect[2] = dw;
			drawerRect[3] = dh;
		} else {
			drawerRect[2] = 0;
		}
		boolean overHub = ui.hover(hubRect[0] - 10, hubRect[1] - 10, hubRect[2] + 20, hubRect[3] + 20);
		boolean overDrawer = in(drawerRect, ui);
		boolean overNotice = notice != null && in(noticeRect, ui);

		// modules and their boxes
		shown.clear();
		rects.clear();
		tags.clear();
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
		for (int i = 0; i < shown.size(); i++) {
			float[] r = rects.get(i);
			float lw = c.textWidth(Fonts.SEMIBOLD, 10.5f, shown.get(i).name) + 16;
			float rowW = lw + 4 + 18 + 4 + 18;
			float lx = Math.max(2, Math.min(W - rowW - 2, r[0] - 3));
			float ly = r[1] - 3 - 22 < 2 ? r[1] + r[3] + 7 : r[1] - 3 - 22;
			tags.add(new float[] {lx, ly, lw, lx + lw + 4, lx + lw + 26});
		}
		boolean overPop = popover != null && ui.hover(popX, popY, popW, popH);
		boolean overVol = volPick != null && in(volRect, ui);
		boolean blocked = overHub || overDrawer || overNotice || overPop || overVol;

		// the gear / minus badges first: they sit over the tag row, outside the box
		HudModule tagHover = null;
		if (!blocked && drag == null && sizing == null) {
			for (int i = shown.size() - 1; i >= 0; i--) {
				float[] t = tags.get(i);
				if (ui.hover(t[0], t[1], t[4] + 18 - t[0], 18)) {
					tagHover = shown.get(i);
					ui.cursorHand = true;
					if (ui.pressed && ui.hover(t[3], t[1], 18, 18)) {
						// the gear goes straight to this module's page in the Native menu (right-click keeps the quick card)
						Modules.save();
						UiRuntime.openMod(UiRuntime.mc().hostParent(screen), tagHover);
						ui.pressed = false;
					} else if (ui.pressed && ui.hover(t[4], t[1], 18, 18)) {
						HudModule gone = tagHover;
						gone.setEnabled(false);
						Modules.changed();
						say(ui, gone.name + " removed", gone);
						if (popover == gone) {
							popover = null;
						}
						ui.pressed = false;
					}
					break;
				}
			}
		}

		// pick: a resize corner of the module under the cursor first
		HudModule hover = null;
		int hoverIndex = -1;
		HudModule handle = null;
		int handleCorner = -1;
		if (!blocked && drag == null && sizing == null && tagHover == null) {
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
		if (!blocked && drag == null && sizing == null && hover == null && tagHover == null) {
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
				pressX = ui.mx;
				pressY = ui.my;
				grabX = ui.mx - r[0];
				grabY = ui.my - r[1];
				popover = null;
			} else if (ui.rightPressed) {
				popover = popover == hover ? null : hover;
			} else if (ui.scroll != 0) {
				hover.scale.set(hover.scale.value + (ui.scroll > 0 ? 0.05f : -0.05f));
				Modules.changed();
				ui.scroll = 0;
			}
		} else if (ui.pressed && !blocked && tagHover == null) {
			popover = null;
		}
		guideX = guideY = Float.NaN;
		if (drag != null) {
			int di = shown.indexOf(drag);
			if (!ui.down || di < 0) {
				if (di >= 0 && Math.hypot(ui.mx - pressX, ui.my - pressY) < 4) {
					// a click, not a drag: the voice overlay opens a volume bubble for the face under it
					float[] r = rects.get(di);
					String pick = drag.pickAt(c, inWorld && live != null ? live : sample, pressX - r[0], pressY - r[1]);
					volPick = pick;
					volX = r[0] + r[2] + 10;
					volY = pressY - 20;
				}
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
				float[] t = tags.get(di);
				float lw = t[2], rowW = lw + 4 + 18 + 4 + 18;
				t[0] = Math.max(2, Math.min(W - rowW - 2, r[0] - 3));
				t[1] = r[1] - 3 - 22 < 2 ? r[1] + r[3] + 7 : r[1] - 3 - 22;
				t[3] = t[0] + lw + 4;
				t[4] = t[0] + lw + 26;
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
			float[] t = tags.get(i);
			Game g = inWorld && live != null && m.visible(live) ? live : sample;
			boolean active = m == hover || m == drag || m == popover || m == sizing || m == tagHover;
			float a = ui.anim("hud:" + m.id + "#h", active, 16f);
			c.round(r[0] - 3, r[1] - 3, r[2] + 6, r[3] + 6, 6, Theme.alpha(0x14FFFFFF, a));
			m.paint(ui, r[0], r[1], g);
			c.outline(r[0] - 3, r[1] - 3, r[2] + 6, r[3] + 6, 6, 1, Theme.mix(0x59FFFFFF, 0xE6FFFFFF, a));
			// name tag with the gear and minus badges
			c.round(t[0], t[1], t[2], 18, 9, Theme.mix(0xE60E0F13, 0xF2181A20, a));
			c.outline(t[0], t[1], t[2], 18, 9, 1, Theme.alpha(0xFFFFFFFF, 0.1f + 0.2f * a));
			c.text(Fonts.SEMIBOLD, 10.5f, m.name, t[0] + 8, t[1] + (18 - c.lineHeight(Fonts.SEMIBOLD, 10.5f)) / 2, Theme.mix(0xFFD4D4D8, 0xFFFFFFFF, a));
			badge(ui, "hud:gear:" + m.id, t[3], t[1], BLUE, Theme.I_SETTINGS, m == popover);
			badge(ui, "hud:minus:" + m.id, t[4], t[1], RED, Theme.I_MINUS, false);
			if (a > 0.3f) {
				// resize handles on all four corners: drag one, the opposite corner stays put
				for (int k = 0; k < 4; k++) {
					float hx = cornerX(r, k), hy = cornerY(r, k);
					boolean on = m == sizing && k == sizeCorner;
					boolean near = ui.hover(hx - 8, hy - 8, 16, 16);
					float hs = on || near ? 9 : 7;
					c.fill(hx - hs / 2, hy - hs / 2, hs, hs, Theme.alpha(0xFFFFFFFF, a));
					c.fill(hx - hs / 2 + 1.5f, hy - hs / 2 + 1.5f, hs - 3, hs - 3, Theme.alpha(on ? Theme.ACCENT : 0xFF09090B, a));
				}
				if (m == hover || m == drag || m == sizing) {
					String size = Math.round(m.scale.value * 100) + "%";
					float sw = c.textWidth(Fonts.MEDIUM, 10, size) + 12;
					float sx = r[0] + r[2] + 3 - sw, sy = r[1] + r[3] + 6;
					if (sy + 16 > H) {
						sy = r[1] - 22;
					}
					if (t[1] > r[1] && sy < r[1]) {
						sy = r[1] + r[3] + 28;
					}
					c.round(sx, sy, sw, 16, 8, 0xE60E0F13);
					c.text(Fonts.MEDIUM, 10, size, sx + 6, sy + (16 - c.lineHeight(Fonts.MEDIUM, 10)) / 2, Theme.TEXT_SECONDARY);
				}
			}
		}

		boolean under = ui.interactive;
		ui.interactive = under && !overPop; // the settings card sits on top of the hub and the drawer
		if (hub(ui, screen, in, shown.isEmpty())) {
			ui.interactive = under;
			return;
		}
		if (dt > 0.01f) {
			drawer(ui, dt, W, H);
		}
		notice(ui, W, H);
		ui.interactive = under;
		if (popover != null) {
			int pi = shown.indexOf(popover);
			if (pi < 0) {
				popover = null;
			} else {
				drawPopover(ui, popover, rects.get(pi), W, H);
			}
		}
		volumeBubble(ui, W, H);
	}

	private void volumeBubble(Ui ui, float W, float H) {
		Voice.Member m = volPick == null ? null : Voice.member(volPick);
		if (m == null) {
			volPick = null;
			volRect[2] = 0;
			return;
		}
		Canvas c = ui.c;
		float w = 300, h = 66, x = volX + w > W - 8 ? volX - w - 20 : volX, y = Math.max(8, Math.min(H - h - 8, volY));
		x = Math.max(8, x);
		volRect[0] = x;
		volRect[1] = y;
		volRect[2] = w;
		volRect[3] = h;
		if (ui.pressed && !ui.hover(x, y, w, h)) {
			volPick = null;
			return;
		}
		c.shadow(x, y + 3, w, h, 12, 18, 0x88000000);
		c.round(x, y, w, h, 12, 0xF808090C);
		c.outline(x, y, w, h, 12, 1, Theme.HAIRLINE_STRONG);
		c.text(Fonts.SEMIBOLD, 11, "PLAYER VOLUME", x + 14, y + 10, Theme.TEXT_MUTED);
		if (ui.iconButton("hud:vol:close", x + w - 32, y + 6, 22, Theme.I_X, null)) {
			volPick = null;
			return;
		}
		String name = m.ign != null ? m.ign : m.name;
		xyz.nativelaunch.ui.mod.VoiceModules.volumeRow(ui, "hud:vol:" + m.userId, m.userId, name, m.skin, Voice.speaking(m), x + 14, y + 28, w - 28);
	}

	// ── hub ──────────────────────────────────────────────────────────────

	/** The middle of the screen: the Native mark, the way into the settings and a row of shortcuts. Returns true when it left the screen. */
	private boolean hub(Ui ui, Object screen, float in, boolean empty) {
		Canvas c = ui.c;
		float W = c.width();
		float x0 = hubRect[0], y0 = hubRect[1], hw = hubRect[2];
		float a = in;
		if (logo == null) {
			logo = Image.resource("/assets/native/ui/logo.png");
		}
		if (logo != null) {
			c.stamp("hud:logo", logo, W / 2 - 28, y0, 56, 56, true, Theme.alpha(0xFFFFFFFF, a));
		}
		// the big button
		float bw = 236, bh = 42, bx = W / 2 - bw / 2, by = y0 + 70;
		boolean over = ui.hover(bx, by, bw, bh);
		boolean click = ui.clicked("hud:hub:mods", bx, by, bw, bh);
		float hv = ui.anim("hud:hub:mods#h", over, 16f);
		float pr = ui.anim("hud:hub:mods#p", ui.isPressing("hud:hub:mods"), 24f);
		c.shadow(bx, by + 3, bw, bh, bh / 2, 18, Theme.alpha(0x99000000, a));
		c.round(bx, by, bw, bh, bh / 2, Theme.alpha(Theme.mix(Theme.mix(0xFFF4F4F5, 0xFFFFFFFF, hv), 0xFFD4D4D8, pr), a));
		String label = "NATIVE MODS";
		float fs = 12.5f, tw = c.textWidth(Fonts.SEMIBOLD, fs, label);
		float tx = bx + (bw - tw - 24) / 2;
		c.icon(Theme.I_NAV_MODS, 15, tx + 7, by + bh / 2, Theme.alpha(Theme.SOLID_FG, a));
		c.text(Fonts.SEMIBOLD, fs, label, tx + 24, by + (bh - c.lineHeight(Fonts.SEMIBOLD, fs)) / 2, Theme.alpha(Theme.SOLID_FG, a));
		if (over) {
			ui.cursorHand = true;
		}
		if (click) {
			openMenu(screen, 0); // the Mods page
			return true;
		}
		// round shortcuts
		int[] icons = {Theme.I_PLUS, Theme.I_SETTINGS, Theme.I_SHIRT, Theme.I_MESSAGE, Theme.I_RESET};
		String[] tips = {"HUD modules", "Settings", "Cosmetics", "Relay chat", "Reset layout"};
		float d = 42, gap = 10, rowW = icons.length * d + (icons.length - 1) * gap;
		float rx = W / 2 - rowW / 2, ry = by + bh + 16;
		for (int i = 0; i < icons.length; i++) {
			float cx = rx + i * (d + gap);
			boolean on = i == 0 && drawerOpen;
			if (round(ui, "hud:hub:" + i, cx, ry, d, icons[i], tips[i], on, a)) {
				switch (i) {
					case 0:
						drawerOpen = !drawerOpen;
						break;
					case 1:
						openMenu(screen, 2);
						return true;
					case 2:
						openMenu(screen, 1);
						return true;
					case 3:
						Modules.save();
						UiRuntime.openRelay(UiRuntime.mc().hostParent(screen));
						return true;
					default:
						Modules.resetPositions();
						say(ui, "Layout reset", null);
						break;
				}
			}
		}
		if (empty) {
			String t = "No HUD modules on screen";
			String b = "Press + to add some.";
			float ty = ry + d + 22;
			c.text(Fonts.SEMIBOLD, 13, t, W / 2 - c.textWidth(Fonts.SEMIBOLD, 13, t) / 2, ty, Theme.alpha(Theme.TEXT_STRONG, a));
			c.text(Fonts.REGULAR, 11.5f, b, W / 2 - c.textWidth(Fonts.REGULAR, 11.5f, b) / 2, ty + 20, Theme.alpha(Theme.TEXT_SECONDARY, a));
		}
		return false;
	}

	private boolean round(Ui ui, String id, float x, float y, float d, int icon, String tip, boolean on, float alpha) {
		Canvas c = ui.c;
		boolean over = ui.hover(x, y, d, d);
		boolean click = ui.clicked(id, x, y, d, d);
		float hv = ui.anim(id + "#h", over || on, 16f);
		float pr = ui.anim(id + "#p", ui.isPressing(id), 24f);
		c.shadow(x, y + 2, d, d, d / 2, 12, Theme.alpha(0x88000000, alpha));
		c.round(x, y, d, d, d / 2, Theme.alpha(Theme.mix(Theme.mix(0xF0111317, 0xFFF4F4F5, hv), 0xFFD4D4D8, pr), alpha));
		if (hv < 0.98f) {
			c.outline(x, y, d, d, d / 2, 1, Theme.alpha(Theme.mix(0x33FFFFFF, 0x00FFFFFF, hv), alpha));
		}
		c.icon(icon, 17, x + d / 2, y + d / 2, Theme.alpha(Theme.mix(0xFFE4E4E7, Theme.SOLID_FG, hv), alpha));
		if (over) {
			ui.cursorHand = true;
			ui.tip(tip);
		}
		return click;
	}

	private void badge(Ui ui, String id, float x, float y, int color, int icon, boolean on) {
		Canvas c = ui.c;
		boolean over = ui.hover(x, y, 18, 18);
		float hv = ui.anim(id + "#h", over || on, 18f);
		float k = 18 + hv * 2;
		float bx = x - (k - 18) / 2, by = y - (k - 18) / 2;
		c.round(bx, by, k, k, k / 2, Theme.mix(color, Theme.mix(color, 0xFFFFFFFF, 0.22f), hv));
		c.outline(bx, by, k, k, k / 2, 1, 0x40FFFFFF);
		c.icon(icon, 11, x + 9, y + 9, 0xFFFFFFFF);
		if (over) {
			ui.tip(icon == Theme.I_SETTINGS ? "Settings" : "Remove from screen");
		}
	}

	// ── drawer: every HUD module, switch them on and off ─────────────────

	private void drawer(Ui ui, float t, float W, float H) {
		Canvas c = ui.c;
		float x = drawerRect[0], y = drawerRect[1], w = drawerRect[2], h = drawerRect[3];
		// grab the header to move the drawer anywhere on the screen
		boolean overHead = ui.hover(x, y, w - 60, 58);
		ui.clicked("hud:drawer:grab", x, y, w - 60, 58);
		if (ui.isPressing("hud:drawer:grab")) {
			if (ui.pressed || !drawerMoved) {
				grabDX = ui.mx - x;
				grabDY = ui.my - y;
			}
			drawerMoved = true;
			drawerX = Math.max(0, Math.min(W - w, ui.mx - grabDX));
			drawerY = Math.max(0, Math.min(H - h, ui.my - grabDY));
			x = drawerX;
			y = drawerY;
			drawerRect[0] = x;
			drawerRect[1] = y;
		}
		if (overHead || ui.isPressing("hud:drawer:grab")) {
			ui.cursorHand = true;
		}
		c.pushAlpha(t);
		c.shadow(x, y + 4, w, h, 14, 26, 0xAA000000);
		c.round(x, y, w, h, 14, 0xF4090A0D);
		c.outline(x, y, w, h, 14, 1, Theme.HAIRLINE_STRONG);
		int on = 0;
		for (HudModule m : Modules.hud()) {
			on += m.enabled ? 1 : 0;
		}
		c.text(Fonts.SEMIBOLD, 14.5f, "HUD modules", x + 18, y + 16, Theme.TEXT_STRONG);
		c.text(Fonts.REGULAR, 11, on + " of " + Modules.hud().size() + " on screen", x + 18, y + 36, Theme.TEXT_MUTED);
		for (int gx = 0; gx < 2; gx++) { // a grip: the header drags the drawer
			for (int gy = 0; gy < 3; gy++) {
				c.circle(x + w - 18 - 26 - 18 + gx * 5, y + 20 + gy * 5, 1.1f, Theme.alpha(0xFFFFFFFF, overHead ? 0.55f : 0.3f));
			}
		}
		if (ui.iconButton("hud:drawer:close", x + w - 18 - 26, y + 14, 26, Theme.I_X, null)) {
			drawerOpen = false;
			c.popAlpha();
			return;
		}
		float ly = y + 62, lh = y + h - 12 - ly, rowH = 44;
		float total = Modules.hud().size() * rowH;
		float maxScroll = Math.max(0, total - lh);
		if (ui.hover(x, y, w, h) && ui.scroll != 0) {
			drawerScroll -= ui.scroll * 40;
			ui.scroll = 0;
		}
		drawerScroll = Math.max(0, Math.min(maxScroll, drawerScroll));
		c.fill(x + 14, ly - 4, w - 28, 1, Theme.HAIRLINE);
		c.pushClip(x, ly, w, lh);
		float ry = ly - drawerScroll;
		for (HudModule m : Modules.hud()) {
			if (ry + rowH > ly && ry < ly + lh) {
				boolean over = ui.hover(x + 8, ry, w - 16 - 52, rowH - 4) && ui.hover(x, ly, w, lh);
				boolean click = ui.clicked("hud:drawer:row:" + m.id, x + 8, ry, w - 16 - 52, rowH - 4);
				float hv = ui.anim("hud:drawer:row:" + m.id + "#h", over, 16f);
				c.round(x + 8, ry, w - 16, rowH - 4, 10, Theme.alpha(Theme.SUBTLE_HOVER, hv));
				c.round(x + 16, ry + 6, 28, 28, 8, m.enabled ? 0xFFF4F4F5 : Theme.SUBTLE_HOVER);
				c.icon(m.icon, 14, x + 30, ry + 20, m.enabled ? Theme.SOLID_FG : Theme.TEXT_SECONDARY);
				c.text(Fonts.MEDIUM, 12.5f, c.ellipsize(Fonts.MEDIUM, 12.5f, m.name, w - 16 - 52 - 44 - 8), x + 54, ry + (rowH - 4 - c.lineHeight(Fonts.MEDIUM, 12.5f)) / 2,
						m.enabled ? Theme.TEXT_STRONG : Theme.TEXT_SECONDARY);
				if (over) {
					ui.cursorHand = true;
				}
				boolean tg = ui.toggle("hud:drawer:tg:" + m.id, x + w - 14 - 34, ry + 9, m.enabled);
				if (click || tg) {
					m.setEnabled(!m.enabled);
					Modules.changed();
					if (!m.enabled && popover == m) {
						popover = null;
					}
				}
			}
			ry += rowH;
		}
		c.popClip();
		c.popAlpha();
	}

	// ── notices ───────────────────────────────────────────────────

	private void notice(Ui ui, float W, float H) {
		Canvas c = ui.c;
		noticeRect[2] = 0;
		if (notice == null) {
			return;
		}
		float age = (ui.now - noticeAt) / 1000f;
		if (age > 5f) {
			notice = null;
			removed = null;
			return;
		}
		float t = Math.min(1f, age * 6f) * Math.min(1f, (5f - age) * 4f);
		float tw = c.textWidth(Fonts.MEDIUM, 12, notice);
		boolean undo = removed != null;
		float uw = undo ? c.textWidth(Fonts.SEMIBOLD, 12, "Undo") + 20 : 0;
		float w = tw + 32 + (undo ? uw + 6 : 0), h = 38, x = (W - w) / 2, y = H - 76 + (1 - t) * 10;
		noticeRect[0] = x;
		noticeRect[1] = y;
		noticeRect[2] = w;
		noticeRect[3] = h;
		c.pushAlpha(t);
		c.shadow(x, y + 3, w, h, h / 2, 18, 0x99000000);
		c.round(x, y, w, h, h / 2, 0xF40B0C10);
		c.outline(x, y, w, h, h / 2, 1, Theme.HAIRLINE_STRONG);
		c.text(Fonts.MEDIUM, 12, notice, x + 16, y + (h - c.lineHeight(Fonts.MEDIUM, 12)) / 2, Theme.TEXT_STRONG);
		if (undo) {
			float ux = x + w - uw - 6, uy = y + 6;
			boolean over = ui.hover(ux, uy, uw, h - 12);
			boolean click = ui.clicked("hud:notice:undo", ux, uy, uw, h - 12);
			float hv = ui.anim("hud:notice:undo#h", over, 16f);
			c.round(ux, uy, uw, h - 12, (h - 12) / 2, Theme.mix(0xFFE4E4E7, 0xFFFFFFFF, hv));
			c.text(Fonts.SEMIBOLD, 12, "Undo", ux + 10, uy + (h - 12 - c.lineHeight(Fonts.SEMIBOLD, 12)) / 2, Theme.SOLID_FG);
			if (over) {
				ui.cursorHand = true;
			}
			if (click) {
				removed.setEnabled(true);
				Modules.changed();
				notice = null;
				removed = null;
			}
		}
		c.popAlpha();
	}

	private static float cornerX(float[] r, int k) {
		return k == 0 || k == 3 ? r[0] - 3 : r[0] + r[2] + 3;
	}

	private static float cornerY(float[] r, int k) {
		return k == 0 || k == 1 ? r[1] - 3 : r[1] + r[3] + 3;
	}

	private void openMenu(Object screen, int tab) {
		McBridge mc = UiRuntime.mc();
		Modules.save();
		UiRuntime.openMenu(mc.hostParent(screen), tab);
	}

	private void drawPopover(Ui ui, HudModule m, float[] r, float W, float H) {
		Canvas c = ui.c;
		List<Setting> all = m.shownAll();
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
