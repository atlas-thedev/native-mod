package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.McBridge;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.UiRuntime;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;

/**
 * Freelook: hold a key and the mouse turns the camera around your player instead of turning the player, so you can look
 * behind you while still running forward. Let go and the view glides back. The game's look-direction and camera code are
 * hooked (FreelookMixin, FreelookCameraMixin); this module owns the state they read.
 */
public final class Freelook extends Module {
	private static volatile boolean active;
	private static volatile boolean returning;
	private static volatile float yawOff, pitchOff;
	private static volatile Object player;
	private static float lookYaw, lookPitch, baseYaw, basePitch;

	final Setting.Key key = add(new Setting.Key("key", "Freelook key", 342)); // left alt
	final Setting.Bool toggle = add(new Setting.Bool("toggle", "Toggle instead of hold", false));
	final Setting.Bool third = add(new Setting.Bool("third", "Third person while looking around", true));
	final Setting.Bool smooth = add(new Setting.Bool("smooth", "Glide back when I let go", true));
	final Setting.Num sensitivity = add(new Setting.Num("sensitivity", "Look speed", 100, 25, 200, 5, "%"));

	private boolean toggled, wasDown, restorePerspective;
	private int savedPerspective = -1;
	private long last;

	Freelook() {
		super("freelook", "Freelook", "Hold a key and move the mouse to look all around your player without turning it: check behind you while you keep running.", VISUAL,
				Theme.I_EYE, false);
	}

	/** True while the camera is detached from where the player faces (or gliding back to it). */
	public static boolean applying() {
		return active || returning;
	}

	/** The game's look-direction hook: true when freelook took this mouse movement (the player must not turn). */
	public static boolean turn(Object entity, double dx, double dy) {
		if (!active || entity == null || entity != player) {
			return false;
		}
		float k = 0.15f * sens;
		lookYaw += (float) dx * k;
		lookPitch = Math.max(-90f, Math.min(90f, lookPitch + (float) dy * k));
		yawOff = lookYaw - baseYaw;
		pitchOff = lookPitch - basePitch;
		return true;
	}

	private static volatile float sens = 1f;

	/** Camera yaw as the game worked it out, turned by the freelook offset. */
	public static float yaw(float original) {
		return applying() ? original + yawOff : original;
	}

	public static float pitch(float original) {
		return applying() ? Math.max(-90f, Math.min(90f, original + pitchOff)) : original;
	}

	@Override
	public boolean onKey(int code, int action, Game g) {
		if (toggle.value && action == 1 && key.value >= 0 && code == key.value && g.playing) {
			toggled = !toggled;
			return true;
		}
		return false;
	}

	@Override
	public void frame(Game g) {
		McBridge mc = UiRuntime.mc();
		if (mc == null) {
			return;
		}
		long now = System.nanoTime();
		float dt = last == 0 ? 0.016f : Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		sens = sensitivity.value / 100f;
		player = mc.playerEntity();
		boolean down = key.value >= 0 && g.playing && Keys.isDown(g.window, key.value);
		boolean want = g.playing && (toggle.value ? toggled : down);
		if (!g.playing && toggled) {
			toggled = false;
		}
		if (want && !active) {
			start(mc, g);
		} else if (!want && active) {
			stop(mc);
		}
		wasDown = down;
		if (returning) {
			float k = (float) Math.exp(-14 * dt);
			yawOff *= k;
			pitchOff *= k;
			if (Math.abs(yawOff) < 0.2f && Math.abs(pitchOff) < 0.2f) {
				yawOff = pitchOff = 0;
				returning = false;
			}
		}
	}

	/** Detaches the camera from where the player faces right now (also the entry point for tests). */
	static void begin(Object entity, float yaw, float pitch) {
		player = entity;
		baseYaw = lookYaw = yaw;
		basePitch = lookPitch = pitch;
		yawOff = pitchOff = 0;
		returning = false;
		active = true;
	}

	static void end(boolean glide) {
		active = false;
		if (glide) {
			returning = true;
		} else {
			yawOff = pitchOff = 0;
		}
	}

	private void start(McBridge mc, Game g) {
		begin(player, g.yaw, g.pitch);
		active = false; // switched on below, once the perspective is set
		restorePerspective = false;
		if (third.value) {
			savedPerspective = mc.perspective();
			if (savedPerspective == 0) {
				mc.setPerspective(1);
				restorePerspective = true;
			}
		}
		active = true;
	}

	private void stop(McBridge mc) {
		if (restorePerspective) {
			mc.setPerspective(savedPerspective);
			restorePerspective = false;
		}
		end(smooth.value);
	}

	@Override
	protected void onDisable() {
		McBridge mc = UiRuntime.mc();
		if (active && mc != null) {
			stop(mc);
		}
		active = false;
		returning = false;
		yawOff = pitchOff = 0;
		toggled = false;
	}

	@Override
	public boolean preview(Ui ui, Game g, float x, float y, float w, float h) {
		Canvas c = ui.c;
		c.gradientV(x, y, w, h * 0.6f, 0xFF6BA8E8, 0xFFB9DAF5);
		c.fill(x, y + h * 0.6f, w, h * 0.4f, 0xFF5E8F3F);
		float cx = x + w / 2, cy = y + h * 0.7f, u = Math.min(w, h) / 70f;
		// the player stays put while the camera swings around it
		int[][] parts = {{-4, 24, 8, 8}, {-4, 12, 8, 12}, {-8, 12, 4, 12}, {4, 12, 4, 12}, {-4, 0, 4, 12}, {0, 0, 4, 12}};
		int[] col = {0xFFC49A6C, 0xFF2AA3A6, 0xFFC49A6C, 0xFFC49A6C, 0xFF3A44A8, 0xFF3A44A8};
		for (int i = 0; i < parts.length; i++) {
			c.fill(cx + parts[i][0] * u, cy - (parts[i][1] + parts[i][3]) * u, parts[i][2] * u, parts[i][3] * u, col[i]);
		}
		double a = ui.now / 900.0;
		float rx = w * 0.36f, ry = h * 0.2f;
		float camX = cx + (float) Math.sin(a) * rx, camY = cy - 16 * u + (float) Math.cos(a) * ry * 0.5f;
		c.round(camX - 11, camY - 8, 22, 16, 4, 0xFF15171D);
		c.outline(camX - 11, camY - 8, 22, 16, 4, 1.5f, Theme.ACCENT);
		c.circle(camX, camY, 4, Theme.ACCENT);
		String hint = key.value >= 0 ? (toggle.value ? "Press " : "Hold ") + Keys.name(key.value) + " and move the mouse" : "Set a freelook key";
		float tw = c.textWidth(Fonts.SEMIBOLD, 10.5f, hint) + 20;
		c.round(x + (w - tw) / 2, y + h - 34, tw, 22, 11, 0xC0000000);
		c.text(Fonts.SEMIBOLD, 10.5f, hint, x + (w - tw) / 2 + 10, y + h - 34 + (22 - c.lineHeight(Fonts.SEMIBOLD, 10.5f)) / 2, Theme.TEXT_SECONDARY);
		return true;
	}
}
