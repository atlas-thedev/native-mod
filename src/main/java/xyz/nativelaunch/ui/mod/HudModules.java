package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.McBridge;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.UiRuntime;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;

import java.util.Calendar;
import java.util.Locale;

/** The built-in modules: HUD read-outs plus a few mechanics (toggle sprint, zoom, fullbright). */
public final class HudModules {
	private HudModules() {
	}

	static void register() {
		Modules.register(new Fps());
		Modules.register(new Cps());
		Modules.register(new Keystrokes());
		Modules.register(new Coordinates());
		Modules.register(new Direction());
		Modules.register(new Clock());
		Modules.register(new Memory());
		Modules.register(new Ping());
		Modules.register(new ServerIp());
		Modules.register(new Speed());
		Modules.register(new ToggleSprint());
		Modules.register(new ZoomModule());
		Modules.register(new Fullbright());
	}

	static McBridge mc() {
		return UiRuntime.mc();
	}

	// ── text read-outs ───────────────────────────────────────────────────

	static final class Fps extends HudModule {
		Fps() {
			super("fps", "FPS", "Frames per second.", Theme.I_GAUGE, true, 0f, 0f);
		}

		@Override
		protected boolean valueFirst() {
			return true;
		}

		@Override
		protected int lines(Game g, String[] l, String[] v) {
			l[0] = "FPS";
			v[0] = String.valueOf(g.fps);
			return 1;
		}
	}

	static final class Cps extends HudModule {
		final Setting.Bool right = add(new Setting.Bool("right", "Show right clicks", true));

		Cps() {
			super("cps", "CPS", "Clicks per second.", Theme.I_CLICK, false, 0f, 0.07f);
		}

		@Override
		protected boolean valueFirst() {
			return true;
		}

		@Override
		protected int lines(Game g, String[] l, String[] v) {
			l[0] = "CPS";
			v[0] = right.value ? g.cpsLeft + " | " + g.cpsRight : String.valueOf(g.cpsLeft);
			return 1;
		}
	}

	static final class Coordinates extends HudModule {
		final Setting.Bool facing = add(new Setting.Bool("facing", "Show facing", true));
		final Setting.Bool compact = add(new Setting.Bool("compact", "One line", false));
		final Setting.Choice decimals = add(new Setting.Choice("decimals", "Decimals", 0, "0", "1", "2"));

		Coordinates() {
			super("coords", "Coordinates", "Your position and facing.", Theme.I_MAP_PIN, false, 0f, 0.14f);
		}

		private String n(double v) {
			switch (decimals.value) {
				case 1:
					return String.format(Locale.ROOT, "%.1f", v);
				case 2:
					return String.format(Locale.ROOT, "%.2f", v);
				default:
					return String.valueOf((long) Math.floor(v));
			}
		}

		@Override
		protected int lines(Game g, String[] l, String[] v) {
			if (compact.value) {
				l[0] = "XYZ";
				v[0] = n(g.x) + " " + n(g.y) + " " + n(g.z) + (facing.value ? "  " + g.facing() : "");
				return 1;
			}
			l[0] = "X";
			v[0] = n(g.x);
			l[1] = "Y";
			v[1] = n(g.y);
			l[2] = "Z";
			v[2] = n(g.z);
			if (facing.value) {
				l[3] = "Facing";
				v[3] = g.facing();
				return 4;
			}
			return 3;
		}
	}

	static final class Clock extends HudModule {
		final Setting.Bool h24 = add(new Setting.Bool("24h", "24-hour clock", true));
		final Setting.Bool seconds = add(new Setting.Bool("seconds", "Show seconds", false));
		private final Calendar cal = Calendar.getInstance();

		Clock() {
			super("clock", "Clock", "The time on your computer.", Theme.I_CLOCK, false, 1f, 0f);
		}

		@Override
		protected int lines(Game g, String[] l, String[] v) {
			cal.setTimeInMillis(System.currentTimeMillis());
			int h = cal.get(Calendar.HOUR_OF_DAY), m = cal.get(Calendar.MINUTE), s = cal.get(Calendar.SECOND);
			String suffix = "";
			if (!h24.value) {
				suffix = h < 12 ? " AM" : " PM";
				h = h % 12 == 0 ? 12 : h % 12;
			}
			l[0] = null;
			v[0] = (h24.value && h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m
					+ (seconds.value ? ":" + (s < 10 ? "0" : "") + s : "") + suffix;
			return 1;
		}
	}

	static final class Memory extends HudModule {
		final Setting.Bool detail = add(new Setting.Bool("detail", "Show amount", false));

		Memory() {
			super("memory", "Memory", "Java memory in use.", Theme.I_MEMORY, false, 1f, 0.07f);
		}

		@Override
		protected int lines(Game g, String[] l, String[] v) {
			long max = Math.max(1, g.memMax);
			int pct = (int) (g.memUsed * 100 / max);
			l[0] = "Mem";
			v[0] = pct + "%" + (detail.value ? String.format(Locale.ROOT, " %.1f/%.1f GB", g.memUsed / 1073741824.0, max / 1073741824.0) : "");
			return 1;
		}
	}

	static final class Ping extends HudModule {
		Ping() {
			super("ping", "Ping", "Latency to the server.", Theme.I_SIGNAL, false, 1f, 0.14f);
		}

		@Override
		public boolean visible(Game g) {
			return g.sample || g.ping >= 0 && g.server != null;
		}

		@Override
		protected boolean valueFirst() {
			return true;
		}

		@Override
		protected int lines(Game g, String[] l, String[] v) {
			l[0] = "ms";
			v[0] = String.valueOf(Math.max(0, g.ping));
			return 1;
		}
	}

	static final class ServerIp extends HudModule {
		ServerIp() {
			super("server", "Server IP", "The server you are playing on.", Theme.I_SERVER, false, 1f, 0.21f);
		}

		@Override
		public boolean visible(Game g) {
			return g.server != null && !g.server.isEmpty();
		}

		@Override
		protected int lines(Game g, String[] l, String[] v) {
			l[0] = null;
			v[0] = g.server == null ? "" : g.server;
			return 1;
		}
	}

	static final class Speed extends HudModule {
		final Setting.Choice unit = add(new Setting.Choice("unit", "Unit", 0, "m/s", "km/h"));

		Speed() {
			super("speed", "Speed", "How fast you are moving.", Theme.I_ACTIVITY, false, 0f, 0.28f);
		}

		@Override
		protected boolean valueFirst() {
			return true;
		}

		@Override
		protected int lines(Game g, String[] l, String[] v) {
			float s = unit.value == 1 ? g.speed * 3.6f : g.speed;
			l[0] = unit.options[unit.value];
			v[0] = String.format(Locale.ROOT, "%.1f", s);
			return 1;
		}
	}

	// ── graphical ────────────────────────────────────────────────────────

	static final class Keystrokes extends HudModule {
		final Setting.Bool mouse = add(new Setting.Bool("mouse", "Mouse buttons", true));
		final Setting.Bool space = add(new Setting.Bool("space", "Space bar", true));
		final Setting.Bool cps = add(new Setting.Bool("cps", "CPS on mouse", true));
		final Setting.Color pressed = add(new Setting.Color("pressed", "Pressed colour", 0xFFFFFFFF));
		private final float[] size = new float[2];
		private final float[] anim = new float[Game.CONTROLS.length];
		private long last;

		Keystrokes() {
			super("keystrokes", "Keystrokes", "Shows the movement keys and mouse buttons you press.", Theme.I_KEYBOARD, true, 0f, 0.5f);
		}

		@Override
		public float[] measure(Canvas c, Game g) {
			float s = scale.value, k = 26 * s, gap = 2 * s;
			size[0] = 3 * k + 2 * gap;
			float h = 2 * k + gap;
			if (mouse.value) {
				h += gap + k;
			}
			if (space.value) {
				h += gap + 12 * s;
			}
			size[1] = h;
			return size;
		}

		@Override
		public void paint(Ui ui, float x, float y, Game g) {
			Canvas c = ui.c;
			long now = System.nanoTime();
			float dt = last == 0 ? 0.016f : Math.min(0.1f, (now - last) / 1e9f);
			last = now;
			float kk = 1f - (float) Math.exp(-18 * dt);
			for (int i = 0; i < anim.length; i++) {
				anim[i] += ((g.down[i] ? 1 : 0) - anim[i]) * kk;
			}
			float s = scale.value, k = 26 * s, gap = 2 * s;
			key(c, s, x + k + gap, y, k, k, label(g, Game.FORWARD, "W"), null, anim[Game.FORWARD]);
			float ry = y + k + gap;
			key(c, s, x, ry, k, k, label(g, Game.LEFT, "A"), null, anim[Game.LEFT]);
			key(c, s, x + k + gap, ry, k, k, label(g, Game.BACK, "S"), null, anim[Game.BACK]);
			key(c, s, x + 2 * (k + gap), ry, k, k, label(g, Game.RIGHT, "D"), null, anim[Game.RIGHT]);
			ry += k + gap;
			if (mouse.value) {
				float half = (3 * k + gap) / 2;
				key(c, s, x, ry, half, k, "LMB", cps.value ? g.cpsLeft + " CPS" : null, anim[Game.ATTACK]);
				key(c, s, x + half + gap, ry, half, k, "RMB", cps.value ? g.cpsRight + " CPS" : null, anim[Game.USE]);
				ry += k + gap;
			}
			if (space.value) {
				float w = 3 * k + 2 * gap, h = 12 * s;
				box(c, s, x, ry, w, h, anim[Game.JUMP]);
				float bw = w * 0.35f;
				c.round(x + (w - bw) / 2, ry + h / 2 - 1 * s, bw, 2 * s, s, Theme.mix(Theme.alpha(color.value, 0.8f), Theme.SOLID_FG, anim[Game.JUMP]));
			}
		}

		private static String label(Game g, int i, String def) {
			int code = g.keys[i];
			if (code < 0) {
				return def;
			}
			String n = Keys.name(code);
			return n.length() > 5 ? def : n;
		}

		private void box(Canvas c, float s, float x, float y, float w, float h, float t) {
			float a = opacity.value / 100f;
			int bg = Theme.mix(Theme.alpha(0xFF08090C, a), Theme.alpha(pressed.value, 0.92f), t);
			c.round(x, y, w, h, 5 * s, bg);
			if (style.value == STYLE_CARD) {
				c.outline(x, y, w, h, 5 * s, 1, Theme.alpha(Theme.HAIRLINE_STRONG, Math.max(0.3f, a)));
			}
		}

		private void key(Canvas c, float s, float x, float y, float w, float h, String name, String sub, float t) {
			box(c, s, x, y, w, h, t);
			int fg = Theme.mix(color.value, Theme.SOLID_FG, t);
			float fs = (name.length() > 2 ? 9.5f : 12f) * s;
			float tw = c.textWidth(Fonts.SEMIBOLD, fs, name);
			float lh = c.lineHeight(Fonts.SEMIBOLD, fs);
			if (sub == null) {
				text(c, Fonts.SEMIBOLD, fs, name, x + (w - tw) / 2, y + (h - lh) / 2, fg);
			} else {
				float ss = 8f * s;
				float sl = c.lineHeight(Fonts.MEDIUM, ss);
				float top = y + (h - lh - sl) / 2;
				text(c, Fonts.SEMIBOLD, fs, name, x + (w - tw) / 2, top, fg);
				text(c, Fonts.MEDIUM, ss, sub, x + (w - c.textWidth(Fonts.MEDIUM, ss, sub)) / 2, top + lh, Theme.alpha(fg, 0.7f));
			}
		}

		@Override
		protected float text(Canvas c, int face, float size, String t, float x, float y, int argb) {
			// no shadow on light keys: it reads as dirt
			c.text(face, size, t, x, y, argb);
			return x + c.textWidth(face, size, t);
		}
	}

	static final class Direction extends HudModule {
		final Setting.Bool degrees = add(new Setting.Bool("degrees", "Show degrees", true));
		private final float[] size = new float[2];
		private static final String[] NAMES = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

		Direction() {
			super("direction", "Direction", "A compass strip that turns with you.", Theme.I_COMPASS, false, 0.5f, 0f);
		}

		@Override
		public float[] measure(Canvas c, Game g) {
			float s = scale.value;
			size[0] = 200 * s;
			size[1] = (degrees.value ? 32 : 22) * s;
			return size;
		}

		@Override
		public void paint(Ui ui, float x, float y, Game g) {
			Canvas c = ui.c;
			float s = scale.value;
			float w = 200 * s, h = 22 * s;
			if (style.value == STYLE_CARD) {
				background(c, x, y, w, h, s);
			}
			float heading = (((g.yaw + 180) % 360) + 360) % 360;
			float span = 110; // degrees visible
			float ppd = w / span;
			float cx = x + w / 2;
			c.pushClip(x + 4 * s, y, w - 8 * s, h + 12 * s);
			for (int a = 0; a < 360; a += 15) {
				float off = a - heading;
				off = ((off + 540) % 360) - 180;
				if (Math.abs(off) > span / 2 + 10) {
					continue;
				}
				float px = cx + off * ppd;
				float fade = 1f - Math.min(1f, Math.abs(off) / (span / 2)) * 0.85f;
				if (a % 45 == 0) {
					String n = NAMES[a / 45];
					float fs = (n.length() == 1 ? 12f : 10f) * s;
					int col = a == 0 ? Theme.ACCENT : color.value;
					text(c, Fonts.SEMIBOLD, fs, n, px - c.textWidth(Fonts.SEMIBOLD, fs, n) / 2, y + (h - c.lineHeight(Fonts.SEMIBOLD, fs)) / 2,
							Theme.alpha(col, fade));
				} else {
					c.fill(px - 0.5f * s, y + h / 2 - 3 * s, Math.max(1, s), 6 * s, Theme.alpha(color.value, 0.45f * fade));
				}
			}
			c.popClip();
			// centre marker
			c.round(cx - 1 * s, y + 2 * s, 2 * s, 4 * s, s, Theme.alpha(color.value, 0.9f));
			if (degrees.value) {
				String d = Math.round(heading) % 360 + "\u00B0";
				float fs = 9.5f * s;
				text(c, Fonts.MEDIUM, fs, d, cx - c.textWidth(Fonts.MEDIUM, fs, d) / 2, y + h + 1 * s, Theme.alpha(color.value, 0.75f));
			}
		}
	}

	// ── mechanics ────────────────────────────────────────────────────────

	static final class ToggleSprint extends HudModule {
		final Setting.Bool sneak = add(new Setting.Bool("sneak", "Toggle sneak too", false));
		final Setting.Bool status = add(new Setting.Bool("status", "Show status on HUD", true));
		private boolean sprint, sneaking, forced, forcedSneak;

		ToggleSprint() {
			super("togglesprint", "Toggle Sprint", "Press sprint once to keep sprinting (optionally sneak too).", MECHANIC, Theme.I_FOOTPRINTS,
					false, 0f, 1f);
		}

		@Override
		public boolean onKey(int key, int action, Game g) {
			if (!g.playing || action == 2) {
				return false;
			}
			if (key == g.keys[Game.SPRINT] && key > 7) {
				if (action == 1) {
					sprint = !sprint;
					g.sprintToggled = sprint;
				}
				return true; // we drive the binding
			}
			if (sneak.value && key == g.keys[Game.SNEAK] && key > 7) {
				if (action == 1) {
					sneaking = !sneaking;
					g.sneakToggled = sneaking;
				}
				return true;
			}
			return false;
		}

		@Override
		public void frame(Game g) {
			g.sprintToggled = sprint;
			g.sneakToggled = sneaking && sneak.value;
			if (!g.playing) {
				return;
			}
			McBridge mc = mc();
			if (sprint || forced) {
				mc.setPressed("sprint", sprint);
				forced = sprint;
			}
			boolean sn = sneaking && sneak.value;
			if (sn || forcedSneak) {
				mc.setPressed("sneak", sn);
				forcedSneak = sn;
			}
		}

		@Override
		protected void onDisable() {
			McBridge mc = mc();
			if (mc != null) {
				if (forced) {
					mc.setPressed("sprint", false);
				}
				if (forcedSneak) {
					mc.setPressed("sneak", false);
				}
			}
			sprint = sneaking = forced = forcedSneak = false;
		}

		@Override
		public boolean visible(Game g) {
			return status.value && (g.sample || g.sprintToggled || g.sneakToggled);
		}

		@Override
		protected int lines(Game g, String[] l, String[] v) {
			l[0] = null;
			if (g.sample) {
				v[0] = "Sprinting (Toggled)";
			} else if (g.sneakToggled) {
				v[0] = "Sneaking (Toggled)";
			} else {
				v[0] = "Sprinting (Toggled)";
			}
			return 1;
		}
	}

	static final class ZoomModule extends Module {
		final Setting.Key key = add(new Setting.Key("key", "Zoom key", 67));
		final Setting.Num level = add(new Setting.Num("level", "Zoom", 4f, 1.5f, 12f, 0.5f, "x"));
		final Setting.Bool smooth = add(new Setting.Bool("smooth", "Smooth zoom", true));
		final Setting.Bool scroll = add(new Setting.Bool("scroll", "Scroll to adjust", true));
		final Setting.Bool cinematic = add(new Setting.Bool("cinematic", "Cinematic camera", false));
		private float current = 1, extra = 1;
		private boolean held, smoothSet, smoothWas;
		private long last;

		ZoomModule() {
			super("zoom", "Zoom", "Hold a key to zoom in like a spyglass. Scroll to zoom further.", VISUAL, Theme.I_ZOOM, true);
		}

		@Override
		public void frame(Game g) {
			boolean want = g.playing && key.value >= 0 && Keys.isDown(g.window, key.value);
			if (want && !held) {
				extra = 1;
			}
			held = want;
			float target = held ? Math.max(1f, Math.min(50f, level.value * extra)) : 1f;
			long now = System.nanoTime();
			float dt = last == 0 ? 0.016f : Math.min(0.1f, (now - last) / 1e9f);
			last = now;
			if (smooth.value) {
				current += (target - current) * (1f - (float) Math.exp(-14 * dt));
				if (Math.abs(target - current) < 0.002f) {
					current = target;
				}
			} else {
				current = target;
			}
			Zoom.factor = current;
			g.zoom = current;
			McBridge mc = mc();
			if (cinematic.value && held && !smoothSet) {
				smoothWas = mc.smoothCamera();
				mc.setSmoothCamera(true);
				smoothSet = true;
			} else if (smoothSet && !held) {
				mc.setSmoothCamera(smoothWas);
				smoothSet = false;
			}
		}

		@Override
		public boolean onScroll(double dy, Game g) {
			if (!held || !scroll.value || dy == 0) {
				return false;
			}
			extra = (float) Math.max(0.4, Math.min(6, extra * (dy > 0 ? 1.25 : 0.8)));
			return true;
		}

		@Override
		protected void onDisable() {
			held = false;
			current = 1;
			Zoom.factor = 1;
			if (smoothSet) {
				mc().setSmoothCamera(smoothWas);
				smoothSet = false;
			}
		}
	}

	static final class Fullbright extends Module {
		private double old = Double.NaN;
		private int tick;

		Fullbright() {
			super("fullbright", "Fullbright", "See in the dark: turns the brightness all the way up.", VISUAL, Theme.I_SUN, false);
		}

		@Override
		public void frame(Game g) {
			if (!g.inWorld || tick++ % 30 != 0) {
				return;
			}
			McBridge mc = mc();
			double now = mc.gamma();
			if (now < 15) {
				if (Double.isNaN(old)) {
					old = now;
				}
				mc.setGamma(16);
			}
		}

		@Override
		protected void onDisable() {
			McBridge mc = mc();
			if (!Double.isNaN(old)) {
				mc.setGamma(old);
				old = Double.NaN;
			} else if (mc != null && mc.gamma() > 1) {
				mc.setGamma(1); // left over from a session that ended with fullbright on
			}
		}
	}
}
