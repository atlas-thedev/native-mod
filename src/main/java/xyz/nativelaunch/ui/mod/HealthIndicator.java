package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.ui.gfx.Image;

/**
 * Hearts floating over every player and mob (like the Health Indicator mods): the game bridge finds the living things in
 * range and projects the spot above their heads onto the screen, this module draws the hearts there with the game's own
 * heart sprites.
 */
final class HealthIndicator extends Module {
	static final int PLAYER = 1, HOSTILE = 2, PASSIVE = 4, SELF = 8;

	final Setting.Bool players = add(new Setting.Bool("players", "Players", true));
	final Setting.Bool hostile = add(new Setting.Bool("hostile", "Hostile mobs", true));
	final Setting.Bool passive = add(new Setting.Bool("passive", "Other mobs and animals", true));
	final Setting.Bool self = add(new Setting.Bool("self", "Yourself (third person)", false));
	final Setting.Choice display = add(new Setting.Choice("display", "Show as", 0, "Hearts", "Bar", "Number"));
	final Setting.Num perRow = add(new Setting.Num("row", "Hearts per row", 10, 5, 20, 1, ""));
	final Setting.Bool numbers = add(new Setting.Bool("numbers", "Also show the number", false));
	final Setting.Bool hideFull = add(new Setting.Bool("hideFull", "Hide at full health", false));
	final Setting.Bool sight = add(new Setting.Bool("occlude", "Hide behind blocks", true));
	final Setting.Num range = add(new Setting.Num("range", "Range", 24, 8, 64, 2, " blocks"));
	final Setting.Bool fade = add(new Setting.Bool("fade", "Fade out with distance", true));
	final Setting.Num size = add(new Setting.Num("size", "Heart size", 11, 6, 24, 1, " px"));
	final Setting.Bool shrink = add(new Setting.Bool("shrink", "Smaller when far away", false));

	HealthIndicator() {
		super("healthindicator", "Health Indicator", "Hearts above players and mobs, so you can see how hurt they are.", VISUAL, Theme.I_HEART, false);
	}

	@Override
	protected boolean hidden(Setting s) {
		if (s == perRow) {
			return display.value != 0;
		}
		if (s == numbers) {
			return display.value == 2;
		}
		return false;
	}

	@Override
	public int needs() {
		return Game.NEED_TAGS;
	}

	@Override
	public void frame(Game g) {
		g.tagKinds = (players.value ? PLAYER : 0) | (hostile.value ? HOSTILE : 0) | (passive.value ? PASSIVE : 0) | (self.value ? SELF : 0);
		g.tagRange = range.value;
		g.tagSight = sight.value;
	}

	@Override
	public boolean overlays(Game g) {
		return g.hudAllowed && g.vitalsOk && g.tagCount > 0;
	}

	@Override
	public void overlay(Ui ui, Game g, boolean editing) {
		if (editing || g.sample) {
			return;
		}
		Canvas c = ui.c;
		float W = c.width(), H = c.height(), R = Math.max(1f, g.tagRange);
		for (int i = 0; i < g.tagCount; i++) {
			Game.Tag t = g.tags[i];
			if (hideFull.value && t.health >= t.max - 0.01f) {
				continue;
			}
			// the same size at any distance, unless "Smaller when far away" is on
			float px = shrink.value ? Math.min(size.value, Math.max(5f, 0.2f * t.perBlock * H)) : size.value;
			float a = 1f;
			if (fade.value) {
				a = Math.max(0f, Math.min(1f, (R - t.depth) / (R * 0.25f)));
				if (a <= 0.02f) {
					continue;
				}
			}
			float x = t.nx * W, y = t.ny * H;
			if (x < -80 || x > W + 80 || y < -40 || y > H + 40) {
				continue;
			}
			if (a < 1f) {
				c.pushAlpha(a);
			}
			draw(c, x, y, px, t.health, t.max);
			if (a < 1f) {
				c.popAlpha();
			}
		}
	}

	/** One indicator with its bottom centre at (x, y); {@code ps} is the heart size in UI px. */
	private void draw(Canvas c, float x, float y, float ps, float hp, float max) {
		int mode = display.value;
		String num = Status.hp(hp), maxNum = Status.hp(max);
		if (mode == 0 && max > 40f) {
			// a golem or a boss: still hearts like everyone else, just each one stands for more health (two rows at most)
			hp = hp * 40f / max;
			max = 40f;
		}
		if (mode == 2) {
			float nf = ps * 1.15f, gap = ps * 0.3f;
			float tw = c.textWidth(Fonts.SEMIBOLD, nf, num), w = ps + gap + tw;
			float x0 = x - w / 2, ty = y - ps;
			heart(c, "heart_full", x0, ty, ps, 0xFFEF4444);
			float lh = c.lineHeight(Fonts.SEMIBOLD, nf);
			float tx = x0 + ps + gap, tyy = ty + (ps - lh) / 2;
			c.text(Fonts.SEMIBOLD, nf, num, tx + 0.8f, tyy + 0.8f, 0xB0000000);
			c.text(Fonts.SEMIBOLD, nf, num, tx, tyy, 0xFFFFFFFF);
			return;
		}
		float bottom = y;
		if (numbers.value) {
			float nf = ps * 1.05f, lh = c.lineHeight(Fonts.SEMIBOLD, nf);
			String s = num + " / " + maxNum;
			float tx = x - c.textWidth(Fonts.SEMIBOLD, nf, s) / 2;
			c.text(Fonts.SEMIBOLD, nf, s, tx + 0.8f, bottom - lh + 0.8f, 0xB0000000);
			c.text(Fonts.SEMIBOLD, nf, s, tx, bottom - lh, 0xFFFFFFFF);
			bottom -= lh + ps * 0.15f;
		}
		if (mode == 1) {
			float w = ps * 8f, h = Math.max(3f, ps * 0.55f), f = Math.max(0f, Math.min(1f, hp / Math.max(1f, max)));
			float bx = x - w / 2, by = bottom - h;
			c.round(bx - 1, by - 1, w + 2, h + 2, (h + 2) / 2, 0xB0000000);
			c.round(bx, by, w, h, h / 2, 0xFF3B1D1D);
			if (f > 0) {
				c.round(bx, by, Math.max(h, w * f), h, h / 2, Status.wear(f));
			}
			return;
		}
		int hearts = Math.max(1, (int) Math.ceil(max / 2f)), per = Math.min((int) perRow.value, hearts);
		int rows = (hearts + per - 1) / per;
		float pitch = ps * 0.88f, w = pitch * (per - 1) + ps, x0 = x - w / 2;
		for (int r = 0; r < rows; r++) {
			float ry = bottom - ps - r * ps * 0.9f;
			for (int i = r * per; i < Math.min(hearts, (r + 1) * per); i++) {
				float hx = x0 + (i % per) * pitch;
				heart(c, "heart_container", hx, ry, ps, 0xFF3F3F46);
				if (hp >= 2 * i + 2) {
					heart(c, "heart_full", hx, ry, ps, 0xFFEF4444);
				} else if (hp > 2 * i) {
					heart(c, "heart_half", hx, ry, ps, 0xFFEF4444);
				}
			}
		}
	}

	private static void heart(Canvas c, String name, float x, float y, float size, int fallback) {
		Image img = Vanilla.pip(name);
		if (img != null) {
			Vanilla.draw(c, img, x, y, size, size, 0xFFFFFFFF);
		} else if (!name.endsWith("container")) {
			c.round(x + 1, y + 1, size - 2, size - 2, size * 0.3f, fallback);
		}
	}

	@Override
	public boolean preview(Ui ui, Game g, float x, float y, float w, float h) {
		Canvas c = ui.c;
		c.round(x, y, w, h, 10, 0xFF2B3A33);
		c.fill(x, y + h * 0.7f, w, h * 0.3f, 0xFF3F5A3A);
		c.pushClip(x, y, w, h);
		float ps = Math.min(13f, w / 16f);
		draw(c, x + w * 0.5f, y + h * 0.36f, ps, 20f, 20f);
		draw(c, x + w * 0.5f, y + h * 0.66f, ps, 13f, 20f);
		c.popClip();
		return true;
	}
}
