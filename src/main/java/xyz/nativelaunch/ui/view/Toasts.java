package xyz.nativelaunch.ui.view;

import xyz.nativelaunch.relay.Model;
import xyz.nativelaunch.relay.RelayClient;
import xyz.nativelaunch.ui.Avatars;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.UiRuntime;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.voice.Voice;

import java.util.ArrayList;
import java.util.List;

/** Small message cards in the top-right corner while playing. Purely visual: they never take input. */
public final class Toasts {
	private static final long LIFE = 6000, FADE = 260;
	private static final int MAX = 3;
	private final List<Model.Notice> shown = new ArrayList<Model.Notice>();
	private final List<Long> born = new ArrayList<Long>();

	/** Conversation of the newest card still on screen, so the chat key can jump straight to it. */
	public String latestKey() {
		return shown.isEmpty() ? null : shown.get(0).convKey;
	}

	public boolean active() {
		return !shown.isEmpty() || Voice.hasIncoming() || Voice.calling() != null;
	}

	public void draw(Ui ui) {
		RelayClient client = RelayClient.get();
		long now = System.currentTimeMillis();
		if (client != null) {
			Model.Notice n;
			while ((n = client.notices.poll()) != null) {
				if (n.convKey != null && n.convKey.equals(client.viewing)) {
					continue;
				}
				// one card per conversation: a newer message replaces the older card
				for (int i = shown.size() - 1; i >= 0; i--) {
					String k = shown.get(i).convKey;
					if (k != null && k.equals(n.convKey)) {
						shown.remove(i);
						born.remove(i);
					}
				}
				shown.add(0, n);
				born.add(0, now);
				while (shown.size() > MAX) {
					shown.remove(shown.size() - 1);
					born.remove(born.size() - 1);
				}
			}
		}
		for (int i = shown.size() - 1; i >= 0; i--) {
			if (now - born.get(i) > LIFE) {
				shown.remove(i);
				born.remove(i);
			}
		}
		Canvas c = ui.c;
		String api = client == null ? null : client.api();
		float w = 290, h = 66, gap = 8;
		float x = c.width() - w - 14;
		float y = 14;
		y += callCard(ui, api, x, y, w) ;
		if (shown.isEmpty()) {
			return;
		}
		String hint = "Press " + UiRuntime.keyName(UiRuntime.config().relayKey) + " to open chat";
		for (int i = 0; i < shown.size(); i++) {
			Model.Notice n = shown.get(i);
			long age = now - born.get(i);
			float in = Math.min(1f, age / (float) FADE);
			float out = Math.min(1f, Math.max(0f, (LIFE - age) / (float) FADE));
			float a = ease(Math.min(in, out));
			float slide = (1f - ease(in)) * 24f;
			c.pushAlpha(a);
			float cx = x + slide;
			c.shadow(cx, y + 3, w, h, 14, 14, 0x66000000);
			c.round(cx, y, w, h, 14, 0xF00A0B0E);
			c.outline(cx, y, w, h, 14, 1, Theme.HAIRLINE_STRONG);
			Avatars.draw(c, api, n.avatarName, n.skin, cx + 12, y + 12, 38, 10);
			float tx = cx + 60, tw = w - 72;
			c.text(Fonts.SEMIBOLD, 13, c.ellipsize(Fonts.SEMIBOLD, 13, n.title == null ? "" : n.title, tw), tx, y + 10, Theme.TEXT_STRONG);
			String body = n.body == null ? "" : n.body.replace('\n', ' ');
			c.text(Fonts.REGULAR, 12, c.ellipsize(Fonts.REGULAR, 12, body, tw), tx, y + 27, Theme.TEXT);
			if (i == 0) {
				c.text(Fonts.REGULAR, 10, RelayClient.REQUESTS_KEY.equals(n.convKey) ? hint.replace("open chat", "respond") : hint, tx, y + 47, Theme.TEXT_MUTED);
			}
			// remaining-time bar
			float left = Math.max(0f, 1f - age / (float) LIFE);
			c.fill(cx + 14, y + h - 2, (w - 28) * left, 1, Theme.alpha(Theme.ACCENT, 0.55f));
			c.popAlpha();
			y += (h + gap) * a;
		}
	}

	/** The ring: who is calling, with the keys that answer. Returns the height used (0 when nothing is ringing). */
	private float callCard(Ui ui, String api, float x, float y, float w) {
		Voice.Ring ring = Voice.incoming();
		String calling = ring == null ? Voice.calling() : null;
		if (ring == null && calling == null) {
			return 0;
		}
		Canvas c = ui.c;
		long now = System.currentTimeMillis();
		float h = 76;
		float pulse = (float) ((Math.sin(now / 260.0) + 1) / 2);
		RelayClient client = RelayClient.get();
		String name, skin = null;
		if (ring != null) {
			name = ring.name;
			skin = ring.skin;
		} else {
			Model.Friend f = client == null ? null : client.friend(calling.substring(3));
			name = f == null ? "your friend" : f.display();
			skin = f == null ? null : f.skin;
		}
		int accent = ring != null ? Theme.ONLINE : Theme.ACCENT;
		c.shadow(x, y + 3, w, h, 14, 16, 0x70000000);
		c.round(x, y, w, h, 14, 0xF40A0B0E);
		c.outline(x, y, w, h, 14, 1, Theme.alpha(accent, 0.35f + 0.35f * pulse));
		float ax = x + 14, ay = y + 14;
		if (ring != null) {
			c.circle(ax + 24, ay + 24, 27 + 2 * pulse, Theme.alpha(accent, 0.18f + 0.2f * pulse));
		}
		Avatars.draw(c, api, name, skin, ax, ay, 48, 12);
		float tx = x + 76, tw = w - 90;
		String head = ring != null ? "Incoming voice call" : "Calling...";
		c.text(Fonts.MEDIUM, 10.5f, head, tx, y + 12, accent);
		c.text(Fonts.SEMIBOLD, 14, c.ellipsize(Fonts.SEMIBOLD, 14, name, tw), tx, y + 27, Theme.TEXT_STRONG);
		String hint = ring != null ? "V  accept     N  decline" : "N  cancel the call";
		c.text(Fonts.REGULAR, 10.5f, hint, tx, y + 50, Theme.TEXT_SECONDARY);
		if (ring != null) {
			float left = Math.max(0f, Math.min(1f, (ring.until - now) / (float) (ring.until - ring.at)));
			c.fill(x + 14, y + h - 3, (w - 28) * left, 1.5f, Theme.alpha(accent, 0.6f));
		}
		return h + 8;
	}

	private static float ease(float t) {
		float u = 1f - t;
		return 1f - u * u * u;
	}
}
