package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;

/** Weather Changer: clear, rain or a thunderstorm on your screen (and in your ears), whatever the server says. The client's rain / thunder levels are hooked (WeatherMixin). */
public final class WeatherChanger extends Module {
	private static volatile int mode = -1; // -1 off, 0 clear, 1 rain, 2 thunder
	final Setting.Choice weather = add(new Setting.Choice("weather", "Weather", 0, "Clear", "Rain", "Thunder"));

	WeatherChanger() {
		super("weather-changer", "Weather Changer", "Pick clear skies, rain or a thunderstorm for your own screen. The server's weather does not change.", VISUAL, Theme.I_ZOOM, false);
	}

	private static boolean isClientWorld(Object world) {
		if (world == null) {
			return false;
		}
		String n = world.getClass().getName();
		return n.equals("net.minecraft.class_638") || n.equals("net.minecraft.client.multiplayer.ClientLevel");
	}

	/** The hook: the rain level to show for this world, or null to leave the game's own. */
	public static Float rain(Object world) {
		int m = mode;
		return m < 0 || !isClientWorld(world) ? null : (m >= 1 ? 1f : 0f);
	}

	public static Float thunder(Object world) {
		int m = mode;
		return m < 0 || !isClientWorld(world) ? null : (m >= 2 ? 1f : 0f);
	}

	@Override
	public void frame(Game g) {
		mode = weather.value;
	}

	@Override
	protected void onDisable() {
		mode = -1;
	}

	@Override
	public boolean preview(Ui ui, Game g, float x, float y, float w, float h) {
		Canvas c = ui.c;
		int w0 = weather.value;
		int topCol = w0 == 0 ? 0xFF6BA8E8 : w0 == 1 ? 0xFF6F7C91 : 0xFF3A4152, botCol = w0 == 0 ? 0xFFB9DAF5 : w0 == 1 ? 0xFF98A3B3 : 0xFF596277;
		c.gradientV(x, y, w, h * 0.62f, topCol, botCol);
		c.fill(x, y + h * 0.62f, w, h * 0.38f, w0 == 0 ? 0xFF5E8F3F : 0xFF4B7433);
		if (w0 == 0) {
			c.circle(x + w * 0.78f, y + h * 0.2f, 16, 0xFFFFE08A);
		}
		for (int i = 0; i < 3; i++) { // clouds
			float cx = x + ((ui.now / 60 + i * 170) % (int) (w + 120)) - 60, cy = y + h * (0.12f + 0.1f * i);
			int col = w0 == 0 ? 0xE6FFFFFF : w0 == 1 ? 0xCC8A94A5 : 0xCC4A5164;
			c.round(cx, cy, 90, 22, 11, col);
			c.round(cx + 18, cy - 10, 50, 22, 11, col);
		}
		if (w0 >= 1) {
			for (int i = 0; i < 70; i++) {
				float px = x + ((i * 73) % 1000) / 1000f * w;
				float py = y + (((i * 131) % 1000) / 1000f * h * 0.9f + ui.now * 0.45f * (0.7f + (i % 5) * 0.1f)) % (h * 0.95f);
				c.fill(px, py, 1.5f, 9, 0x88B6D4F5);
			}
		}
		if (w0 == 2 && (ui.now / 450) % 5 == 0) {
			c.fill(x, y, w, h, 0x33FFFFFF);
			float bx = x + w * 0.62f;
			c.fill(bx, y + h * 0.1f, 3, h * 0.18f, 0xFFFFF7B0);
			c.fill(bx - 6, y + h * 0.28f, 3, h * 0.14f, 0xFFFFF7B0);
			c.fill(bx - 3, y + h * 0.42f, 3, h * 0.2f, 0xFFFFF7B0);
		}
		String t = w0 == 0 ? "Clear" : w0 == 1 ? "Rain" : "Thunderstorm";
		float tw = c.textWidth(Fonts.SEMIBOLD, 10.5f, t) + 20;
		c.round(x + (w - tw) / 2, y + h - 34, tw, 22, 11, 0xC0000000);
		c.text(Fonts.SEMIBOLD, 10.5f, t, x + (w - tw) / 2 + 10, y + h - 34 + (22 - c.lineHeight(Fonts.SEMIBOLD, 10.5f)) / 2, Theme.TEXT_SECONDARY);
		return true;
	}
}
