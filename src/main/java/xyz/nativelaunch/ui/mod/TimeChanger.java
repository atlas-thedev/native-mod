package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;

/**
 * Time Changer: shows the world at any time of day on your screen only (the server and other players keep the real time, and
 * the integrated server of a singleplayer world is not touched). The client's copy of the world time is hooked
 * (TimeChangerMixin); the day count stays, so the moon phase stays too.
 */
public final class TimeChanger extends Module {
	/** Set while this client asks for the real time (the HUD's own clock), so the hook lets it through. */
	public static volatile boolean bypass;
	private static volatile boolean active;
	private static volatile long target = 6000;
	private static volatile boolean flow;
	private static volatile boolean flowDirty = true;
	private static volatile long flowOffset;

	static final String[] NAMES = {"Sunrise", "Noon", "Sunset", "Night", "Midnight", "Custom"};
	static final long[] TICKS = {0, 6000, 12000, 14000, 18000, -1};
	final Setting.Choice preset = add(new Setting.Choice("preset", "Time", 1, NAMES).shorts("Dawn", "Noon", "Dusk", "Night", "Midn.", "Custom"));
	final Setting.Num custom = add(new Setting.Num("custom", "Time of day", 6000, 0, 23750, 250, " ticks"));
	final Setting.Bool pass = add(new Setting.Bool("flow", "Let time keep passing", false));

	TimeChanger() {
		super("time-changer", "Time Changer", "See the world at sunrise, noon, sunset or midnight whenever you like. Only on your screen: the server's time does not change.", VISUAL,
				Theme.I_CLOCK, false);
	}

	@Override
	protected boolean hidden(Setting s) {
		return s == custom && preset.value != NAMES.length - 1;
	}

	/** Time of day in ticks (0 = sunrise) the chosen preset stands for. */
	static long chosen(int presetIndex, float customTicks) {
		long t = TICKS[Math.max(0, Math.min(TICKS.length - 1, presetIndex))];
		return t >= 0 ? t : Math.floorMod((long) customTicks, 24000L);
	}

	/** The hook: the world time as the game has it in, the time to show out. Whole days stay as they are. */
	public static long map(long real) {
		if (!active || bypass) {
			return real;
		}
		long day = Math.floorDiv(real, 24000L);
		if (flow) {
			if (flowDirty) {
				flowOffset = target - Math.floorMod(real, 24000L);
				flowDirty = false;
			}
			return real + flowOffset;
		}
		return day * 24000L + target;
	}

	@Override
	public void frame(Game g) {
		long t = chosen(preset.value, custom.value);
		if (t != target || pass.value != flow) {
			flowDirty = true;
		}
		target = t;
		flow = pass.value;
		active = true;
	}

	@Override
	protected void onDisable() {
		active = false;
		flowDirty = true;
	}

	@Override
	public boolean preview(Ui ui, Game g, float x, float y, float w, float h) {
		Canvas c = ui.c;
		long t = chosen(preset.value, custom.value);
		if (pass.value) {
			t = (t + (ui.now / 20)) % 24000;
		}
		float f = t / 24000f; // 0 sunrise, .25 noon, .5 sunset, .75 midnight
		double ang = f * Math.PI * 2;
		float sun = (float) Math.sin(ang); // 1 at noon, -1 at midnight
		int top = ExtraModulesSky.sky(t, true), bottom = ExtraModulesSky.sky(t, false);
		c.gradientV(x, y, w, h * 0.66f, top, bottom);
		c.fill(x, y + h * 0.66f, w, h * 0.34f, ExtraModulesSky.ground(t));
		float cx = x + w / 2, cy = y + h * 0.66f, r = Math.min(w * 0.4f, h * 0.5f);
		float sx = cx - (float) Math.cos(ang) * r, sy = cy - sun * r * 0.9f; // sun rises on the left
		if (sy < cy) {
			c.circle(sx, sy, 15, 0xFFFFE08A);
		}
		float mx = cx + (float) Math.cos(ang) * r, my = cy + sun * r * 0.9f;
		if (my < cy) {
			c.circle(mx, my, 12, 0xFFE6EAF5);
			c.circle(mx + 4, my - 3, 10, top);
		}
		for (int i = 0; i < 14 && sun < 0.05f; i++) { // stars while it is dark
			float a = Math.min(1f, (0.05f - sun) * 4);
			c.fill(x + ((i * 97) % 100) / 100f * w, y + ((i * 53) % 55) / 100f * h * 0.6f, 2, 2, Theme.alpha(0xFFFFFFFF, a * 0.9f));
		}
		long hours = (t / 1000 + 6) % 24, minutes = (t % 1000) * 60 / 1000;
		String label = (hours < 10 ? "0" : "") + hours + ":" + (minutes < 10 ? "0" : "") + minutes;
		float tw = c.textWidth(Fonts.SEMIBOLD, 12, label) + 22;
		c.round(x + (w - tw) / 2, y + h - 36, tw, 24, 12, 0xC0000000);
		c.text(Fonts.SEMIBOLD, 12, label, x + (w - tw) / 2 + 11, y + h - 36 + (24 - c.lineHeight(Fonts.SEMIBOLD, 12)) / 2, Theme.TEXT_STRONG);
		return true;
	}
}
