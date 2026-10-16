package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.relay.RelayClient;
import xyz.nativelaunch.ui.Avatars;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.Widgets;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.voice.Voice;

import java.util.ArrayList;
import java.util.List;

/** Voice chat: the settings module plus the movable "who is talking" overlay. */
public final class VoiceModules {
	private VoiceModules() {
	}

	static void register() {
		Modules.register(new VoiceChat());
		Modules.register(new VoiceOverlay());
	}

	static final class VoiceChat extends Module {
		static final int PTT = 0, ACTIVATION = 1;
		final Setting.Device micDevice = add(new Setting.Device("micdevice", "Microphone", xyz.nativelaunch.voice.VoiceDevices::inputs));
		final Setting.Level test = add(new Setting.Level("mictest", "Mic test", Voice::micDb, this::marker, this::hot, Voice::micError, Voice::micTest));
		final Setting.Num mic = add(new Setting.Num("mic", "Microphone volume", 100, 0, 300, 5, "%"));
		final Setting.Device outDevice = add(new Setting.Device("outdevice", "Output device", xyz.nativelaunch.voice.VoiceDevices::outputs));
		final Setting.Num volume = add(new Setting.Num("volume", "Voice volume", 100, 0, 200, 5, "%"));
		final Setting.Choice mode = add(new Setting.Choice("mode", "Talk mode", ACTIVATION, "Push to talk", "Voice activation").shorts("PTT", "Voice"));
		final Setting.Key ptt = add(new Setting.Key("ptt", "Push-to-talk key", 66));
		final Setting.Num threshold = add(new Setting.Num("threshold", "Activation level", -42, -70, -10, 1, " dB"));
		final Setting.Bool proximity = add(new Setting.Bool("proximity", "Proximity voice on servers", true));
		final Setting.Num range = add(new Setting.Num("range", "Hearing distance", 48, 8, 96, 4, " blocks"));
		final Setting.Key mute = add(new Setting.Key("mute", "Mute key", -1));
		final Setting.Key deafen = add(new Setting.Key("deafen", "Deafen key", -1));

		/** Where voice activation opens, drawn on the mic test bar (none with push to talk). */
		private double marker() {
			return mode.value == ACTIVATION ? threshold.value : Double.NaN;
		}

		/** Green while the mic would send: above the activation level, or (push to talk) clearly picking up a voice. */
		private boolean hot() {
			return Voice.micDb() >= (mode.value == ACTIVATION ? threshold.value : -50);
		}

		VoiceChat() {
			super("voice", "Voice Chat", "Talk to friends and groups in a call, and to Native players near you on any server.", MECHANIC,
					Theme.I_MIC, true);
		}

		@Override
		protected boolean hidden(Setting s) {
			boolean svc = Voice.svcInstalled(); // Simple Voice Chat does proximity then; Native keeps calls
			return s == ptt && mode.value != PTT || s == threshold && mode.value != ACTIVATION || s == range && (!proximity.value || svc) || s == proximity && svc;
		}

		@Override
		public void frame(Game g) {
			Voice.pushToTalk = mode.value == PTT;
			// in a call the key also works while a screen (Relay chat, menus) is open: you are not in the world then
			Voice.pttHeld = (g.playing || Voice.call() != null) && ptt.value >= 0 && Keys.isDown(g.window, ptt.value);
			Voice.pttKey = ptt.value >= 0 ? Keys.name(ptt.value) : "";
			Voice.thresholdDb = threshold.value;
			Voice.micDevice = micDevice.value;
			Voice.outDevice = outDevice.value;
			Voice.micGain = mic.value / 100f;
			Voice.outVolume = volume.value / 100f;
			Voice.proximityOn = proximity.value;
			Voice.range = range.value;
			Voice.tick(HudModules.mc(), true);
		}

		@Override
		public boolean preview(Ui ui, Game g, float x, float y, float w, float h) {
			Canvas c = ui.c;
			float rw = Math.min(w - 36, 420), rh = 150;
			float rx = x + (w - rw) / 2, ry = y + (h - rh) / 2;
			c.shadow(rx, ry + 3, rw, rh, 14, 16, 0x66000000);
			c.round(rx, ry, rw, rh, 14, 0xD00A0B0E);
			c.outline(rx, ry, rw, rh, 14, 1, Theme.HAIRLINE_STRONG);
			boolean ptt = mode.value == PTT;
			// the microphone and its level (a live reading when the mic is open, a moving sample otherwise)
			double live = Voice.micDb();
			double wave = Math.max(0, Math.sin(ui.now / 260.0) * Math.sin(ui.now / 710.0 + 1.3));
			double db = live > -90 ? live : -62 + 40 * wave;
			boolean open = ptt ? wave > 0.35 || live > -50 : db >= threshold.value;
			c.circle(rx + 36, ry + 38, 17, open ? 0x3322C55E : Theme.SUBTLE_HOVER);
			c.icon(Theme.I_MIC, 16, rx + 36, ry + 38, open ? Theme.ONLINE : Theme.TEXT_SECONDARY);
			float mx = rx + 64, mw = rw - 64 - 22;
			c.text(Fonts.SEMIBOLD, 11.5f, "You", mx, ry + 17, Theme.TEXT_STRONG);
			String state = open ? "Talking" : (ptt ? "Hold " + (this.ptt.value >= 0 ? Keys.name(this.ptt.value) : "your key") + " to talk" : "Listening");
			c.text(Fonts.REGULAR, 10.5f, state, rx + rw - 22 - c.textWidth(Fonts.REGULAR, 10.5f, state), ry + 18, open ? Theme.ONLINE : Theme.TEXT_MUTED);
			int bars = 24;
			float bw = (mw - (bars - 1) * 3) / bars;
			float lvl = (float) Math.max(0, Math.min(1, (db + 70) / 60.0));
			for (int i = 0; i < bars; i++) {
				boolean on = i / (float) bars < lvl;
				c.round(mx + i * (bw + 3), ry + 42, bw, 14, 2, on ? (open ? Theme.ONLINE : Theme.TEXT_SECONDARY) : Theme.SUBTLE_HOVER);
			}
			if (!ptt) {
				float tx = mx + mw * (float) Math.max(0, Math.min(1, (threshold.value + 70) / 60.0));
				c.fill(tx - 1, ry + 38, 2, 22, 0xFFFDE68A);
			}
			c.fill(rx + 18, ry + 76, rw - 36, 1, Theme.HAIRLINE);
			// who is in the call
			String[] names = {"Alex", "Sam", "Riley"};
			for (int i = 0; i < names.length; i++) {
				float cx = rx + 38 + i * 70, cy = ry + 108;
				boolean talk = ((ui.now / 900) % 3) == i;
				if (talk) {
					c.circle(cx, cy, 19, Theme.ONLINE);
				}
				c.circle(cx, cy, talk ? 17 : 18, 0xFF1F2026);
				String ini = names[i].substring(0, 1);
				c.text(Fonts.SEMIBOLD, 13, ini, cx - c.textWidth(Fonts.SEMIBOLD, 13, ini) / 2, cy - c.lineHeight(Fonts.SEMIBOLD, 13) / 2, Theme.TEXT_STRONG);
				c.text(Fonts.REGULAR, 9.5f, names[i], cx - c.textWidth(Fonts.REGULAR, 9.5f, names[i]) / 2, cy + 22, Theme.TEXT_MUTED);
			}
			return true;
		}

		@Override
		public boolean onKey(int key, int action, Game g) {
			if (!g.playing || action != 1 || key < 0) {
				return false;
			}
			if (key == mute.value) {
				Voice.toggleMute();
				return true;
			}
			if (key == deafen.value) {
				Voice.toggleDeafen();
				return true;
			}
			return false;
		}

		@Override
		protected void onDisable() {
			Voice.shutdown();
		}
	}

	static final class VoiceOverlay extends HudModule {
		final Setting.Bool names = add(new Setting.Bool("names", "Show names", true));
		final Setting.Choice layout = add(new Setting.Choice("layout", "Layout", 0, "Vertical", "Horizontal"));
		final Setting.Bool nearby = add(new Setting.Bool("nearby", "Show nearby speakers", true));
		private final List<Row> rows = new ArrayList<Row>();
		private final float[] size = new float[2];

		static final class Row {
			String name, skin, userId;
			boolean speaking, muted, deafened, self;
		}

		VoiceOverlay() {
			super("voice-overlay", "Voice Overlay", "Faces of everyone in your voice call, with a green ring while they talk.", Theme.I_HEADPHONES,
					true, 0f, 0.3f);
			drop(style, labels);
		}

		@Override
		public boolean visible(Game g) {
			return collect(false) > 0;
		}

		private Row row(int i) {
			while (rows.size() <= i) {
				rows.add(new Row());
			}
			return rows.get(i);
		}

		/** Fills {@link #rows}; with {@code preview} a sample call when there is nothing live (HUD editor, menu). */
		private int collect(boolean preview) {
			int n = 0;
			String me = Voice.me();
			Voice.Room call = Voice.call();
			if (call != null) {
				for (Voice.Member m : call.members()) {
					Row r = row(n++);
					r.self = me != null && me.equals(m.userId);
					r.name = r.self ? "You" : m.ign != null ? m.ign : m.name;
					r.userId = r.self ? null : m.userId;
					r.skin = m.skin;
					r.speaking = Voice.speaking(m);
					r.muted = r.self ? Voice.muted : m.muted;
					r.deafened = r.self ? Voice.deafened : m.deafened;
				}
			}
			Voice.Room prox = Voice.proximity();
			if (nearby.value && prox != null) {
				for (Voice.Member m : prox.members()) {
					if (me != null && me.equals(m.userId) || !Voice.speaking(m) || inCall(call, m.userId)) {
						continue;
					}
					Row r = row(n++);
					r.self = false;
					r.name = m.ign != null ? m.ign : m.name;
					r.userId = m.userId;
					r.skin = m.skin;
					r.speaking = true;
					r.muted = r.deafened = false;
				}
			}
			if (n == 0 && preview) {
				RelayClient client = RelayClient.get();
				Row a = row(n++);
				a.self = true;
				a.name = "You";
				a.userId = null;
				a.skin = Avatars.self(HudModules.mc() == null ? null : HudModules.mc().username());
				a.speaking = true;
				a.muted = a.deafened = false;
				Row b = row(n++);
				b.self = false;
				b.name = client != null && !client.friends.isEmpty() ? client.friends.get(0).display() : "Friend";
				b.skin = client != null && !client.friends.isEmpty() ? client.friends.get(0).skin : null;
				b.userId = null;
				b.speaking = false;
				b.muted = true;
				b.deafened = false;
			}
			return n;
		}

		private static boolean inCall(Voice.Room call, String userId) {
			if (call == null || userId == null) {
				return false;
			}
			for (Voice.Member m : call.members()) {
				if (userId.equals(m.userId)) {
					return true;
				}
			}
			return false;
		}

		@Override
		public String pickAt(Canvas c, Game g, float lx, float ly) {
			float[] sz = measure(c, g);
			int n = collect(true);
			float s = scale.value, face = 22 * s, gap = 6 * s, pad = 6 * s;
			for (int i = 0; i < n; i++) {
				float fx = layout.value == 1 ? pad + i * (face + gap) : pad;
				float fy = layout.value == 1 ? pad : pad + i * (face + gap);
				float rw = layout.value == 1 ? face : sz[0] - pad * 2;
				if (lx >= fx - gap / 2 && lx <= fx + rw + gap / 2 && ly >= fy - gap / 2 && ly <= fy + face + gap / 2) {
					return rows.get(i).userId;
				}
			}
			return null;
		}

		@Override
		public float[] measure(Canvas c, Game g) {
			int n = collect(true);
			float s = scale.value, face = 22 * s, gap = 6 * s, pad = 6 * s;
			if (layout.value == 1) {
				size[0] = pad * 2 + n * face + Math.max(0, n - 1) * gap;
				size[1] = pad * 2 + face;
				return size;
			}
			float text = 0;
			if (names.value) {
				for (int i = 0; i < n; i++) {
					text = Math.max(text, c.textWidth(Fonts.SEMIBOLD, 12 * s, rows.get(i).name));
				}
				text += 8 * s;
			}
			size[0] = pad * 2 + face + text;
			size[1] = pad * 2 + n * face + Math.max(0, n - 1) * gap;
			return size;
		}

		@Override
		public void paint(Ui ui, float x, float y, Game g) {
			Canvas c = ui.c;
			float[] sz = measure(c, g);
			int n = collect(true);
			float s = scale.value, face = 22 * s, gap = 6 * s, pad = 6 * s;
			background(c, x, y, sz[0], sz[1], s);
			String api = Avatars.api();
			for (int i = 0; i < n; i++) {
				Row r = rows.get(i);
				float fx = layout.value == 1 ? x + pad + i * (face + gap) : x + pad;
				float fy = layout.value == 1 ? y + pad : y + pad + i * (face + gap);
				float cx = fx + face / 2, cy = fy + face / 2;
				c.circle(cx, cy, face / 2 + 2 * s, r.speaking ? 0xFF22C55E : 0x40FFFFFF);
				c.circle(cx, cy, face / 2 + 0.5f * s, 0xFF08090C);
				Avatars.drawRound(c, api, r.name, r.skin, fx, fy, face);
				if (r.muted || r.deafened) {
					float bx = fx + face - 3 * s, by = fy + face - 3 * s, br = 5.5f * s;
					c.circle(bx, by, br, Theme.DANGER);
					c.icon(r.deafened ? Theme.I_HEADPHONES_OFF : Theme.I_MIC_OFF, 7.5f * s, bx, by, 0xFFFFFFFF);
				}
				if (names.value && layout.value == 0) {
					float ty = cy - c.lineHeight(Fonts.SEMIBOLD, 12 * s) / 2;
					text(c, s, r.name, fx + face + 8 * s, ty, r.speaking ? 0xFFFFFFFF : Theme.alpha(color.value, 0.7f));
				}
			}
		}
	}

	/** One "face  name  ----o----  120%" row for a player's own volume (Relay call panel, HUD editor bubble). */
	public static void volumeRow(Ui ui, String id, String userId, String name, String skin, boolean speaking, float x, float y, float w) {
		Canvas c = ui.c;
		float face = 20, cy = y + 15;
		c.circle(x + face / 2, cy, face / 2 + 2, speaking ? 0xFF22C55E : 0x26FFFFFF);
		c.circle(x + face / 2, cy, face / 2 + 0.5f, 0xFF08090C);
		Avatars.drawRound(c, Avatars.api(), name, skin, x, cy - face / 2, face);
		float nameW = Math.min(110, w * 0.32f);
		c.text(Fonts.MEDIUM, 12, c.ellipsize(Fonts.MEDIUM, 12, name, nameW), x + face + 8, cy - c.lineHeight(Fonts.MEDIUM, 12) / 2, Theme.TEXT_STRONG);
		Setting.Num n = VOL.get(userId);
		if (n == null) {
			n = new Setting.Num("vol", "Volume", 100, 0, 200, 5, "%");
			VOL.put(userId, n);
		}
		if (!ui.isPressing(id)) {
			n.value = Math.round(Voice.volume(userId) * 100);
		}
		String pct = n.text();
		float vw = 40, sx = x + face + 8 + nameW + 12, sw = Math.max(40, x + w - vw - 10 - sx);
		if (Widgets.slider(ui, id, sx, cy - 9, sw, n)) {
			Voice.setVolume(userId, n.value / 100f);
		}
		c.text(Fonts.SEMIBOLD, 11.5f, pct, x + w - c.textWidth(Fonts.SEMIBOLD, 11.5f, pct), cy - c.lineHeight(Fonts.SEMIBOLD, 11.5f) / 2,
				n.value == 0 ? Theme.DANGER : Theme.TEXT_SECONDARY);
	}

	private static final java.util.Map<String, Setting.Num> VOL = new java.util.HashMap<String, Setting.Num>();
}
