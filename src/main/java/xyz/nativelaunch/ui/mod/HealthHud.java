package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.health.DamageArc;
import xyz.nativelaunch.health.EntityRules;
import xyz.nativelaunch.health.HealthEvents;
import xyz.nativelaunch.health.HealthLayout;
import xyz.nativelaunch.health.HealthScene;
import xyz.nativelaunch.health.HealthSettings;
import xyz.nativelaunch.health.HealthSprites;
import xyz.nativelaunch.health.Projector;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.UiRuntime;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.ui.gfx.Image;

/**
 * Draws the health indicators on the Native HUD layer: every living thing worth one is projected from the world to the
 * screen and gets its hearts / armour / number drawn at a fixed size, so they look the same near, far and zoomed in.
 */
final class HealthHud {
	/** Most icons drawn in one frame (a crowd of bosses must not stall the HUD). */
	private static final int MAX_ICONS = 1800;
	/** Half a block of air between the head and the first row, plus the name tag when there is one. */
	private static final float ABOVE_HEAD = 0.5f, LABEL_LIFT = 9f * 1.15f * 0.025f;

	private final HealthScene scene = new HealthScene();
	private final Projector proj = new Projector();
	private final EntityRules.Facts facts = new EntityRules.Facts();
	private final HealthLayout layout = new HealthLayout();
	private final float[] pt = new float[3];
	private Image sheet;
	private int icons;
	private float alphaMul;

	private Image sheet() {
		if (sheet == null) {
			sheet = new Image(HealthSprites.sheetWidth(), HealthSprites.sheetHeight(), HealthSprites.sheet());
			sheet.linear = false; // pixel art stays crisp
		}
		return sheet;
	}

	void draw(Ui ui, HealthSettings s) {
		scene.reset();
		if (!UiRuntime.mc().health(scene) || !scene.ok) {
			return;
		}
		Canvas c = ui.c;
		proj.set(scene.camX, scene.camY, scene.camZ, scene.camYaw, scene.camPitch, scene.fov, c.width(), c.height());
		long now = HealthEvents.tick();
		if (scene.playerHurt && s.arc) {
			markArc();
		}
		if (!(s.hearts || s.armor || s.override)) {
			return;
		}
		int target = findTarget(s);
		icons = 0;
		alphaMul = Math.max(0, Math.min(100, s.opacity)) / 100f;
		if (alphaMul <= 0f) {
			return;
		}
		for (int i = 0; i < scene.count && icons < MAX_ICONS; i++) {
			HealthScene.Entry e = scene.entries[i];
			if (!e.valid) {
				continue;
			}
			double dx = e.x - scene.camX, dy = e.y - scene.camY, dz = e.z - scene.camZ;
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (e.self && dist < 1.2) {
				continue; // first person: that is us
			}
			boolean targeted = e.id == target;
			facts.valid = true;
			facts.player = e.player;
			facts.self = e.self;
			facts.hostile = e.hostile;
			facts.passive = e.passive;
			facts.health = e.health;
			facts.max = e.max;
			facts.absorption = e.absorb;
			facts.distance = dist;
			facts.recentlyHit = HealthEvents.HITS.active(e.id, now);
			facts.targeted = targeted;
			facts.listed = HealthModule.list().allows(e.type, e.player ? e.name : null);
			if (!EntityRules.show(facts, s)) {
				continue;
			}
			// like a solid object: hidden behind blocks, except the mob you are aiming at when "through walls" is on
			if (!e.visible && !(s.throughWalls && targeted && !e.player)) {
				continue;
			}
			drawEntity(c, s, e, dist, now);
		}
	}

	/** The nearest thing the crosshair is on (within reach), or -1. */
	private int findTarget(HealthSettings s) {
		if (!(s.lookingAt || s.throughWalls)) {
			return -1;
		}
		double range = s.lookingAt ? s.reach : 64.0;
		int best = -1;
		double bestT = Double.MAX_VALUE;
		for (int i = 0; i < scene.count; i++) {
			HealthScene.Entry e = scene.entries[i];
			if (!e.valid || e.self) {
				continue;
			}
			if (!e.visible && !s.throughWalls) {
				continue;
			}
			double t = proj.rayBox(e.x, e.y, e.z, e.width, e.height, 0.1);
			if (t >= 0 && t <= range && t < bestT) {
				bestT = t;
				best = e.id;
			}
		}
		return best;
	}

	/** Without the attacker's position (older versions), blame the nearest hostile mob or player. */
	private void markArc() {
		HealthScene.Entry near = null;
		double bestSq = 12.0 * 12.0;
		for (int i = 0; i < scene.count; i++) {
			HealthScene.Entry e = scene.entries[i];
			if (!e.valid || e.self || !(e.hostile || e.player)) {
				continue;
			}
			double dx = e.x - scene.camX, dz = e.z - scene.camZ;
			double sq = dx * dx + dz * dz;
			if (sq < bestSq) {
				bestSq = sq;
				near = e;
			}
		}
		if (near != null) {
			DamageArc.mark(near.x, near.z, System.nanoTime());
		}
	}

	private void drawEntity(Canvas c, HealthSettings s, HealthScene.Entry e, double dist, long now) {
		boolean label = e.label || (s.higherForPlayers && e.player && !e.self);
		float top = e.height + ABOVE_HEAD + (label ? LABEL_LIFT : 0f);
		if (!proj.project(e.x, e.y + top, e.z, pt)) {
			return;
		}
		float sx = pt[0], sy = pt[1];
		if (sx < -80 || sx > c.width() + 80 || sy < -80 || sy > c.height() + 80) {
			return;
		}
		float u = s.size;
		if (s.shrinkWithDistance) {
			u *= (float) Math.max(0.5, Math.min(1.0, 10.0 / Math.max(1.0, dist)));
		}
		float rowH = 10f * u;
		float bottom = sy - 9f * u - s.offset * rowH; // top edge of the first row
		int rows = 0;
		boolean hearts = s.hearts || s.override;
		float health = Math.max(0f, Math.min(e.health, e.max));
		if (hearts) {
			boolean number = s.useNumber(e.max, false);
			layout.set(health, e.max, e.absorb, s.percent, s.percentMax);
			if (layout.overflow) {
				number = true;
			}
			if (number) {
				drawNumber(c, s, e, health, sx, bottom, u);
				rows = 1;
			} else {
				rows = HealthLayout.rows(layout.total, s.iconsPerRow);
				drawHearts(c, s, e, sx, bottom, u, now);
			}
		}
		if ((s.armor || s.override) && e.armor > 0) {
			drawArmor(c, s, e, sx, bottom - rows * rowH, u);
		}
	}

	private int tint(float extra) {
		int a = Math.round(255f * alphaMul * extra);
		return (a << 24) | 0xFFFFFF;
	}

	private void icon(Canvas c, int cell, float x, float y, float size, int argb) {
		c.image(sheet(), x, y, size, size, HealthSprites.u0(cell), HealthSprites.v0(), HealthSprites.u1(cell), HealthSprites.v1(), argb);
		icons++;
	}

	private void drawHearts(Canvas c, HealthSettings s, HealthScene.Entry e, float sx, float bottom, float u, long now) {
		int perRow = Math.max(1, s.iconsPerRow);
		int n = layout.total;
		float step = HealthLayout.STEP * u, size = HealthLayout.ICON * u, rowH = 10f * u;
		float left = sx - (Math.min(n, perRow) * HealthLayout.STEP + 1) * u / 2f;
		boolean hc = scene.hardcore && s.effects;
		int color = s.effects ? e.kind : HealthSprites.NORMAL;
		int hop = s.effects && e.regen ? HealthLayout.regenIndex((int) now, e.max) : -1;
		int tint = tint(1f);
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < n; i++) {
				int type = layout.typeAt(i);
				if (pass == 1 && type == HealthLayout.EMPTY) {
					continue;
				}
				float x = left + (i % perRow) * step;
				float y = bottom - HealthLayout.rowOf(i, perRow) * rowH - (i == hop ? 2f * u : 0f);
				int cell;
				if (pass == 0) {
					cell = hc ? HealthSprites.CONTAINER_HARDCORE : HealthSprites.CONTAINER;
				} else if (type == HealthLayout.YELLOW_FULL || type == HealthLayout.YELLOW_HALF) {
					cell = HealthSprites.heart(HealthSprites.ABSORB, hc, type == HealthLayout.YELLOW_HALF);
				} else {
					cell = HealthSprites.heart(color, hc, type == HealthLayout.RED_HALF);
				}
				icon(c, cell, x, y, size, tint);
			}
		}
	}

	private void drawArmor(Canvas c, HealthSettings s, HealthScene.Entry e, float sx, float bottom, float u) {
		int perRow = Math.max(1, s.iconsPerRow);
		int slots = HealthLayout.armorSlots(e.armor);
		float step = HealthLayout.STEP * u, size = HealthLayout.ICON * u, rowH = 10f * u;
		float left = sx - (Math.min(slots, perRow) * HealthLayout.STEP + 1) * u / 2f;
		int tint = tint(1f);
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < slots; i++) {
				int type = HealthLayout.armorType(i, e.armor);
				if (pass == 1 && type == 0) {
					continue;
				}
				float x = left + (i % perRow) * step;
				float y = bottom - HealthLayout.rowOf(i, perRow) * rowH;
				int cell = pass == 0 ? HealthSprites.ARMOR_EMPTY : type == 2 ? HealthSprites.ARMOR_HALF : HealthSprites.ARMOR_FULL;
				icon(c, cell, x, y, size, tint);
			}
		}
	}

	private void drawNumber(Canvas c, HealthSettings s, HealthScene.Entry e, float health, float sx, float bottom, float u) {
		String text = HealthLayout.text(health, e.max, e.absorb, s.percent, s.decimals);
		float fs = 11f * u;
		float w = c.textWidth(Fonts.MEDIUM, fs, text);
		float x = sx - w / 2f, y = bottom + 9f * u - c.lineHeight(Fonts.MEDIUM, fs);
		int a = Math.round(((s.numberColor >>> 24) / 255f) * alphaMul * 255f);
		int color = (a << 24) | (s.numberColor & 0xFFFFFF);
		if (s.numberShadow) {
			c.text(Fonts.MEDIUM, fs, text, x + Math.max(0.75f, u), y + Math.max(0.75f, u), (Math.round(a * 0.8f) << 24));
		}
		c.text(Fonts.MEDIUM, fs, text, x, y, color);
		icons++;
	}
}
