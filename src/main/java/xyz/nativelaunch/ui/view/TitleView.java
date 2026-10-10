package xyz.nativelaunch.ui.view;

import xyz.nativelaunch.relay.Model;
import xyz.nativelaunch.relay.RelayClient;
import xyz.nativelaunch.ui.Ads;
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
	/**
	 * One still picture: the artwork of this Minecraft version. The launcher copies the same picture it shows on the
	 * instance card to {@code <game dir>/.native/version-art.jpg} at launch; without it the stock artwork is used.
	 */
	private static volatile Image art;
	private static boolean artStarted;
	private long artShownAt;
	private boolean bgLoaded;
	private Image logo;
	private long shownAt;
	private Object lastScreen;
	private boolean confirmQuit;
	private final java.util.Set<String> hiddenAds = new java.util.HashSet<>();
	private int adIndex;
	private long adSwitchAt;
	private static final long AD_ROTATE_MS = 12_000;

	private void load() {
		if (bgLoaded) {
			return;
		}
		bgLoaded = true;
		startArt();
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

		// relay card + ad card (the same ads as the launcher's Home)
		List<Ads.Ad> ads = visibleAds();
		if (wide) {
			float cw = Math.min(340, W * 0.3f);
			float cx = W - x0 - cw;
			float avail = Math.min(H - top - 80, 460 + 12 + adHeight(cw));
			float adH = adHeight(cw);
			boolean showAd = !ads.isEmpty() && avail - adH - 12 >= 230;
			float ch = showAd ? Math.min(avail - adH - 12, 460) : Math.min(H - top - 80, 460);
			float appear = clamp01((ui.now - shownAt - 250) / 450f);
			float e = 1 - (1 - appear) * (1 - appear) * (1 - appear);
			c.pushAlpha(e);
			relayCard(ui, cx + (1 - e) * 30, top, cw, ch, screen);
			c.popAlpha();
			if (showAd) {
				float appear2 = clamp01((ui.now - shownAt - 340) / 450f);
				float e2 = 1 - (1 - appear2) * (1 - appear2) * (1 - appear2);
				c.pushAlpha(e2);
				adCard(ui, ads, cx + (1 - e2) * 30, top + ch + 12, cw, screen);
				c.popAlpha();
			}
		} else if (!ads.isEmpty()) {
			float room = W - (x0 + bw + 24) - x0;
			float cw = Math.min(320, room);
			float adH = adHeight(cw);
			if (cw >= 240 && H - 40 - adH >= top) {
				float appear = clamp01((ui.now - shownAt - 250) / 450f);
				float e = 1 - (1 - appear) * (1 - appear) * (1 - appear);
				c.pushAlpha(e);
				adCard(ui, ads, W - x0 - cw + (1 - e) * 30, H - 40 - adH, cw, screen);
				c.popAlpha();
			}
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

	private static synchronized void startArt() {
		if (artStarted) {
			return;
		}
		artStarted = true;
		Thread t = new Thread(() -> {
			Image img = null;
			try {
				java.nio.file.Path gameDir = xyz.nativelaunch.core.NativeState.get().gameDir();
				java.nio.file.Path file = gameDir == null ? null : gameDir.resolve(".native").resolve("version-art.jpg");
				if (file != null && java.nio.file.Files.isRegularFile(file) && java.nio.file.Files.size(file) < 16L * 1024 * 1024) {
					img = Image.decode(java.nio.file.Files.readAllBytes(file));
				}
			} catch (Throwable ignored) {
				img = null;
			}
			if (img == null) {
				img = Image.resource("/assets/native/ui/bg1.jpg");
			}
			if (img != null && img.width > 2560) {
				img = img.scaled(2560, Math.max(1, Math.round(img.height * (2560f / img.width))));
			}
			art = img;
		}, "Native title art");
		t.setDaemon(true);
		t.start();
	}

	private void drawBackground(Ui ui, float W, float H) {
		Canvas c = ui.c;
		c.fill(0, 0, W, H, 0xFF050608);
		Image a = art;
		if (a != null) {
			if (artShownAt == 0) {
				artShownAt = ui.now;
			}
			float k = Math.min(1f, (ui.now - artShownAt) / 350f);
			c.imageCover(a, 0, 0, W, H, 1.04f, 0.2f, 0, Theme.alpha(0xFFFFFFFF, k));
		}
		c.gradientH(0, 0, W * 0.7f, H, 0xF0000000, 0x00000000);
		c.gradientV(0, H * 0.5f, W, H * 0.5f, 0x00000000, 0xD0000000);
		c.gradientV(0, 0, W, 140, 0x99000000, 0x00000000);
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

	private List<Ads.Ad> visibleAds() {
		List<Ads.Ad> all = Ads.list();
		if (hiddenAds.isEmpty()) {
			return all;
		}
		List<Ads.Ad> out = new java.util.ArrayList<>();
		for (Ads.Ad ad : all) {
			if (!hiddenAds.contains(ad.id)) {
				out.add(ad);
			}
		}
		return out;
	}

	private static float adHeight(float w) {
		return w * 5f / 12f + 62;
	}

	/** A Feather-style sponsored card: banner (with the player's own skin when the ad asks for it), text, buttons. */
	private void adCard(Ui ui, List<Ads.Ad> ads, float x, float y, float w, Object screen) {
		Canvas c = ui.c;
		int count = ads.size();
		float mh = w * 5f / 12f, h = adHeight(w);
		boolean over = ui.hover(x, y, w, h);
		if (adSwitchAt == 0 || over) {
			adSwitchAt = ui.now + AD_ROTATE_MS;
		} else if (ui.now >= adSwitchAt && count > 1) {
			adIndex = (adIndex + 1) % count;
			adSwitchAt = ui.now + AD_ROTATE_MS;
		}
		Ads.Ad ad = ads.get(adIndex % count);
		c.shadow(x, y + 8, w, h, 16, 28, 0x80000000);
		c.round(x, y, w, h, 16, 0xE608090C);
		c.imageCover(ad.image, x, y, w, mh, 1f, 0, 0, 0xFFFFFFFF);
		c.outline(x, y, w, h, 16, 1, Theme.HAIRLINE);

		// close: hide this ad for the rest of the session
		float cs = 22, cxx = x + w - cs - 8, cyy = y + 8;
		boolean overClose = ui.hover(cxx, cyy, cs, cs);
		float hc = ui.anim("ad#close", overClose, 14f);
		c.circle(cxx + cs / 2, cyy + cs / 2, cs / 2, Theme.alpha(0xFF000000, 0.5f + 0.25f * hc));
		c.icon(Theme.I_X, 11, cxx + cs / 2, cyy + cs / 2, 0xFFFFFFFF);
		if (ui.clicked("ad#close", cxx, cyy, cs, cs)) {
			hiddenAds.add(ad.id);
			adIndex = 0;
			return;
		}

		// buttons (right side of the footer)
		float pad = 14, fy = y + mh, fh = h - mh;
		float bx = x + w - pad;
		int n = Math.min(2, ad.buttons.size());
		float bh = n > 1 ? 22 : 28, gap = 5;
		float by = fy + (fh - (bh * n + gap * (n - 1))) / 2;
		float buttonsW = 0;
		for (int i = 0; i < n; i++) {
			buttonsW = Math.max(buttonsW, c.textWidth(Fonts.BOLD, 12, ad.buttons.get(i).label) + 34);
		}
		buttonsW = Math.min(buttonsW, w * 0.42f);
		boolean buttonHit = false;
		for (int i = 0; i < n; i++) {
			Ads.Button b = ad.buttons.get(i);
			float yy = by + i * (bh + gap), xx = bx - buttonsW;
			String id = "ad#b" + i;
			boolean ob = ui.hover(xx, yy, buttonsW, bh);
			float hb = ui.anim(id, ob, 14f);
			int bg = i == 0 ? Theme.mix(0xFFFFFFFF, 0xFFE8E9EF, hb) : Theme.alpha(0xFFFFFFFF, 0.10f + 0.08f * hb);
			int fg = i == 0 ? 0xFF0D0E12 : 0xFFFFFFFF;
			c.round(xx, yy, buttonsW, bh, bh / 2, bg);
			int icon = b.server() ? Theme.I_PLAY : Theme.I_CHEVRON_RIGHT;
			float tw = c.textWidth(Fonts.BOLD, 12, b.label);
			float iw = 12;
			float tx = xx + (buttonsW - tw - iw - 5) / 2;
			c.text(Fonts.BOLD, 12, c.ellipsize(Fonts.BOLD, 12, b.label, buttonsW - 30), tx, yy + (bh - c.lineHeight(Fonts.BOLD, 12)) / 2, fg);
			c.icon(icon, 11, tx + tw + 5 + iw / 2, yy + bh / 2, fg);
			if (ob) {
				buttonHit = true;
			}
			if (ui.clicked(id, xx, yy, buttonsW, bh)) {
				run(b, screen);
			}
		}

		// text
		float tx = x + pad, maxW = w - pad * 2 - (n > 0 ? buttonsW + 10 : 0);
		String tag = ad.tag.isEmpty() ? "AD" : "AD \u00b7 " + ad.tag.toUpperCase(java.util.Locale.ROOT);
		float lines = ad.body.isEmpty() ? 2 : 3;
		float ty = fy + (fh - (lines == 3 ? 46 : 30)) / 2;
		c.text(Fonts.BOLD, 9.5f, c.ellipsize(Fonts.BOLD, 9.5f, tag, maxW), tx, ty, Theme.TEXT_MUTED);
		c.text(Fonts.SEMIBOLD, 13.5f, c.ellipsize(Fonts.SEMIBOLD, 13.5f, ad.title, maxW), tx, ty + 13, Theme.TEXT_STRONG);
		if (!ad.body.isEmpty()) {
			c.text(Fonts.REGULAR, 11, c.ellipsize(Fonts.REGULAR, 11, ad.body, maxW), tx, ty + 32, Theme.TEXT_SECONDARY);
		}

		// banner / card click = main button
		if (!buttonHit && !overClose && ui.clicked("ad#card", x, y, w, h)) {
			run(ad.primary(), screen);
		}

		// dots
		if (count > 1) {
			float dw = 6, dg = 5, total = count * dw + (count - 1) * dg + 6;
			float dx = x + (w - total) / 2, dy = y + mh - 14;
			c.round(dx - 4, dy - 4, total + 8, dw + 8, (dw + 8) / 2, 0x66000000);
			for (int i = 0; i < count; i++) {
				boolean active = i == adIndex % count;
				float ww = active ? dw + 6 : dw;
				c.round(dx, dy, ww, dw, dw / 2, active ? 0xFFFFFFFF : 0x80FFFFFF);
				if (ui.clicked("ad#dot" + i, dx - 2, dy - 4, ww + 4, dw + 8)) {
					adIndex = i;
					adSwitchAt = ui.now + AD_ROTATE_MS;
				}
				dx += ww + dg;
			}
		}
	}

	private static void run(Ads.Button b, Object screen) {
		if (b == null) {
			return;
		}
		if (b.server()) {
			UiRuntime.mc().connect(b.value, screen);
		} else {
			Ads.open(b.value);
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
