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
	/** Effect flags the game bridge reports per entity (Game.Tag.fx). */
	static final int POISON = 1, WITHER = 2, REGEN = 4;
	private static final float RANGE = 32f;

	final Setting.Bool players = add(new Setting.Bool("players", "Players", true));
	final Setting.Bool mobs = add(new Setting.Bool("mobs", "Mobs and animals", true));
	final Setting.Bool self = add(new Setting.Bool("self", "Yourself (third person)", false));
	final Setting.Choice display = add(new Setting.Choice("display", "Show as", 0, "Hearts", "Number"));
	final Setting.Num size = add(new Setting.Num("size", "Size", 11, 6, 24, 1, " px"));

	HealthIndicator() {
		super("healthindicator", "Health Indicator", "Hearts above players and mobs, so you can see how hurt they are.", VISUAL, Theme.I_HEART, false);
	}

	@Override
	public int needs() {
		return Game.NEED_TAGS;
	}

	@Override
	public void frame(Game g) {
		g.tagKinds = (players.value ? PLAYER : 0) | (mobs.value ? HOSTILE | PASSIVE : 0) | (self.value ? SELF : 0);
		g.tagRange = RANGE;
		g.tagSight = true; // never through walls
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
		float W = c.width(), H = c.height();
		for (int i = 0; i < g.tagCount; i++) {
			Game.Tag t = g.tags[i];
			// fade out over the last quarter of the range
			float a = Math.max(0f, Math.min(1f, (RANGE - t.depth) / (RANGE * 0.25f)));
			if (a <= 0.02f) {
				continue;
			}
			float x = t.nx * W, y = t.ny * H;
			if (x < -80 || x > W + 80 || y < -40 || y > H + 40) {
				continue;
			}
			if (a < 1f) {
				c.pushAlpha(a);
			}
			draw(c, x, y, size.value, t.health, t.max, t.absorb, t.fx);
			if (a < 1f) {
				c.popAlpha();
			}
		}
	}

	/** One indicator with its bottom centre at (x, y); {@code ps} is the heart size in UI px. */
	private void draw(Canvas c, float x, float y, float ps, float hp, float max, float absorb, int fx) {
		String kind = (fx & WITHER) != 0 ? "wither" : (fx & POISON) != 0 ? "poison" : "";
		if (display.value == 1) {
			String num = Status.hp(hp) + " / " + Status.hp(max) + (absorb > 0.05f ? " +" + Status.hp(absorb) : "");
			float nf = ps * 1.15f, gap = ps * 0.3f;
			float tw = c.textWidth(Fonts.SEMIBOLD, nf, num), w = ps + gap + tw;
			float x0 = x - w / 2, ty = y - ps;
			heart(c, kind.isEmpty() ? "heart_full" : "heart_" + kind + "_full", x0, ty, ps, 0xFFEF4444);
			float lh = c.lineHeight(Fonts.SEMIBOLD, nf);
			float tx = x0 + ps + gap, tyy = ty + (ps - lh) / 2;
			int col = (fx & WITHER) != 0 ? 0xFFA1A1AA : (fx & POISON) != 0 ? 0xFF84CC16 : 0xFFFFFFFF;
			c.text(Fonts.SEMIBOLD, nf, num, tx + 0.8f, tyy + 0.8f, 0xB0000000);
			c.text(Fonts.SEMIBOLD, nf, num, tx, tyy, col);
			return;
		}
		// one row of at most 10 hearts for everyone: a big mob's hearts just stand for more health each
		float k = max > 20f ? 20f / max : 1f;
		int hearts = Math.max(1, (int) Math.ceil(max * k / 2f));
		float pitch = ps * 0.88f, rowH = ps * 0.9f;
		float w = pitch * (hearts - 1) + ps, x0 = x - w / 2, ry = y - ps;
		float h = hp * k;
		int jump = (fx & REGEN) != 0 ? (int) ((System.currentTimeMillis() / 50) % (hearts + 5)) : -1; // like the game: a heart hops while regenerating
		for (int i = 0; i < hearts; i++) {
			float hx = x0 + i * pitch, hy = i == jump ? ry - ps * 0.2f : ry;
			heart(c, "heart_container", hx, hy, ps, 0xFF3F3F46);
			if (h >= 2 * i + 2) {
				heart(c, kind.isEmpty() ? "heart_full" : "heart_" + kind + "_full", hx, hy, ps, 0xFFEF4444);
			} else if (h > 2 * i) {
				heart(c, kind.isEmpty() ? "heart_half" : "heart_" + kind + "_half", hx, hy, ps, 0xFFEF4444);
			}
		}
		if (absorb > 0.05f) {
			// extra (golden) hearts get their own row above, like in the game
			float ka = absorb > 20f ? 20f / absorb : 1f, ah = absorb * ka;
			int n = Math.max(1, (int) Math.ceil(ah / 2f));
			float aw = pitch * (n - 1) + ps, ax0 = x - aw / 2, ay = ry - rowH;
			for (int i = 0; i < n; i++) {
				float hx = ax0 + i * pitch;
				heart(c, "heart_container", hx, ay, ps, 0xFF3F3F46);
				heart(c, ah >= 2 * i + 2 ? "heart_absorb_full" : "heart_absorb_half", hx, ay, ps, 0xFFF59E0B);
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
		draw(c, x + w * 0.5f, y + h * 0.4f, ps, 20f, 20f, 6f, 0);
		draw(c, x + w * 0.5f, y + h * 0.7f, ps, 13f, 20f, 0f, POISON);
		c.popClip();
		return true;
	}
}
