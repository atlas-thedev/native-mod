package xyz.nativelaunch.ui.view;

import xyz.nativelaunch.relay.Model;
import xyz.nativelaunch.relay.RelayClient;
import xyz.nativelaunch.ui.Avatars;
import xyz.nativelaunch.ui.McBridge;
import xyz.nativelaunch.ui.TextLayout;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.UiRuntime;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.ui.gfx.Image;

import java.util.List;

/** The Native title screen: artwork background, launcher-style menu and a live Relay friends card. */
public final class TitleView {
	private static final String[] BACKGROUNDS = {"bg1.jpg", "bg2.jpg", "bg3.jpg"};
	private final Image[] bg = new Image[BACKGROUNDS.length];
	private boolean bgLoaded;
	private Image logo;
	private long shownAt;
	private Object lastScreen;
	private boolean confirmQuit;

	private void load() {
		if (bgLoaded) {
			return;
		}
		bgLoaded = true;
		for (int i = 0; i < BACKGROUNDS.length; i++) {
			bg[i] = Image.resource("/assets/native/ui/" + BACKGROUNDS[i]);
		}
		logo = Image.resource("/assets/native/ui/logo.png");
	}

	public void escape() {
		confirmQuit = false;
	}

	public void draw(Ui ui, Object screen) {
		load();
		Canvas c = ui.c;
		McBridge mc = UiRuntime.mc();
		if (screen != lastScreen) {
			lastScreen = screen;
			if (shownAt == 0) {
				shownAt = ui.now;
				ui.setAnim("title#in", 0);
			}
		}
		float W = c.width(), H = c.height();
		float in = ui.anim("title#in", 1f, 4f);
		drawBackground(ui, W, H);

		float x0 = Math.max(36, W * 0.065f);
		float top = Math.max(40, H * 0.16f);
		boolean wide = W >= 940;

		// brand
		c.pushAlpha(in);
		float slide = (1 - in) * 24;
		float ly = top - slide * 0.5f;
		if (logo != null) {
			c.stamp("logo", logo, x0, ly, 46, 46, true, 0xFFFFFFFF);
		}
		c.text(Fonts.BOLD, 34, "Native", x0 + 60, ly - 3, Theme.TEXT_STRONG);
		c.text(Fonts.MEDIUM, 12.5f, "Minecraft " + UiRuntime.minecraftVersion(), x0 + 62, ly + 33, Theme.TEXT_SECONDARY);

		// menu
		float bw = 300, bh = 46, gap = 8;
		float by = ly + 84;
		String[][] items = {
				{"singleplayer", mc.tr("menu.singleplayer", "Singleplayer")},
				{"multiplayer", mc.tr("menu.multiplayer", "Multiplayer")},
				{"realms", mc.tr("menu.online", "Minecraft Realms")},
				{"mods", "Mods"},
				{"relay", "Relay"},
				{"nativemods", "Native Mods"},
				{"options", mc.tr("menu.options", "Options...").replace("...", "").replace("\u2026", "")},
		};
		int[] icons = {Theme.I_USER, Theme.I_GLOBE, Theme.I_CLOUD, Theme.I_PUZZLE, Theme.I_MESSAGE, Theme.I_SPARKLES, Theme.I_SETTINGS};
		int index = 0;
		for (int i = 0; i < items.length; i++) {
			String id = items[i][0];
			if ("mods".equals(id) && !mc.hasMods()) {
				continue;
			}
			if ("relay".equals(id) && wide) {
				continue;
			}
			float appear = clamp01((ui.now - shownAt - 80 - index * 45) / 380f);
			float e = 1 - (1 - appear) * (1 - appear) * (1 - appear);
			c.pushAlpha(e);
			float bx = x0 - (1 - e) * 26;
			if (menuButton(ui, "menu:" + id, bx, by, bw, bh, items[i][1], icons[i], index == 0)) {
				act(id, screen);
			}
			c.popAlpha();
			by += bh + gap;
			index++;
		}
		by += 10;
		float half = (bw - gap) / 2;
		if (confirmQuit) {
			if (ui.button("menu:quit-yes", x0, by, half, 38, "Quit game", Theme.I_POWER, Ui.BTN_DANGER)) {
				mc.quit();
			}
			if (ui.button("menu:quit-no", x0 + half + gap, by, half, 38, "Cancel", 0, Ui.BTN_GLASS)) {
				confirmQuit = false;
			}
		} else {
			if (ui.button("menu:quit", x0, by, half, 38, mc.tr("menu.quit", "Quit Game"), Theme.I_POWER, Ui.BTN_GLASS)) {
				confirmQuit = true;
			}
			if (ui.button("menu:classic", x0 + half + gap, by, half, 38, "Classic menu", Theme.I_LAYERS, Ui.BTN_GHOST)) {
				UiRuntime.useClassicTitle();
			}
		}
		c.popAlpha();

		// relay card
		if (wide) {
			float cw = Math.min(340, W * 0.3f);
			float cx = W - x0 - cw;
			float ch = Math.min(H - top - 80, 460);
			float appear = clamp01((ui.now - shownAt - 250) / 450f);
			float e = 1 - (1 - appear) * (1 - appear) * (1 - appear);
			c.pushAlpha(e);
			relayCard(ui, cx + (1 - e) * 30, top, cw, ch, screen);
			c.popAlpha();
		}

		// footer
		float fs = 11;
		float fy = H - 26;
		c.text(Fonts.REGULAR, fs, "Copyright Mojang AB. Do not distribute!", x0, fy, 0x99FFFFFF);
		String right = "Native Client";
		c.text(Fonts.MEDIUM, fs, right, W - x0 - c.textWidth(Fonts.MEDIUM, fs, right), fy, 0x99FFFFFF);
	}

	private void act(String id, Object screen) {
		McBridge mc = UiRuntime.mc();
		if ("relay".equals(id)) {
			UiRuntime.openRelay(screen);
		} else if ("nativemods".equals(id)) {
			UiRuntime.openMenu(screen);
		} else {
			mc.open(id, screen);
		}
	}

	private boolean menuButton(Ui ui, String id, float x, float y, float w, float h, String label, int icon, boolean primary) {
		Canvas c = ui.c;
		boolean over = ui.hover(x, y, w, h);
		boolean click = ui.clicked(id, x, y, w, h);
		float hv = ui.anim(id + "#h", over, 12f);
		float pr = ui.anim(id + "#p", ui.isPressing(id), 22f);
		float dx = hv * 4 - pr * 1.5f;
		x += dx;
		int bg, border, fg, iconBg, iconFg;
		if (primary) {
			bg = Theme.mix(0xF2F4F4F5, 0xFFFFFFFF, hv);
			border = 0;
			fg = Theme.SOLID_FG;
			iconBg = 0x14000000;
			iconFg = Theme.SOLID_FG;
		} else {
			bg = Theme.mix(0xB308090C, 0xE00E1014, hv);
			border = Theme.mix(Theme.HAIRLINE, Theme.BORDER_HOVER, hv);
			fg = Theme.mix(Theme.TEXT, Theme.TEXT_STRONG, hv);
			iconBg = Theme.mix(Theme.SUBTLE, Theme.SUBTLE_HOVER, hv);
			iconFg = fg;
		}
		if (hv > 0.01f) {
			c.shadow(x, y + 4, w, h, 12, 16, Theme.alpha(0x80000000, hv));
		}
		c.round(x, y, w, h, 12, bg);
		if (border != 0) {
			c.outline(x, y, w, h, 12, 1, border);
		}
		float is = h - 14;
		c.round(x + 7, y + 7, is, is, 8, iconBg);
		c.icon(icon, 16, x + 7 + is / 2, y + h / 2, iconFg);
		c.text(Fonts.SEMIBOLD, 14, label, x + 7 + is + 12, y + (h - c.lineHeight(Fonts.SEMIBOLD, 14)) / 2, fg);
		c.icon(Theme.I_CHEVRON_RIGHT, 15, x + w - 20 + hv * 3, y + h / 2, Theme.alpha(fg, 0.35f + 0.65f * hv));
		return click;
	}

	/** Just the artwork (used behind Relay when it is opened from the menu). */
	public void background(Ui ui) {
		load();
		drawBackground(ui, ui.c.width(), ui.c.height());
	}

	private void drawBackground(Ui ui, float W, float H) {
		Canvas c = ui.c;
		c.fill(0, 0, W, H, 0xFF050608);
		int count = 0;
		for (Image i : bg) {
			if (i != null) {
				count++;
			}
		}
		if (count > 0) {
			double period = 16000, fade = 2200;
			long t = ui.now;
			int cur = (int) ((t / (long) period) % count);
			double phase = t % (long) period;
			Image a = nth(cur), b = nth((cur + 1) % count);
			float zoomA = 1.04f + 0.08f * (float) (phase / period);
			c.imageCover(a, 0, 0, W, H, zoomA, 0.2f, 0, 0xFFFFFFFF);
			if (phase > period - fade && b != a) {
				float k = (float) ((phase - (period - fade)) / fade);
				c.imageCover(b, 0, 0, W, H, 1.04f, 0.2f, 0, Theme.alpha(0xFFFFFFFF, k));
			}
		}
		c.gradientH(0, 0, W * 0.7f, H, 0xF0000000, 0x00000000);
		c.gradientV(0, H * 0.5f, W, H * 0.5f, 0x00000000, 0xD0000000);
		c.gradientV(0, 0, W, 140, 0x99000000, 0x00000000);
	}

	private Image nth(int n) {
		int k = 0;
		for (Image i : bg) {
			if (i != null) {
				if (k == n) {
					return i;
				}
				k++;
			}
		}
		return null;
	}

	private void relayCard(Ui ui, float x, float y, float w, float h, Object screen) {
		Canvas c = ui.c;
		RelayClient client = RelayClient.get();
		c.shadow(x, y + 8, w, h, 16, 28, 0x80000000);
		c.round(x, y, w, h, 16, 0xD908090C);
		c.outline(x, y, w, h, 16, 1, Theme.HAIRLINE);
		float pad = 16;
		c.icon(Theme.I_USERS, 16, x + pad + 8, y + 26, Theme.TEXT_STRONG);
		c.text(Fonts.SEMIBOLD, 15, "Friends", x + pad + 24, y + 26 - c.lineHeight(Fonts.SEMIBOLD, 15) / 2, Theme.TEXT_STRONG);
		List<Model.Friend> friends = client == null ? java.util.Collections.<Model.Friend>emptyList() : client.friends;
		int online = 0;
		for (Model.Friend f : friends) {
			if (f.online) {
				online++;
			}
		}
		if (client != null && client.state != RelayClient.State.NO_ACCOUNT) {
			String chip = online + " online";
			float tw = c.textWidth(Fonts.SEMIBOLD, 11, chip);
			float chx = x + w - pad - tw - 22;
			c.round(chx, y + 15, tw + 22, 22, 11, 0x1A22C55E);
			c.circle(chx + 10, y + 26, 3, Theme.ONLINE);
			c.text(Fonts.SEMIBOLD, 11, chip, chx + 16, y + 26 - c.lineHeight(Fonts.SEMIBOLD, 11) / 2, 0xFF86EFAC);
		}
		c.fill(x + pad, y + 50, w - pad * 2, 1, Theme.HAIRLINE);
		float listY = y + 58, listH = h - 58 - 64;
		if (client == null || client.state == RelayClient.State.NO_ACCOUNT) {
			empty(c, x, listY, w, listH, Theme.I_LOCK, "Not signed in", "Start Minecraft from the Native Client to chat with your friends here.");
		} else if (!client.friendsLoaded) {
			if (client.state == RelayClient.State.OFFLINE) {
				empty(c, x, listY, w, listH, Theme.I_WIFI_OFF, "Relay is offline", "Retrying in the background.");
			} else {
				ui.dots(x + w / 2, listY + listH / 2, Theme.TEXT_SECONDARY);
			}
		} else if (friends.isEmpty()) {
			empty(c, x, listY, w, listH, Theme.I_USER_PLUS, "No friends yet", "Add friends in the Native launcher's Relay tab.");
		} else {
			c.pushClip(x, listY, w, listH);
			float ry = listY;
			for (Model.Friend f : friends) {
				if (ry > listY + listH) {
					break;
				}
				float rh = 50;
				String id = "card:" + f.id;
				boolean over = ui.hover(x + 8, ry, w - 16, rh);
				float hv = ui.anim(id, over, 14f);
				if (ui.clicked(id, x + 8, ry, w - 16, rh)) {
					RelayView.selectNext("dm:" + f.id);
					UiRuntime.openRelay(screen);
				}
				c.round(x + 8, ry, w - 16, rh, 10, Theme.alpha(Theme.SUBTLE_HOVER, hv));
				float av = 34;
				c.pushAlpha(f.online ? 1f : 0.55f);
				Avatars.draw(c, client.api(), f.name, f.skin, x + pad, ry + (rh - av) / 2, av, 9);
				c.popAlpha();
				c.circle(x + pad + av - 2, ry + (rh - av) / 2 + av - 2, 5.5f, 0xFF08090C);
				c.circle(x + pad + av - 2, ry + (rh - av) / 2 + av - 2, 3.8f, f.dot());
				float tx = x + pad + av + 12;
				float maxW = w - (tx - x) - pad - (f.unread > 0 ? 30 : 0);
				c.text(Fonts.SEMIBOLD, 13, c.ellipsize(Fonts.SEMIBOLD, 13, f.display(), maxW), tx, ry + 8, f.online ? Theme.TEXT_STRONG : Theme.TEXT_SECONDARY);
				String sub = f.line();
				c.text(Fonts.REGULAR, 11, c.ellipsize(Fonts.REGULAR, 11, sub, maxW), tx, ry + 27, f.online ? Theme.TEXT_SECONDARY : Theme.TEXT_MUTED);
				if (f.unread > 0) {
					badge(c, x + w - pad - 8, ry + rh / 2, f.unread);
				}
				ry += rh + 2;
			}
			c.popClip();
		}
		int unread = client == null ? 0 : client.unreadTotal();
		String label = unread > 0 ? "Open Relay \u00b7 " + unread + " new" : "Open Relay";
		if (ui.button("card:open", x + pad, y + h - 52, w - pad * 2, 38, label, Theme.I_MESSAGE, Ui.BTN_PRIMARY)) {
			UiRuntime.openRelay(screen);
		}
	}

	static void badge(Canvas c, float rightX, float cy, int n) {
		String s = n > 99 ? "99+" : String.valueOf(n);
		float tw = c.textWidth(Fonts.BOLD, 10.5f, s);
		float bw = Math.max(18, tw + 10);
		c.round(rightX - bw, cy - 9, bw, 18, 9, Theme.DANGER);
		c.text(Fonts.BOLD, 10.5f, s, rightX - bw + (bw - tw) / 2, cy - c.lineHeight(Fonts.BOLD, 10.5f) / 2, 0xFFFFFFFF);
	}

	static void empty(Canvas c, float x, float y, float w, float h, int icon, String title, String body) {
		float cy = y + h / 2 - 34;
		c.round(x + w / 2 - 22, cy - 22, 44, 44, 12, Theme.SUBTLE);
		c.icon(icon, 20, x + w / 2, cy, Theme.TEXT_SECONDARY);
		float tw = c.textWidth(Fonts.SEMIBOLD, 13.5f, title);
		c.text(Fonts.SEMIBOLD, 13.5f, title, x + (w - tw) / 2, cy + 32, Theme.TEXT_STRONG);
		float ly = cy + 54;
		for (String line : TextLayout.wrap(c, Fonts.REGULAR, 11.5f, body, Math.min(w - 48, 280))) {
			float lw = c.textWidth(Fonts.REGULAR, 11.5f, line);
			c.text(Fonts.REGULAR, 11.5f, line, x + (w - lw) / 2, ly, Theme.TEXT_MUTED);
			ly += 17;
		}
	}

	private static float clamp01(float v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}
}
