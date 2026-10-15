package xyz.nativelaunch.ui.view;

import xyz.nativelaunch.core.Session;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.gfx.Canvas;

/**
 * What Relay and the locker show while the game is not signed in to Native: the reason (guest,
 * expired, offline, checking) and, where it helps, a "Try again" button. Pages call {@link #locked()}
 * first and draw this instead of their content, so stale data never looks live.
 */
final class SessionLock {
	private SessionLock() {
	}

	/** True when account pages must stay locked. */
	static boolean locked() {
		return !Session.signedIn();
	}

	static void draw(Ui ui, String id, float x, float y, float w, float h) {
		Canvas c = ui.c;
		Session.Status status = Session.status();
		if (status == Session.Status.CHECKING) {
			ui.dots(x + w / 2, y + h / 2 - 24, Theme.TEXT_SECONDARY);
			String title = Session.lockTitle();
			c.text(xyz.nativelaunch.ui.gfx.Fonts.SEMIBOLD, 13.5f, title,
					x + (w - c.textWidth(xyz.nativelaunch.ui.gfx.Fonts.SEMIBOLD, 13.5f, title)) / 2, y + h / 2, Theme.TEXT_STRONG);
			return;
		}
		int icon = status == Session.Status.OFFLINE ? Theme.I_WIFI_OFF : Theme.I_LOCK;
		boolean action = status == Session.Status.OFFLINE || status == Session.Status.EXPIRED;
		TitleView.empty(c, x, y, w, action ? h - 40 : h, icon, Session.lockTitle(), Session.lockBody());
		if (action && ui.button(id + ":retry", x + w / 2 - 62, y + h / 2 + 44, 124, 34, "Try again", Theme.I_REFRESH, Ui.BTN_GLASS)) {
			Session.retry();
		}
	}
}
