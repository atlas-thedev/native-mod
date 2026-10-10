package xyz.nativelaunch.ui.view;

import xyz.nativelaunch.relay.RelayClient;
import xyz.nativelaunch.ui.McBridge;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.UiRuntime;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;

/**
 * The pause menu (Esc in a world), laid out like vanilla's (title, Back to Game, two columns, Save and Quit) so it
 * feels like the game, but drawn with Native's own buttons. Relay and Mods take the place of Feedback / Report Bugs.
 */
public final class PauseView {
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
		if (screen != lastScreen) {
			lastScreen = screen;
			confirmLeave = false;
			ui.setAnim("pause#in", 0);
		}
		float W = c.width(), H = c.height();
		float in = ui.anim("pause#in", 1f, 12f);
		c.fill(0, 0, W, H, Theme.alpha(0x8C000000, in));

		String where = mc.server();
		boolean single = where == null || "Singleplayer".equals(where);
		float bw = Math.min(400, W - 40), bh = 40, gap = 8, half = (bw - gap) / 2;
		float rows = 5;
		float total = 28 + 24 + rows * bh + (rows - 1) * gap + 14;
		float x = (W - bw) / 2;
		float y = Math.max(16, (H - total) / 2 - H * 0.04f) + (1 - in) * 10;
		c.pushAlpha(in);

		// title (with the server / world under it)
		String title = confirmLeave ? (single ? "Save and quit to title?" : "Disconnect from the server?") : mc.tr("menu.game", "Game Menu");
		float tw = c.textWidth(Fonts.BOLD, 17, title);
		c.text(Fonts.BOLD, 17, title, (W - tw) / 2 + 1, y + 1, 0x99000000);
		c.text(Fonts.BOLD, 17, title, (W - tw) / 2, y, Theme.TEXT_STRONG);
		String sub = confirmLeave ? (single ? "Your world is saved automatically." : "You can rejoin from the server list.") : (single ? "Singleplayer" : where);
		int ping = single || confirmLeave ? -1 : mc.ping();
		if (ping >= 0) {
			sub = sub + "  \u00b7  " + ping + " ms";
		}
		sub = c.ellipsize(Fonts.MEDIUM, 11.5f, sub, bw);
		float sw = c.textWidth(Fonts.MEDIUM, 11.5f, sub);
		c.text(Fonts.MEDIUM, 11.5f, sub, (W - sw) / 2, y + 26, Theme.TEXT_SECONDARY);
		y += 28 + 24;

		if (confirmLeave) {
			if (button(ui, "pause:leave:yes", x, y, bw, bh, single ? "Save and quit" : "Disconnect", Theme.I_LOG_OUT, DANGER)) {
				confirmLeave = false;
				mc.exitWorld();
			}
			y += bh + gap;
			if (button(ui, "pause:leave:no", x, y, bw, bh, "Stay in game", 0, NORMAL)) {
				confirmLeave = false;
			}
			c.popAlpha();
			return;
		}

		if (button(ui, "pause:resume", x, y, bw, bh, mc.tr("menu.returnToGame", "Back to Game"), 0, PRIMARY)) {
			UiRuntime.closeHost(screen);
			c.popAlpha();
			return;
		}
		y += bh + gap;
		if (button(ui, "pause:adv", x, y, half, bh, mc.tr("gui.advancements", "Advancements"), Theme.I_STAR, NORMAL)) {
			mc.open("advancements", screen);
		}
		if (button(ui, "pause:stats", x + half + gap, y, half, bh, mc.tr("gui.stats", "Statistics"), Theme.I_ACTIVITY, NORMAL)) {
			mc.open("stats", screen);
		}
		y += bh + gap;
		RelayClient client = RelayClient.get();
		if (button(ui, "pause:relay", x, y, half, bh, "Relay", Theme.I_MESSAGE, NORMAL)) {
			UiRuntime.openRelay(screen);
		}
		int unread = client == null ? 0 : client.unreadTotal();
		if (unread > 0) {
			TitleView.badge(c, x + half - 10, y + bh / 2, unread);
		}
		if (button(ui, "pause:menu", x + half + gap, y, half, bh, "Native Mods", Theme.I_SPARKLES, NORMAL)) {
			UiRuntime.openMenu(screen);
		}
		y += bh + gap;
		String options = mc.tr("menu.options", "Options...").replace("...", "").replace("\u2026", "");
		if (button(ui, "pause:options", x, y, half, bh, options, Theme.I_SETTINGS, NORMAL)) {
			mc.open("options", screen);
		}
		if (single) {
			// 26.3+: World Options took Open to LAN's place in the vanilla menu
			String worldOptions = mc.tr("options.worldOptions.button", null);
			String lan = worldOptions != null ? worldOptions : mc.tr("menu.shareToLan", "Open to LAN");
			if (button(ui, "pause:lan", x + half + gap, y, half, bh, lan, worldOptions != null ? Theme.I_SLIDERS : Theme.I_GLOBE, NORMAL)) {
				mc.open("lan", screen);
			}
		} else if (button(ui, "pause:locker", x + half + gap, y, half, bh, "Cosmetics", Theme.I_SHIRT, NORMAL)) {
			UiRuntime.openMenu(screen, 1);
		}
		y += bh + gap;
		String leave = single ? mc.tr("menu.returnToMenu", "Save and Quit to Title") : mc.tr("menu.disconnect", "Disconnect");
		if (button(ui, "pause:leave", x, y, bw, bh, leave, 0, NORMAL)) {
			confirmLeave = true;
		}
		c.popAlpha();
	}

	private static final int NORMAL = 0, PRIMARY = 1, DANGER = 2;

	/**
	 * Native's pause button: a dark glass slab with a soft top light and a hairline edge, small corners like the
	 * game's own buttons; hover brightens the edge and lifts it a pixel, pressing sinks it.
	 */
	private static boolean button(Ui ui, String id, float x, float y, float w, float h, String label, int icon, int kind) {
		Canvas c = ui.c;
		boolean over = ui.hover(x, y, w, h);
		boolean click = ui.clicked(id, x, y, w, h);
		float hv = ui.anim(id + "#h", over, 16f);
		float pr = ui.anim(id + "#p", ui.isPressing(id), 24f);
		float r = 6;
		float yy = y - hv + pr * 1.5f;
		int bg, top, edge, fg;
		if (kind == PRIMARY) {
			bg = Theme.mix(0xF2EDEDF0, 0xFFFFFFFF, hv);
			top = 0x00FFFFFF;
			edge = 0;
			fg = Theme.SOLID_FG;
		} else if (kind == DANGER) {
			bg = Theme.mix(0xE0281416, 0xF0381A1D, hv);
			top = 0x14FFFFFF;
			edge = Theme.mix(0x66EF4444, 0xB3EF4444, hv);
			fg = 0xFFFECACA;
		} else {
			bg = Theme.mix(0xD90C0D11, 0xEB16181E, hv);
			top = Theme.mix(0x0FFFFFFF, 0x1AFFFFFF, hv);
			edge = Theme.mix(0x24FFFFFF, 0x59FFFFFF, hv);
			fg = Theme.mix(0xFFE4E4E7, 0xFFFFFFFF, hv);
		}
		if (hv > 0.01f) {
			c.shadow(x, yy + 3, w, h, r, 12, Theme.alpha(0x80000000, hv));
		}
		c.round(x, yy, w, h, r, bg);
		if ((top >>> 24) != 0) {
			c.pushClip(x, yy, w, h / 2);
			c.round(x, yy, w, h, r, top);
			c.popClip();
		}
		if (edge != 0) {
			c.outline(x, yy, w, h, r, 1, edge);
		}
		float size = 13.5f;
		float tw = c.textWidth(Fonts.SEMIBOLD, size, label);
		float iw = icon != 0 ? 22 : 0;
		float maxText = w - 16 - iw;
		String text = tw > maxText ? c.ellipsize(Fonts.SEMIBOLD, size, label, maxText) : label;
		tw = Math.min(tw, maxText);
		float tx = x + (w - tw - iw) / 2;
		if (icon != 0) {
			c.icon(icon, 15, tx + 7, yy + h / 2, Theme.alpha(fg, 0.8f + 0.2f * hv));
		}
		c.text(Fonts.SEMIBOLD, size, text, tx + iw, yy + (h - c.lineHeight(Fonts.SEMIBOLD, size)) / 2, fg);
		return click;
	}
}
