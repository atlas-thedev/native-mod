package xyz.nativelaunch.ui.mod;

import java.nio.file.Path;

import xyz.nativelaunch.health.DamageArc;
import xyz.nativelaunch.health.EntityList;
import xyz.nativelaunch.health.HealthSettings;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.UiRuntime;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Image;

/**
 * Health Indicators: floating hearts / number / armour over mobs and players, plus the damage-direction arc.
 * Written for Native from scratch; the game-side drawing lives in the per-version hook ({@code health26}), this
 * class owns the settings, the keys and the arc's HUD drawing.
 */
public final class HealthModule extends Module {
	// ── indicator ──
	final Setting.Bool hearts = add(new Setting.Bool("hearts", "Health indicator", true));
	final Setting.Bool armor = add(new Setting.Bool("armor", "Armour indicator", false));
	final Setting.Choice style = add(new Setting.Choice("style", "Style", 0, "Hearts", "Number", "Dynamic"));
	final Setting.Num dynamicThreshold = add(new Setting.Num("dynamicThreshold", "Number above this max health", 100, 10, 200, 2, ""));
	final Setting.Num size = add(new Setting.Num("size", "Size", 100, 30, 300, 5, "%"));
	final Setting.Bool shrink = add(new Setting.Bool("shrink", "Shrink a little when far away", false));
	final Setting.Num opacity = add(new Setting.Num("opacity", "Opacity", 100, 0, 100, 5, "%"));
	final Setting.Num perRow = add(new Setting.Num("perRow", "Icons per row", 10, 1, 20, 1, ""));
	final Setting.Bool effects = add(new Setting.Bool("effects", "Poison / wither / frozen / hardcore hearts", true));
	final Setting.Color numberColor = add(new Setting.Color("numberColor", "Number colour", 0xFFFF0000));
	final Setting.Bool numberShadow = add(new Setting.Bool("numberShadow", "Number shadow", false));
	final Setting.Num decimals = add(new Setting.Num("decimals", "Decimal places", 1, 0, 4, 1, ""));
	final Setting.Bool percent = add(new Setting.Bool("percent", "Show as a percentage", false));
	final Setting.Num percentMax = add(new Setting.Num("percentMax", "Hearts shown at 100 %", 20, 2, 100, 2, ""));
	final Setting.Num offset = add(new Setting.Num("offset", "Height offset", 0, -4, 8, 0.25f, ""));
	final Setting.Bool higherForPlayers = add(new Setting.Bool("higherForPlayers", "Lift above other players' name tags", false));

	// ── who gets one ──
	final Setting.Bool passive = add(new Setting.Bool("passive", "Passive mobs", true));
	final Setting.Bool hostile = add(new Setting.Bool("hostile", "Hostile mobs", true));
	final Setting.Bool players = add(new Setting.Bool("players", "Players", true));
	final Setting.Bool self = add(new Setting.Bool("self", "Myself (third person)", false));
	final Setting.Bool overridePlayers = add(new Setting.Bool("overridePlayers", "Always show other players", false));
	final Setting.Bool throughWalls = add(new Setting.Bool("throughWalls", "Show the mob I aim at through walls", false));
	final Setting.Bool afterAttack = add(new Setting.Bool("afterAttack", "Only after I hit it", false));
	final Setting.Num timeAfterHit = add(new Setting.Num("timeAfterHit", "Keep showing for", 60, 1, 120, 1, " s"));
	final Setting.Bool damagedOnly = add(new Setting.Bool("damagedOnly", "Only when hurt", false));
	final Setting.Num maxHealthPct = add(new Setting.Num("maxHealthPct", "Only below", 100, 1, 100, 1, " %"));
	final Setting.Bool lookingAt = add(new Setting.Bool("lookingAt", "Only the mob under the crosshair", false));
	final Setting.Num reach = add(new Setting.Num("reach", "Crosshair reach", 3, 1, 64, 1, " blocks"));
	final Setting.Bool withinDistance = add(new Setting.Bool("withinDistance", "Only nearby", false));
	final Setting.Num distance = add(new Setting.Num("distance", "Distance", 64, 4, 512, 4, " blocks"));

	// ── damage direction ──
	final Setting.Bool arc = add(new Setting.Bool("arc", "Damage direction arc", false));
	final Setting.Num arcScale = add(new Setting.Num("arcScale", "Arc size", 2, 1, 8, 1, "x"));
	final Setting.Color arcColor = add(new Setting.Color("arcColor", "Arc colour", 0xFFFF0000));
	final Setting.Num arcSeconds = add(new Setting.Num("arcSeconds", "Arc visible for", 4, 1, 8, 1, " s"));
	final Setting.Bool arcFade = add(new Setting.Bool("arcFade", "Fade out", true));
	final Setting.Num arcFadeSeconds = add(new Setting.Num("arcFadeSeconds", "Fade time", 3, 0, 7, 1, " s"));

	// ── messages ──
	final Setting.Choice messages = add(new Setting.Choice("messages", "Key messages", 0, "Action bar", "Chat", "None"));
	final Setting.Bool coloredMessages = add(new Setting.Bool("coloredMessages", "Coloured messages", true));

	// ── keys ──
	final Setting.Key keyHearts = add(new Setting.Key("keyHearts", "Toggle health indicators", 72));
	final Setting.Key keyArmor = add(new Setting.Key("keyArmor", "Toggle armour indicators", 74));
	final Setting.Key keyOverride = add(new Setting.Key("keyOverride", "Hold: show everything", 71));
	final Setting.Key keyUp = add(new Setting.Key("keyUp", "Raise indicators", 266));
	final Setting.Key keyDown = add(new Setting.Key("keyDown", "Lower indicators", 267));
	final Setting.Key keyList = add(new Setting.Key("keyList", "Hide / show this mob type", 75));

	private static final Image WHITE = new Image(1, 1, new int[] {0xFFFFFFFF});
	private static final float[] WHITE_UV = {0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f};
	private final float[] quads = new float[DamageArc.QUAD_FLOATS];
	private final float[] one = new float[8];
	private static EntityList list;
	private boolean lastAfterAttack;
	private final HealthHud hud = new HealthHud();
	private int hudBroken;

	public HealthModule() {
		super("health", "Health Indicators", "Floating hearts, a number or armour above mobs and players, and an arc that points at what hurt you.",
				VISUAL, Theme.I_HEART_PULSE, true);
	}

	/** The blacklist / whitelist ({@code config/native-health.json}). */
	public static synchronized EntityList list() {
		if (list == null) {
			Path dir = xyz.nativelaunch.core.NativeState.get().gameDir();
			list = EntityList.load(dir == null ? null : dir.resolve("config").resolve("native-health.json"));
		}
		return list;
	}

	@Override
	protected boolean hidden(Setting s) {
		boolean number = style.value != 0;
		if (s == dynamicThreshold) {
			return style.value != 2;
		}
		if (s == perRow || s == effects) {
			return style.value == 1;
		}
		if (s == numberColor || s == numberShadow || s == decimals) {
			return !number && !percent.value;
		}
		if (s == percentMax) {
			return !percent.value;
		}
		if (s == timeAfterHit) {
			return !afterAttack.value;
		}
		if (s == maxHealthPct) {
			return !damagedOnly.value;
		}
		if (s == reach) {
			return !lookingAt.value;
		}
		if (s == distance) {
			return !withinDistance.value;
		}
		if (s == arcScale || s == arcColor || s == arcSeconds || s == arcFade) {
			return !arc.value;
		}
		if (s == arcFadeSeconds) {
			return !arc.value || !arcFade.value;
		}
		return false;
	}

	@Override
	protected void onEnable() {
		sync();
	}

	@Override
	protected void onDisable() {
		HealthSettings.cur.enabled = false;
		HealthSettings.cur.override = false;
		DamageArc.clear();
	}

	/** Copies the menu values into the plain settings object the game hooks read. */
	void sync() {
		HealthSettings s = HealthSettings.cur;
		s.enabled = enabled;
		s.hearts = hearts.value;
		s.armor = armor.value;
		s.style = style.value;
		s.dynamicThreshold = Math.round(dynamicThreshold.value);
		s.size = size.value / 100f;
		s.shrinkWithDistance = shrink.value;
		s.opacity = Math.round(opacity.value);
		s.iconsPerRow = Math.round(perRow.value);
		s.effects = effects.value;
		s.numberColor = numberColor.value;
		s.numberShadow = numberShadow.value;
		s.decimals = Math.round(decimals.value);
		s.percent = percent.value;
		s.percentMax = Math.round(percentMax.value);
		s.offset = offset.value;
		s.higherForPlayers = higherForPlayers.value;
		s.passive = passive.value;
		s.hostile = hostile.value;
		s.players = players.value;
		s.self = self.value;
		s.overridePlayers = overridePlayers.value;
		s.throughWalls = throughWalls.value;
		s.afterAttack = afterAttack.value;
		s.timeAfterHit = Math.round(timeAfterHit.value);
		s.damagedOnly = damagedOnly.value;
		s.maxHealthPct = Math.round(maxHealthPct.value);
		s.lookingAt = lookingAt.value;
		s.reach = Math.round(reach.value);
		s.withinDistance = withinDistance.value;
		s.distance = Math.round(distance.value);
		s.messageType = messages.value;
		s.coloredMessages = coloredMessages.value;
		s.arc = arc.value;
		s.arcScale = arcScale.value;
		s.arcColor = arcColor.value;
		s.arcSeconds = Math.round(arcSeconds.value);
		s.arcFade = arcFade.value;
		s.arcFadeSeconds = Math.round(arcFadeSeconds.value);
		if (lastAfterAttack != afterAttack.value) {
			lastAfterAttack = afterAttack.value;
			xyz.nativelaunch.health.HealthEvents.HITS.clear();
		}
	}

	@Override
	public void frame(Game g) {
		sync();
		if (!g.playing) {
			HealthSettings.cur.override = false; // a released key is never seen while a screen is open
		}
	}

	// ── keys ──

	@Override
	public boolean onKey(int key, int action, Game g) {
		if (!g.playing || key <= 7) {
			return false;
		}
		HealthSettings s = HealthSettings.cur;
		if (key == keyOverride.value) {
			if (action == 1 && !s.override) {
				s.override = true;
				say("Health indicators: showing everything", -1);
			} else if (action == 0) {
				s.override = false;
			}
			return true;
		}
		if (action == 0) {
			return key == keyHearts.value || key == keyArmor.value || key == keyUp.value || key == keyDown.value || key == keyList.value;
		}
		if (key == keyHearts.value) {
			if (action == 1) {
				hearts.value = !hearts.value;
				Modules.changed();
				say(hearts.value ? "Enabled Health Indicators" : "Disabled Health Indicators", hearts.value ? 0x55FF55 : 0xFF5555);
			}
			return true;
		}
		if (key == keyArmor.value) {
			if (action == 1) {
				armor.value = !armor.value;
				Modules.changed();
				say(armor.value ? "Enabled Armor Indicators" : "Disabled Armor Indicators", armor.value ? 0x55FF55 : 0xFF5555);
			}
			return true;
		}
		if (key == keyUp.value || key == keyDown.value) {
			float step = s.offsetStep * (key == keyUp.value ? 1 : -1);
			offset.set(offset.value + step);
			Modules.changed();
			say("Set indicator height to " + String.format(java.util.Locale.ROOT, "%.2f", offset.value), -1);
			return true;
		}
		if (key == keyList.value) {
			if (action == 1) {
				toggleTargetInList();
			}
			return true;
		}
		return false;
	}

	private void toggleTargetInList() {
		String id = UiRuntime.mc().healthTarget();
		if (id == null) {
			say("Look at a mob first", -1);
			return;
		}
		EntityList l = list();
		boolean in = l.toggle(id);
		say((in ? "Added " : "Removed ") + id + (in ? " to the " : " from the ") + (l.isBlacklist() ? "hide list" : "show list"), -1);
	}

	private void say(String text, int rgb) {
		HealthSettings s = HealthSettings.cur;
		if (s.messageType == HealthSettings.MESSAGE_NONE) {
			return;
		}
		UiRuntime.mc().healthSay(text, s.coloredMessages ? rgb : -1, s.messageType == HealthSettings.MESSAGE_CHAT);
	}

	// ── damage arc ──

	@Override
	public boolean overlays(Game g) {
		if (!g.hudAllowed || !g.inWorld) {
			return false;
		}
		return hearts.value || armor.value || HealthSettings.cur.override || arc.value;
	}

	@Override
	public void overlay(Ui ui, Game g, boolean editing) {
		if (!editing && g.inWorld && hudBroken < 3) {
			try {
				hud.draw(ui, HealthSettings.cur);
			} catch (Throwable t) {
				xyz.nativelaunch.core.Log.warn("Health indicators failed ({}).", t.toString());
				hudBroken++;
			}
		}
		if (!arc.value || (!editing && !DamageArc.active(System.nanoTime(), Math.round(arcSeconds.value)))) {
			return;
		}
		Canvas c = ui.c;
		float cx = c.width() / 2f, cy = c.height() / 2f;
		float angle;
		float alpha;
		long now = System.nanoTime();
		if (editing && !DamageArc.active(now, Math.round(arcSeconds.value))) {
			angle = 0f;
			alpha = 1f;
		} else {
			angle = DamageArc.angle(g.x, g.z, DamageArc.attackerX(), DamageArc.attackerZ(), g.yaw);
			alpha = DamageArc.alpha(DamageArc.ageSeconds(now), Math.round(arcSeconds.value), arcFade.value, Math.round(arcFadeSeconds.value));
		}
		drawArc(c, cx, cy, angle, arcScale.value, arcColor.value, alpha);
	}

	@Override
	public boolean preview(Ui ui, Game g, float x, float y, float w, float h) {
		Canvas c = ui.c;
		c.round(x, y, w, h, 10, 0xFF1A1B20);
		float cy = y + h * 0.42f;
		float step = Math.min(22f, (w - 24f) / 6f);
		float x0 = x + w / 2f - step * 2.5f;
		for (int i = 0; i < 5; i++) {
			c.icon(Theme.I_HEART, step * 0.8f, x0 + i * step + step / 2f, cy, i < 3 ? 0xFFEF4444 : 0xFF4B4D57);
		}
		String t = "13.0 / 20.0";
		float tw = c.textWidth(xyz.nativelaunch.ui.gfx.Fonts.MEDIUM, 10f, t);
		c.text(xyz.nativelaunch.ui.gfx.Fonts.MEDIUM, 10f, t, x + (w - tw) / 2f, y + h * 0.66f, 0xFFB8BAC4);
		return true;
	}

	private void drawArc(Canvas c, float cx, float cy, float angle, float scale, int argb, float alpha) {
		if (alpha <= 0f) {
			return;
		}
		DamageArc.geometry(cx, cy, angle, scale, quads);
		int a = Math.round(((argb >>> 24) / 255f) * alpha * 255f);
		int main = (a << 24) | (argb & 0xFFFFFF);
		for (int q = 0; q <= DamageArc.SEGMENTS; q++) {
			System.arraycopy(quads, q * 8, one, 0, 8);
			c.freeQuad(WHITE, one, WHITE_UV, main);
		}
	}
}
