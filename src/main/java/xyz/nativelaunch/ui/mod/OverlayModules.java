package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.UiRuntime;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;

/** Native versions of vanilla HUD parts you can move and resize: the scoreboard sidebar and the boss bars. */
final class OverlayModules {
	private OverlayModules() {
	}

	static final String[] LOOKS = {"Vanilla", "Native"};
	private static final float[] GUI = new float[3];

	/** Logical (Native UI) px per vanilla GUI px, plus the GUI size; false when unknown. */
	static boolean gui() {
		if (!UiRuntime.mc().guiSize(GUI) || GUI[0] <= 0) {
			return false;
		}
		GUI[2] = GUI[2] / UiRuntime.uiScale();
		return true;
	}

	static float richWidth(Canvas c, int face, float size, Rich r) {
		float w = 0;
		for (int i = 0; i < r.n; i++) {
			w += c.textWidth(face, size, r.text[i]);
		}
		return w;
	}

	/** A spacer row: servers pad their sidebar with blank lines. */
	static boolean blank(Rich r) {
		for (int i = 0; i < r.n; i++) {
			if (r.text[i] != null && !r.text[i].trim().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	// ── scoreboard ───────────────────────────────────────────────────────

	static final class Scoreboard extends HudModule {
		/** Native look: card padding, extra leading per row, the height of a blank server row, title-to-rows gap. */
		private static final float PAD = 9f, ROW_AIR = 3.5f, GAP_ROW = 7f, TITLE_GAP = 5f;

		final Setting.Choice look = add(new Setting.Choice("look", "Look", 0, LOOKS));
		final Setting.Bool numbers = add(new Setting.Bool("numbers", "Show score numbers", true));
		final Setting.Bool title = add(new Setting.Bool("title", "Show title", true));
		final Setting.Bool colors = add(new Setting.Bool("colors", "Server colours", true));
		private final Overlays.Sidebar live = new Overlays.Sidebar(), demo = new Overlays.Sidebar();
		private final float[] size = new float[2];
		private long readAt;
		private boolean ok;

		Scoreboard() {
			super("scoreboard", "Scoreboard", "The server sidebar. Move it, resize it, hide the red numbers.", Theme.I_LIST, true, 1f, 0.5f);
			opacity.value = 40;
			demo.has = true;
			demo.title.set("NATIVE", 0xFFFFFF55);
			String[] n = {"", "Players: 128", "Map: Lighthouse", "", "Kills: 7", "Coins: 2,450", "", "playnative.fun"};
			int[] col = {0, 0xFFFFFFFF, 0xFFFFFFFF, 0, 0xFF55FF55, 0xFFFFAA00, 0, 0xFFFFFF55};
			demo.count = n.length;
			for (int i = 0; i < n.length; i++) {
				demo.names[i].set(n[i].isEmpty() ? " " : n[i], col[i]);
				demo.scores[i].set(String.valueOf(n.length - i), 0xFFFF5555);
			}
		}

		@Override
		public void frame(Game g) {
			long now = System.currentTimeMillis();
			if (now - readAt < 100) { // a sidebar changes a few times a second at most
				return;
			}
			readAt = now;
			ok = g.inWorld && UiRuntime.mc().sidebar(live);
			place();
		}

		private float boxW, boxH;

		private boolean vanilla() {
			return look.value == 0;
		}

		/** Vanilla look: works out where the game draws it and the move/scale that puts it in our box. */
		private void place() {
			Overlays.hideSidebar = ok && !vanilla();
			if (!vanilla() || !ok || !live.has || live.count == 0 || !gui()) {
				Overlays.moveSidebar = false;
				return;
			}
			float sw = GUI[0], sh = GUI[1], L = GUI[2], s = scale.value;
			int m = live.count * 9;
			float vx = sw - live.vanillaW - 5, vy = (int) (sh / 2) + m / 3 - m - 10;
			float vw = live.vanillaW + 4, vh = m + 10;
			boxW = vw * L * s;
			boxH = vh * L * s;
			float x = pos(fx, sw * L, boxW), y = pos(fy, sh * L, boxH);
			Overlays.sidebarX = x / L - vx * s;
			Overlays.sidebarY = y / L - vy * s;
			Overlays.sidebarS = s;
			Overlays.moveSidebar = true;
		}

		@Override
		protected void onDisable() {
			Overlays.hideSidebar = false;
			Overlays.moveSidebar = false;
		}

		private boolean real(Game g) {
			return vanilla() && !g.sample && visible(g) && Overlays.moveSidebar;
		}

		@Override
		public boolean visible(Game g) {
			return ok && live.has && live.count > 0;
		}

		private Overlays.Sidebar data(Game g) {
			return g.sample || !visible(g) ? demo : live;
		}

		@Override
		public float[] measure(Canvas c, Game g) {
			if (real(g)) {
				place(); // the box follows the module live while it is dragged / resized
				size[0] = boxW;
				size[1] = boxH;
				return size;
			}
			Overlays.Sidebar d = data(g);
			float s = scale.value, fs = 11.5f * s;
			float lh = c.lineHeight(Fonts.MEDIUM, fs) + ROW_AIR * s;
			float w = title.value ? richWidth(c, Fonts.SEMIBOLD, fs, d.title) + 8 * s : 0;
			float body = 0;
			for (int i = 0; i < d.count; i++) {
				if (blank(d.names[i])) {
					body += GAP_ROW * s;
					continue;
				}
				float row = richWidth(c, Fonts.MEDIUM, fs, d.names[i]);
				if (numbers.value) {
					row += 14 * s + chipWidth(c, fs, d.scores[i], s);
				}
				w = Math.max(w, row);
				body += lh;
			}
			size[0] = w + 2 * PAD * s;
			size[1] = body + PAD * s + (title.value ? titleHeight(c, fs, s) + TITLE_GAP * s : PAD * s);
			return size;
		}

		@Override
		public void paint(Ui ui, float x, float y, Game g) {
			if (real(g)) {
				return; // the game draws it, already moved into place
			}
			Canvas c = ui.c;
			Overlays.Sidebar d = data(g);
			float[] sz = measure(c, g);
			float s = scale.value, fs = 11.5f * s;
			float lh = c.lineHeight(Fonts.MEDIUM, fs) + ROW_AIR * s;
			boolean card = style.value != STYLE_TEXT;
			float a = opacity.value / 100f;
			if (card) {
				background(c, x, y, sz[0], sz[1], s);
			}
			float pad = PAD * s;
			float ty = y + pad;
			if (title.value) {
				float th = titleHeight(c, fs, s);
				if (card && a > 0.01f) {
					// a slightly brighter cap behind the title, with the card's own corner radius
					c.pushClip(x, y, sz[0], th);
					c.round(x, y, sz[0], th + 6 * s, 6 * s, Theme.alpha(0xFFFFFFFF, 0.05f * Math.min(1f, a * 1.6f)));
					c.popClip();
				}
				float tw = richWidth(c, Fonts.SEMIBOLD, fs, d.title);
				draw(c, Fonts.SEMIBOLD, fs, d.title, x + (sz[0] - tw) / 2, y + pad);
				c.fill(x + pad, y + th - 1, sz[0] - 2 * pad, 1, Theme.alpha(color.value, 0.14f));
				ty = y + th + TITLE_GAP * s;
			}
			for (int i = 0; i < d.count; i++) {
				if (blank(d.names[i])) {
					// blank server padding becomes a hairline, so the sidebar keeps its grouping without the holes
					c.fill(x + pad, ty + GAP_ROW * s / 2, sz[0] - 2 * pad, 1, Theme.alpha(color.value, 0.10f));
					ty += GAP_ROW * s;
					continue;
				}
				draw(c, Fonts.MEDIUM, fs, d.names[i], x + pad, ty);
				if (numbers.value) {
					float cw = chipWidth(c, fs, d.scores[i], s);
					float nw = richWidth(c, Fonts.MEDIUM, fs, d.scores[i]);
					float cx = x + sz[0] - pad - cw, cy = ty - 1 * s;
					float chH = c.lineHeight(Fonts.MEDIUM, fs) + 2 * s;
					if (card && a > 0.01f) {
						c.round(cx, cy, cw, chH, chH / 2, Theme.alpha(0xFFFFFFFF, 0.07f));
					}
					draw(c, Fonts.MEDIUM, fs, d.scores[i], cx + (cw - nw) / 2, ty);
				}
				ty += lh;
			}
		}

		/** Score chip: the number plus its padding, never narrower than a two-digit one. */
		private float chipWidth(Canvas c, float fs, Rich score, float s) {
			return Math.max(c.textWidth(Fonts.MEDIUM, fs, "00") + 12 * s, richWidth(c, Fonts.MEDIUM, fs, score) + 12 * s);
		}

		private float titleHeight(Canvas c, float fs, float s) {
			return c.lineHeight(Fonts.SEMIBOLD, fs) + 2 * PAD * s;
		}

		private void draw(Canvas c, int face, float fs, Rich r, float x, float y) {
			for (int i = 0; i < r.n; i++) {
				int col = colors.value ? r.color[i] : color.value;
				if (colors.value && (col & 0xFFFFFF) == 0xFFFFFF) {
					col = color.value; // plain white text follows the chosen text colour
				}
				x = text(c, face, fs, r.text[i], x, y, col);
			}
		}
	}

	// ── boss bars ────────────────────────────────────────────────────────

	static final class BossBars extends HudModule {
		static final int[] COLORS = {0xFFEC4899, 0xFF38BDF8, 0xFFEF4444, 0xFF22C55E, 0xFFEAB308, 0xFFA855F7, 0xFFE5E5E5};
		final Setting.Choice look = add(new Setting.Choice("look", "Look", 0, LOOKS));
		final Setting.Bool showName = add(new Setting.Bool("name", "Show name", true));
		final Setting.Bool showBar = add(new Setting.Bool("bar", "Show bar", true));
		final Setting.Bool percent = add(new Setting.Bool("percent", "Show percent", false));
		final Setting.Num width = add(new Setting.Num("width", "Bar width", 182, 100, 320, 2, "px"));
		private final Overlays.Bars live = new Overlays.Bars(), demo = new Overlays.Bars();
		private final float[] shown = new float[8];
		private final Object[] shownIds = new Object[8];
		private final float[] size = new float[2];
		private long readAt, lastPaint;
		private boolean ok;

		BossBars() {
			super("bossbar", "Boss Bar", "Boss and event bars. Move them, resize them, restyle the bar.", Theme.I_ACTIVITY, true, 0.5f, 0f);
			style.value = STYLE_TEXT;
			demo.count = 1;
			demo.names[0].set("Ender Dragon", 0xFFFFFFFF);
			demo.progress[0] = 0.62f;
			demo.color[0] = 0;
		}

		@Override
		public void frame(Game g) {
			long now = System.currentTimeMillis();
			if (now - readAt < 50) {
				return;
			}
			readAt = now;
			ok = g.inWorld && UiRuntime.mc().bossBars(live);
			place();
		}

		private float boxW, boxH;

		private boolean vanilla() {
			return look.value == 0;
		}

		private void place() {
			Overlays.hideBossBars = ok && !vanilla();
			if (!vanilla() || !ok || live.count == 0 || !gui()) {
				Overlays.moveBossBars = false;
				return;
			}
			float sw = GUI[0], sh = GUI[1], L = GUI[2], s = scale.value;
			float w = 182;
			int shown = 0;
			for (int i = 0, j = 12; i < live.count; i++) {
				w = Math.max(w, live.nameW[i]);
				shown++;
				j += 19;
				if (j >= sh / 3) {
					break;
				}
			}
			float vx = (int) (sw / 2) - w / 2, vy = 2, vh = shown * 19 - 4;
			boxW = w * L * s;
			boxH = vh * L * s;
			float x = pos(fx, sw * L, boxW), y = pos(fy, sh * L, boxH);
			Overlays.bossX = x / L - vx * s;
			Overlays.bossY = y / L - vy * s;
			Overlays.bossS = s;
			Overlays.moveBossBars = true;
		}

		@Override
		protected void onDisable() {
			Overlays.hideBossBars = false;
			Overlays.moveBossBars = false;
		}

		private boolean real(Game g) {
			return vanilla() && !g.sample && visible(g) && Overlays.moveBossBars;
		}

		@Override
		public boolean visible(Game g) {
			return ok && live.count > 0;
		}

		private Overlays.Bars data(Game g) {
			return g.sample || !visible(g) ? demo : live;
		}

		private float rowH(Canvas c, float s) {
			float h = 0;
			if (showName.value) {
				h += c.lineHeight(Fonts.SEMIBOLD, 11.5f * s) + 2 * s;
			}
			if (showBar.value) {
				h += 6 * s;
			}
			return h + 6 * s;
		}

		@Override
		public float[] measure(Canvas c, Game g) {
			if (real(g)) {
				place();
				size[0] = boxW;
				size[1] = boxH;
				return size;
			}
			Overlays.Bars d = data(g);
			float s = scale.value;
			float w = width.value * s;
			if (showName.value) {
				for (int i = 0; i < d.count; i++) {
					w = Math.max(w, richWidth(c, Fonts.SEMIBOLD, 11.5f * s, d.names[i]) + (percent.value ? 40 * s : 0));
				}
			}
			boolean card = style.value == STYLE_CARD;
			size[0] = w + (card ? 16 * s : 0);
			size[1] = Math.max(1, d.count) * rowH(c, s) - 6 * s + (card ? 12 * s : 0);
			return size;
		}

		@Override
		public void paint(Ui ui, float x, float y, Game g) {
			if (real(g)) {
				return;
			}
			Canvas c = ui.c;
			Overlays.Bars d = data(g);
			float[] sz = measure(c, g);
			float s = scale.value, fs = 11.5f * s;
			boolean card = style.value == STYLE_CARD;
			if (card) {
				background(c, x, y, sz[0], sz[1], s);
			}
			long now = System.nanoTime();
			float dt = lastPaint == 0 ? 0.016f : Math.min(0.1f, (now - lastPaint) / 1e9f);
			lastPaint = now;
			float k = 1f - (float) Math.exp(-10 * dt);
			float ix = x + (card ? 8 * s : 0), iw = sz[0] - (card ? 16 * s : 0), ty = y + (card ? 6 * s : 0);
			for (int i = 0; i < d.count; i++) {
				if (shownIds[i] != d.ids[i]) {
					shownIds[i] = d.ids[i];
					shown[i] = d.progress[i];
				}
				shown[i] += (d.progress[i] - shown[i]) * k;
				if (showName.value) {
					Rich r = d.names[i];
					float nw = richWidth(c, Fonts.SEMIBOLD, fs, r);
					String pc = percent.value ? "  " + Math.round(d.progress[i] * 100) + "%" : null;
					float pw = pc == null ? 0 : c.textWidth(Fonts.MEDIUM, fs, pc);
					float tx = ix + (iw - nw - pw) / 2;
					for (int j = 0; j < r.n; j++) {
						tx = text(c, Fonts.SEMIBOLD, fs, r.text[j], tx, ty, (r.color[j] & 0xFFFFFF) == 0xFFFFFF ? color.value : r.color[j]);
					}
					if (pc != null) {
						text(c, Fonts.MEDIUM, fs, pc, tx, ty, Theme.alpha(color.value, 0.62f));
					}
					ty += c.lineHeight(Fonts.SEMIBOLD, fs) + 2 * s;
				}
				if (showBar.value) {
					int col = COLORS[Math.max(0, Math.min(COLORS.length - 1, d.color[i]))];
					float bh = 5 * s;
					c.round(ix, ty, iw, bh, bh / 2, 0x66000000);
					c.outline(ix, ty, iw, bh, bh / 2, 1, Theme.HAIRLINE);
					float p = Math.max(0, Math.min(1, shown[i]));
					if (p > 0.005f) {
						c.round(ix, ty, Math.max(bh, iw * p), bh, bh / 2, col);
					}
					ty += 6 * s;
				}
				ty += 6 * s;
			}
		}
	}
}
