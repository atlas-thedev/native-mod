package xyz.nativelaunch.ui.view;

import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.mod.Game;
import xyz.nativelaunch.ui.mod.HudModule;
import xyz.nativelaunch.ui.mod.Modules;

import java.util.List;

/** Paints the enabled HUD modules while playing. */
public final class HudOverlay {
	private HudOverlay() {
	}

	/** True when at least one module would draw something. */
	public static boolean any(Game g) {
		List<HudModule> list = Modules.hud();
		for (int i = 0; i < list.size(); i++) {
			HudModule m = list.get(i);
			if (m.enabled && m.visible(g)) {
				return true;
			}
		}
		return false;
	}

	public static void draw(Ui ui, Game g) {
		Canvas c = ui.c;
		float W = c.width(), H = c.height();
		List<HudModule> list = Modules.hud();
		for (int i = 0; i < list.size(); i++) {
			HudModule m = list.get(i);
			if (!m.enabled || !m.visible(g)) {
				continue;
			}
			float[] sz = m.measure(c, g);
			float w = sz[0], h = sz[1];
			m.paint(ui, HudModule.pos(m.fx, W, w), HudModule.pos(m.fy, H, h), g);
		}
	}
}
