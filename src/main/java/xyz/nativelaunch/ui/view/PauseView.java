package xyz.nativelaunch.ui.view;

import xyz.nativelaunch.relay.RelayClient;
import xyz.nativelaunch.ui.McBridge;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.UiRuntime;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.ui.gfx.Image;

/** The Native pause menu (Esc in a world): resume, Relay, mods menu, options, leave. */
public final class PauseView {
	private Image logo;
	private Object lastScreen;
	private boolean confirmLeave;

	public boolean escape() {
		if (confirmLeave) {
			confirmLeave = false;
			return true;
		}
		return false;
	}

	public void draw(Ui ui, Object screen) {
		Canvas c = ui.c;
		McBridge mc = UiRuntime.mc();
		if (logo == null) {
			logo = Image.resource("/assets/native/ui/logo.png");
		}
		if (screen != lastScreen) {
			lastScreen = screen;
			confirmLeave = false;
			ui.setAnim("pause#in", 0);
		}
		float W = c.width(), H = c.height();
		float in = ui.anim("pause#in", 1f, 10f);
		c.fill(0, 0, W, H, Theme.alpha(0xB0000000, in));
		c.gradientV(0, 0, W, H * 0.45f, Theme.alpha(0x40000000, in), 0);

		float pw = 340, bh = 46, gap = 10;
		String where = mc.server();
		boolean single = where == null || "Singleplayer".equals(where);
		int rows = 5;
		float ph = 118 + rows * (bh + gap) + 34;
		float px = (W - pw) / 2, py = Math.max(24, (H - ph) / 2) + (1 - in) * 14;
		c.pushAlpha(in);
		c.shadow(px, py + 10, pw, ph, 20, 44, 0xAA000000);
		c.round(px, py, pw, ph, 20, 0xF508090C);
		c.outline(px, py, pw, ph, 20, 1, Theme.HAIRLINE_STRONG);

		// header
		float hx = px + 24, hy = py + 22;
		if (logo != null) {
			c.stamp("logo", logo, hx, hy, 44, 44, true, 0xFFFFFFFF);
		}
		c.text(Fonts.BOLD, 18, "Game menu", hx + 58, hy + 2, Theme.TEXT_STRONG);
		String sub = single ? "Singleplayer" : where;
		int ping = single ? -1 : mc.ping();
		float subMax = pw - 48 - 58 - (ping >= 0 ? 64 : 0);
		c.text(Fonts.MEDIUM, 12, c.ellipsize(Fonts.MEDIUM, 12, sub, subMax), hx + 58, hy + 25, Theme.TEXT_MUTED);
		if (ping >= 0) {
			String t = ping + " ms";
			float tw = c.textWidth(Fonts.SEMIBOLD, 11, t) + 26;
			int col = ping < 80 ? Theme.ONLINE : (ping < 180 ? Theme.IDLE : 0xFFF87171);
			float bx = px + pw - 24 - tw, by = hy + 4;
			c.round(bx, by, tw, 22, 11, Theme.SUBTLE);
			c.circle(bx + 11, by + 11, 3.5f, col);
			c.text(Fonts.SEMIBOLD, 11, t, bx + 20, by + (22 - c.lineHeight(Fonts.SEMIBOLD, 11)) / 2, Theme.TEXT_SECONDARY);
		}
		c.fill(px + 24, py + 82, pw - 48, 1, Theme.HAIRLINE);

		float by = py + 100;
		float bw = pw - 48, bx = px + 24;
		if (confirmLeave) {
			c.text(Fonts.SEMIBOLD, 14, single ? "Save and quit to title?" : "Disconnect from the server?", bx, by + 4, Theme.TEXT_STRONG);
			c.text(Fonts.REGULAR, 12, single ? "Your world is saved automatically." : "You can rejoin from the server list.", bx, by + 26, Theme.TEXT_MUTED);
			if (ui.button("pause:leave:yes", bx, by + 60, bw, bh, single ? "Save and quit" : "Disconnect", Theme.I_LOG_OUT, Ui.BTN_DANGER)) {
				confirmLeave = false;
				mc.exitWorld();
			}
			if (ui.button("pause:leave:no", bx, by + 60 + bh + gap, bw, bh, "Stay in game", 0, Ui.BTN_GLASS)) {
				confirmLeave = false;
			}
			c.popAlpha();
			return;
		}
		if (ui.button("pause:resume", bx, by, bw, bh, "Back to game", Theme.I_PLAY, Ui.BTN_PRIMARY)) {
			UiRuntime.closeHost(screen);
		}
		by += bh + gap;
		RelayClient client = RelayClient.get();
		if (ui.button("pause:relay", bx, by, bw, bh, "Relay chat", Theme.I_MESSAGE, Ui.BTN_GLASS)) {
			UiRuntime.openRelay(screen);
		}
		int unread = client == null ? 0 : client.unreadTotal();
		if (unread > 0) {
			TitleView.badge(c, bx + bw - 24, by + bh / 2, unread);
		}
		by += bh + gap;
		if (ui.button("pause:menu", bx, by, bw, bh, "Mods and cosmetics", Theme.I_PUZZLE, Ui.BTN_GLASS)) {
			UiRuntime.openMenu(screen);
		}
		by += bh + gap;
		if (ui.button("pause:options", bx, by, bw, bh, "Options", Theme.I_SETTINGS, Ui.BTN_GLASS)) {
			mc.open("options", screen);
		}
		by += bh + gap;
		if (ui.button("pause:leave", bx, by, bw, bh, single ? "Save and quit to title" : "Disconnect", Theme.I_LOG_OUT, Ui.BTN_DANGER)) {
			confirmLeave = true;
		}
		c.text(Fonts.MEDIUM, 10.5f, "Esc to resume", px + (pw - c.textWidth(Fonts.MEDIUM, 10.5f, "Esc to resume")) / 2, py + ph - 26, Theme.TEXT_MUTED);
		c.popAlpha();
	}
}
