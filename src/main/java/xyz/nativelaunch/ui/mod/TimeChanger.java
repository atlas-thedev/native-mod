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
}
