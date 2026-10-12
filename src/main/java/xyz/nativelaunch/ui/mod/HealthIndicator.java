package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.ui.gfx.Image;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Health Indicators inspired by AdyTech99's HealthIndicators mod:
 * - Hearts mode with multi-row wrapping (icons_per_row) for big health and absorption.
 * - AdyTech99 filter criteria: only damaged, show on attack (with attack timer), on crosshair only.
 * - Option to show armor points bar.
 * - Number mode with percentage or exact HP + absorption.
 * - Dynamic 3D depth scaling, smooth distance fade, poison/wither textures, and regen hop animation.
 */
final class HealthIndicator extends Module {
	static final int PLAYER = 1, HOSTILE = 2, PASSIVE = 4, SELF = 8;
	static final int POISON = 1, WITHER = 2, REGEN = 4;
	private static final float RANGE = 32f;
	private static final long ATTACK_EXPIRY_MS = 3500L; // 3.5s show time after entity hurt

	// Criteria / Filters
	final Setting.Bool players = add(new Setting.Bool("players", "Players", true));
	final Setting.Bool mobs = add(new Setting.Bool("mobs", "Hostile mobs", true));
	final Setting.Bool passive = add(new Setting.Bool("passive", "Passive animals", true));
	final Setting.Bool self = fixed(new Setting.Bool("self", "Yourself (third person)", false));
	final Setting.Bool damagedOnly = add(new Setting.Bool("damaged_only", "Damaged only", false));
	final Setting.Bool afterAttack = add(new Setting.Bool("after_attack", "Show on attack only", false));
	final Setting.Bool lookingAt = add(new Setting.Bool("looking_at", "On crosshair only", false));
	final Setting.Bool overridePlayers = add(new Setting.Bool("override_players", "Always show players", true));

	// Appearance
	final Setting.Choice display = add(new Setting.Choice("display", "Display as", 0, "Hearts", "Number"));
	final Setting.Bool showArmor = add(new Setting.Bool("armor", "Show armor bar", true));
	final Setting.Num iconsPerRow = fixed(new Setting.Num("icons_per_row", "Hearts per row", 10, 5, 20, 1, ""));
	final Setting.Bool percentage = fixed(new Setting.Bool("percentage", "Percentage HP", false));
	final Setting.Num size = fixed(new Setting.Num("size", "Size", 10, 6, 20, 1, " px"));
	final Setting.Num offset = fixed(new Setting.Num("offset", "Height offset", 0, -25, 30, 1, " px"));

	// Recent damage tracking for after_attack
	private final Map<Integer, Long> recentHits = new HashMap<Integer, Long>();

	HealthIndicator() {
		super("healthindicator", "Health Indicator", "Hearts and health above players and mobs with smart PvP filters.", VISUAL, Theme.I_HEART, false);
	}

	@Override
	public int needs() {
		return Game.NEED_TAGS;
	}

	@Override
	public void frame(Game g) {
		g.tagKinds = (players.value ? PLAYER : 0)
				| (mobs.value ? HOSTILE : 0)
				| (passive.value ? PASSIVE : 0)
				| (self.value ? SELF : 0);
		g.tagRange = RANGE;
		g.tagSight = true; // never through solid walls
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
		long now = System.currentTimeMillis();

		// Clean up expired hits
		if (!recentHits.isEmpty()) {
			Iterator<Map.Entry<Integer, Long>> it = recentHits.entrySet().iterator();
			while (it.hasNext()) {
				if (now - it.next().getValue() > ATTACK_EXPIRY_MS) {
					it.remove();
				}
			}
		}

		for (int i = 0; i < g.tagCount; i++) {
			Game.Tag t = g.tags[i];

			// Track if recently hurt
			int entityKey = (int) (t.depth * 100) ^ Float.floatToIntBits(t.max);
			if (t.hurt) {
				recentHits.put(entityKey, now);
			}

			boolean isPlayer = t.kind == PLAYER;
			boolean bypass = overridePlayers.value && isPlayer;

			// Filter: Damaged only
			if (!bypass && damagedOnly.value) {
				boolean isDamaged = t.health < (t.max - 0.2f) || t.absorb > 0.05f;
				if (!isDamaged) {
					continue;
				}
			}

			// Filter: On crosshair / looking at
			if (!bypass && lookingAt.value && !t.targeted) {
				continue;
			}

			// Filter: After attack only
			if (!bypass && afterAttack.value) {
				Long hitTime = recentHits.get(entityKey);
				if (hitTime == null && !t.hurt) {
					continue;
				}
			}

			// Distance fade out over last 20%
			float a = Math.max(0f, Math.min(1f, (RANGE - t.depth) / (RANGE * 0.2f)));
			if (a <= 0.02f) {
				continue;
			}

			float x = t.nx * W;
			float y = t.ny * H + offset.value;

			// Off-screen culling
			if (x < -100 || x > W + 100 || y < -60 || y > H + 60) {
				continue;
			}

			// Stable fixed heart size (never resizes when moving closer/farther)
			float ps = size.value;

			if (a < 1f) {
				c.pushAlpha(a);
			}

			if (display.value == 1) {
				drawNumber(c, x, y, ps, t.health, t.max, t.absorb, t.armor, t.fx);
			} else {
				drawHearts(c, x, y, ps, t.health, t.max, t.absorb, t.armor, t.fx);
			}

			if (a < 1f) {
				c.popAlpha();
			}
		}
	}

	/** Number display mode: draws readable health pill or text with shadow. */
	private void drawNumber(Canvas c, float x, float y, float ps, float hp, float max, float absorb, int armor, int fx) {
		String text;
		if (percentage.value) {
			int pct = Math.max(0, Math.round(((hp + absorb) / Math.max(1f, max)) * 100f));
			text = pct + " %";
		} else {
			text = Status.hp(hp) + (absorb > 0.05f ? " +" + Status.hp(absorb) : "") + " / " + Status.hp(max);
		}

		float nf = ps * 1.15f;
		float tw = c.textWidth(Fonts.SEMIBOLD, nf, text);
		float pad = 4f;
		float totalW = tw + pad * 2f;
		float totalH = nf + pad;
		float rx = x - totalW / 2f;
		float ry = y - totalH;

		// Subtle dark pill background
		c.round(rx, ry, totalW, totalH, 4f, 0x88000000);

		int col;
		if ((fx & WITHER) != 0) {
			col = 0xFFA1A1AA;
		} else if ((fx & POISON) != 0) {
			col = 0xFF84CC16;
		} else if (absorb > 0.05f) {
			col = 0xFFFBBF24;
		} else {
			col = 0xFFFFFFFF;
		}

		float tx = rx + pad;
		float ty = ry + pad * 0.5f;
		c.text(Fonts.SEMIBOLD, nf, text, tx + 0.8f, ty + 0.8f, 0xAA000000);
		c.text(Fonts.SEMIBOLD, nf, text, tx, ty, col);

		// Armor line if present (left-aligned with the number pill at rx)
		if (showArmor.value && armor > 0) {
			float ay = ry - ps * 1.05f;
			drawArmorBar(c, rx, ay, ps, ps * 0.88f, armor);
		}
	}

	/** Hearts display mode: AdyTech99 wrapping logic with absorption & effects. */
	private void drawHearts(Canvas c, float x, float y, float ps, float hp, float max, float absorb, int armor, int fx) {
		if (percentage.value) {
			hp = (hp / Math.max(1f, max)) * 20f;
			max = 20f;
		}
		int healthRed = (int) Math.ceil(hp);
		int maxHealth = (int) Math.ceil(max);
		int healthYellow = (int) Math.ceil(absorb);

		int heartsRed = (int) Math.ceil(healthRed / 2.0f);
		boolean lastRedHalf = (healthRed & 1) == 1;
		int heartsNormal = (int) Math.ceil(maxHealth / 2.0f);
		int heartsYellow = (int) Math.ceil(healthYellow / 2.0f);
		boolean lastYellowHalf = (healthYellow & 1) == 1;
		int heartsTotal = heartsNormal + heartsYellow;

		int perRow = Math.max(5, (int) iconsPerRow.value);
		int cols = Math.min(heartsTotal, perRow);
		float pitch = ps * 0.88f;
		float rowH = ps * 0.95f;
		float totalW = pitch * (cols - 1) + ps;
		float x0 = x - totalW / 2f;

		int regenJump = (fx & REGEN) != 0 ? (int) ((System.currentTimeMillis() / 60) % Math.max(1, heartsTotal)) : -1;
		int rows = (heartsTotal + perRow - 1) / perRow;

		// 1. Draw empty containers
		for (int heart = 0; heart < heartsTotal; heart++) {
			int col = heart % perRow;
			int row = heart / perRow;
			float hx = x0 + col * pitch;
			float hy = y - ps - row * rowH;
			if (heart == regenJump) {
				hy -= ps * 0.22f;
			}
			pip(c, "heart_container", hx, hy, ps, 0xFF3F3F46);
		}

		// 2. Draw heart fillings
		String witherOrPoison = (fx & WITHER) != 0 ? "wither" : (fx & POISON) != 0 ? "poison" : "";
		for (int heart = 0; heart < heartsTotal; heart++) {
			int col = heart % perRow;
			int row = heart / perRow;
			float hx = x0 + col * pitch;
			float hy = y - ps - row * rowH;
			if (heart == regenJump) {
				hy -= ps * 0.22f;
			}

			if (heart < heartsRed) {
				boolean half = (heart == heartsRed - 1) && lastRedHalf;
				if (!witherOrPoison.isEmpty()) {
					pip(c, "heart_" + witherOrPoison + "_" + (half ? "half" : "full"), hx, hy, ps, 0xFF84CC16);
				} else {
					pip(c, half ? "heart_half" : "heart_full", hx, hy, ps, 0xFFEF4444);
				}
			} else if (heart >= heartsNormal) {
				int yellowIndex = heart - heartsNormal;
				if (yellowIndex < heartsYellow) {
					boolean half = (yellowIndex == heartsYellow - 1) && lastYellowHalf;
					pip(c, half ? "heart_absorb_half" : "heart_absorb_full", hx, hy, ps, 0xFFF59E0B);
				}
			}
		}

		// 3. Draw armor bar above the hearts if enabled (left-aligned with hearts at x0)
		if (showArmor.value && armor > 0) {
			float armorY = y - ps - rows * rowH - ps * 0.15f;
			drawArmorBar(c, x0, armorY, ps, pitch, armor);
		}
	}

	/** Draws the 10-icon armor point bar left-aligned with the health bar at x0. */
	private void drawArmorBar(Canvas c, float x0, float y, float size, float pitch, int armor) {
		int armorFull = armor / 2;
		boolean half = (armor % 2) != 0;
		int totalPips = 10;

		for (int i = 0; i < totalPips; i++) {
			float ax = x0 + i * pitch;
			pip(c, "armor_empty", ax, y, size, 0xFF3F3F46);
			if (i < armorFull) {
				pip(c, "armor_full", ax, y, size, 0xFFFFFFFF);
			} else if (i == armorFull && half) {
				pip(c, "armor_half", ax, y, size, 0xFFFFFFFF);
			}
		}
	}

	private static void pip(Canvas c, String name, float x, float y, float size, int fallback) {
		Image img = Vanilla.pip(name);
		if (img != null) {
			Vanilla.draw(c, img, x, y, size, size, 0xFFFFFFFF);
		} else if (!name.endsWith("empty") && !name.endsWith("container")) {
			c.round(x + 1, y + 1, size - 2, size - 2, size * 0.3f, fallback);
		}
	}

	@Override
	public boolean preview(Ui ui, Game g, float x, float y, float w, float h) {
		Canvas c = ui.c;
		c.round(x, y, w, h, 10, 0xFF1E293B);
		c.fill(x, y + h * 0.68f, w, h * 0.32f, 0xFF334155);
		c.pushClip(x, y, w, h);

		float ps = Math.min(12f, w / 16f);
		// Preview 1: Player with hearts, absorption, and armor
		drawHearts(c, x + w * 0.5f, y + h * 0.42f, ps, 16f, 20f, 4f, 15, 0);

		// Preview 2: Poisoned mob with damage
		drawHearts(c, x + w * 0.5f, y + h * 0.78f, ps, 11f, 20f, 0f, 0, POISON);

		c.popClip();
		return true;
	}
}
