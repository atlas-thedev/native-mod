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
}
