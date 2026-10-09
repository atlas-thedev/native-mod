package xyz.nativelaunch.ui;

import xyz.nativelaunch.core.Log;
import xyz.nativelaunch.relay.RelayClient;
import xyz.nativelaunch.ui.gfx.Atlas;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.ui.gfx.GlRenderer;
import xyz.nativelaunch.ui.mod.Game;
import xyz.nativelaunch.ui.mod.Module;
import xyz.nativelaunch.ui.mod.Modules;
import xyz.nativelaunch.ui.view.HudEditor;
import xyz.nativelaunch.ui.view.HudOverlay;
import xyz.nativelaunch.ui.view.MenuView;
import xyz.nativelaunch.ui.view.RelayView;
import xyz.nativelaunch.ui.view.TitleView;
import xyz.nativelaunch.ui.view.Toasts;

import java.nio.file.Path;
import java.util.List;

/**
 * Heart of the Native UI: called once per frame right before the buffer swap, decides what to show (custom
 * title screen, Relay chat, message toasts), runs the immediate-mode views and hands the result to the
 * renderer. Costs nothing when none of it is on screen.
 */
public final class UiRuntime {
	private static McBridge mc;
	private static UiConfig config;
	private static boolean failed;
	private static GlRenderer gl;
	private static Canvas canvas;
	private static Ui ui;
	private static GlfwInput input;
	private static TitleView title;
	private static RelayView relay;
	private static Toasts toasts;
	private static MenuView menu;
	private static HudEditor editor;
	private static final Game game = new Game();
	private static volatile boolean playing;
	private static int frames;
	/** Player chose the vanilla title screen for this session. */
	public static boolean classic;
	private static volatile boolean capturing;
	private static volatile boolean pillVisible;
	private static volatile float pillX, pillY, pillW, pillH; // framebuffer px
	private static volatile int relayHostOpen;
	private static String version = "";

	private UiRuntime() {
	}

	public static void install(McBridge bridge, Path gameDir, String minecraftVersion) {
		mc = bridge;
		config = UiConfig.load(gameDir);
		version = minecraftVersion == null ? "" : minecraftVersion;
		try {
			Modules.init(gameDir);
		} catch (Throwable t) {
			Log.warn("Native mods unavailable: {}", t.toString());
		}
		Log.info("Native UI ready (custom title screen {}, chat key {}).", config.customTitle ? "on" : "off", keyName(config.relayKey));
	}

	public static McBridge mc() {
		return mc;
	}

	public static Game game() {
		return game;
	}

	public static UiConfig config() {
		return config;
	}

	public static String minecraftVersion() {
		return version;
	}

	/** Used by the setScreen hook: swaps the vanilla title screen for ours. */
	public static Object replaceScreen(Object screen) {
		try {
			if (mc != null && !failed && config.customTitle && !classic && screen != null && mc.isVanillaTitle(screen)) {
				return mc.newHost(McBridge.TITLE, null);
			}
		} catch (Throwable t) {
			fail(t);
		}
		return screen;
	}

	/** Called by the render hook right before the frame is presented. */
	public static void onFrame() {
		if (mc == null || failed) {
			return;
		}
		try {
			frame();
		} catch (Throwable t) {
			fail(t);
		}
	}

	private static void fail(Throwable t) {
		failed = true;
		capturing = false;
		Log.warn("Native UI disabled after an error: {}", t.toString());
		for (StackTraceElement e : t.getStackTrace()) {
			Log.warn("    at {}", e);
		}
		try {
			Object s = mc.screen();
			if (mc.hostKind(s) != 0) {
				mc.setScreen(mc.hostKind(s) == McBridge.TITLE ? mc.newVanillaTitle() : null);
			}
		} catch (Throwable ignored) {
			// leave it
		}
	}

	private static void frame() {
		if (!mc.ready()) {
			return;
		}
		long window = mc.window();
		if (window == 0) {
			return;
		}
		if (input == null) {
			input = new GlfwInput(window);
		} else if (++frames % 120 == 0) {
			input.install();
		}
		Game.countFrame();
		Object screen = mc.screen();
		if (config.customTitle && !classic && mc.isVanillaTitle(screen)) {
			mc.setScreen(mc.newHost(McBridge.TITLE, null));
			screen = mc.screen();
		}
		int kind = mc.hostKind(screen);
		relayHostOpen = kind;
		boolean inWorld = mc.inWorld();
		boolean hud = false;
		if (inWorld) {
			game.update(mc, window);
		} else {
			game.inWorld = false;
		}
		game.playing = inWorld && screen == null;
		playing = game.playing;
		modulesFrame();
		Modules.tick();
		if (kind == 0 && inWorld && (screen == null || mc.isChat(screen)) && !mc.hudHidden() && !mc.debugOpen()) {
			hud = HudOverlay.any(game);
		}
		boolean overlay = mc.overlay();
		boolean classicTitle = classic && mc.isVanillaTitle(screen);
		RelayClient client = RelayClient.get();
		if (client != null && (kind != 0 || !config.notifications || !mc.inWorld())) {
			client.notices.clear(); // only shown while playing; the chat itself shows everything else
		}
		boolean wantToasts = kind == 0 && config.notifications && client != null && (toasts != null && toasts.active() || !client.notices.isEmpty());
		capturing = kind != 0 && !overlay;
		pillVisible = classicTitle && !overlay;
		if ((kind == 0 && !classicTitle && !wantToasts && !hud) || overlay) {
			if (kind == 0 && client != null && relay != null) {
				client.viewing = null;
			}
			if (overlay) {
				Input.clear();
			}
			return;
		}
		int fbW = mc.fbWidth(), fbH = mc.fbHeight();
		if (fbW <= 0 || fbH <= 0) {
			return;
		}
		ensure();
		float scale = Math.max(0.75f, Math.min(fbW / 1280f, fbH / 760f)) * config.scale;
		canvas.begin(fbW, fbH, scale);
		List<Input.Event> events = Input.drain();
		ui.begin(events);
		if (kind == McBridge.TITLE) {
			title.draw(ui, screen);
		} else if (kind == McBridge.RELAY) {
			if (!mc.inWorld()) {
				title.background(ui); // nothing is drawn behind us outside a world
			}
			relay.draw(ui, screen, false);
		} else if (kind == McBridge.MENU) {
			if (!inWorld) {
				title.background(ui);
			}
			menu.draw(ui, screen, inWorld);
		} else if (kind == McBridge.HUD) {
			if (!inWorld) {
				title.background(ui);
			}
			editor.draw(ui, screen, inWorld, inWorld ? game : null);
		} else if (classicTitle) {
			drawPill(ui);
		} else {
			if (hud) {
				HudOverlay.draw(ui, game);
			}
			if (toasts != null) {
				toasts.draw(ui);
			}
		}
		ui.tooltips();
		ui.end();
		gl.render(canvas);
	}

	private static void ensure() {
		if (canvas != null) {
			return;
		}
		canvas = new Canvas(new Atlas(1024), new Fonts());
		gl = new GlRenderer();
		ui = new Ui(canvas);
		ui.clipboard = new Ui.Clipboard() {
			@Override
			public String get() {
				return input == null ? null : input.clipboard();
			}

			@Override
			public void set(String text) {
				if (input != null) {
					input.clipboard(text);
				}
			}
		};
		title = new TitleView();
		relay = new RelayView();
		toasts = new Toasts();
		menu = new MenuView();
		editor = new HudEditor();
	}

	/** Runs every enabled module; one that throws is switched off instead of taking the UI down. */
	private static void modulesFrame() {
		java.util.List<Module> all = Modules.all();
		for (int i = 0; i < all.size(); i++) {
			Module m = all.get(i);
			if (!m.enabled) {
				continue;
			}
			try {
				m.frame(game);
			} catch (Throwable t) {
				Log.warn("Module {} failed and was switched off: {}", m.id, t.toString());
				try {
					m.setEnabled(false);
				} catch (Throwable ignored) {
					m.enabled = false;
				}
			}
		}
	}

	/** Opens the Native menu (mods, cosmetics, settings). */
	public static void openMenu(Object parent) {
		mc.setScreen(mc.newHost(McBridge.MENU, parent));
	}

	private static void drawPill(Ui ui) {
		Canvas c = ui.c;
		float w = 128, h = 30, x = 12, y = 12;
		pillX = x * c.scale;
		pillY = y * c.scale;
		pillW = w * c.scale;
		pillH = h * c.scale;
		boolean over = ui.hover(x, y, w, h);
		float hv = ui.anim("pill", over, 14f);
		c.shadow(x, y + 2, w, h, 15, 8, 0x55000000);
		c.round(x, y, w, h, 15, Theme.mix(0xD008090C, 0xF014171D, hv));
		c.outline(x, y, w, h, 15, 1, Theme.mix(Theme.HAIRLINE_STRONG, Theme.BORDER_HOVER, hv));
		c.icon(Theme.I_SPARKLES, 14, x + 18, y + h / 2, Theme.TEXT_STRONG);
		c.text(Fonts.SEMIBOLD, 12, "Native menu", x + 32, y + (h - c.lineHeight(Fonts.SEMIBOLD, 12)) / 2, Theme.TEXT_STRONG);
	}

	/** Opens the in-game chat (from a key press or the title screen). */
	public static void openRelay(Object parent) {
		RelayClient client = RelayClient.get();
		if (client != null) {
			client.refresh();
		}
		mc.setScreen(mc.newHost(McBridge.RELAY, parent));
	}

	public static void closeHost(Object screen) {
		Object parent = mc.hostParent(screen);
		RelayClient client = RelayClient.get();
		if (client != null) {
			client.viewing = null;
		}
		mc.setScreen(parent);
	}

	public static void useClassicTitle() {
		classic = true;
		mc.setScreen(mc.newVanillaTitle());
	}

	public static void useNativeTitle() {
		classic = false;
		mc.setScreen(mc.newHost(McBridge.TITLE, null));
	}

	// ── input (GLFW callbacks, main thread) ───────────────────────────────

	static void onMove(double x, double y) {
		if (mc == null) {
			return;
		}
		Input.move(x * ratioX(), y * ratioY());
	}

	private static double ratioX() {
		int w = mc.windowWidth();
		return w <= 0 ? 1 : (double) mc.fbWidth() / w;
	}

	private static double ratioY() {
		int h = mc.windowHeight();
		return h <= 0 ? 1 : (double) mc.fbHeight() / h;
	}

	static boolean onButton(int button, int action, int mods) {
		if (capturing) {
			Input.button(button, action == 0 ? 0 : 1, mods);
			return true;
		}
		if (action == 1 && playing) {
			Game.click(button);
		}
		if (pillVisible && action == 1 && button == 0) {
			double x = Input.mouseX, y = Input.mouseY;
			if (x >= pillX && x < pillX + pillW && y >= pillY && y < pillY + pillH) {
				try {
					useNativeTitle();
				} catch (Throwable t) {
					fail(t);
				}
				return true;
			}
		}
		return false;
	}

	static boolean onScroll(double dx, double dy) {
		if (capturing) {
			Input.scroll(dx, dy);
			return true;
		}
		if (playing && !failed) {
			try {
				java.util.List<Module> all = Modules.all();
				for (int i = 0; i < all.size(); i++) {
					Module m = all.get(i);
					if (m.enabled && m.onScroll(dy, game)) {
						return true;
					}
				}
			} catch (Throwable t) {
				Log.warn("Module scroll failed: {}", t.toString());
			}
		}
		return false;
	}

	static boolean onChar(int codepoint) {
		if (capturing) {
			Input.character(codepoint);
			return true;
		}
		return false;
	}

	static boolean onKey(int key, int action, int mods) {
		if (mc == null || failed) {
			return false;
		}
		if (capturing) {
			// function keys (fullscreen, screenshot, debug) keep working
			if (key >= 290 && key <= 301) {
				return false;
			}
			if (key == Ui.KEY_ESCAPE && action == 1) {
				try {
					Object s = mc.screen();
					if (mc.hostKind(s) == McBridge.RELAY && (relay == null || !relay.consumeEscape(ui))) {
						closeHost(s);
					} else if (mc.hostKind(s) == McBridge.TITLE && title != null) {
						title.escape();
					} else if (mc.hostKind(s) == McBridge.MENU && (menu == null || !menu.escape(ui))) {
						closeHost(s);
					} else if (mc.hostKind(s) == McBridge.HUD && (editor == null || !editor.escape())) {
						Modules.save();
						closeHost(s);
					}
				} catch (Throwable t) {
					fail(t);
				}
				return true;
			}
			Input.key(key, action, mods);
			return true;
		}
		if (action == 1 && key == config.menuKey && (mods & (Ui.MOD_CONTROL | Ui.MOD_ALT | Ui.MOD_SUPER)) == 0) {
			try {
				if (mc.screen() == null && mc.inWorld()) {
					openMenu(null);
					return true;
				}
			} catch (Throwable t) {
				fail(t);
			}
		}
		if (playing && !failed) {
			try {
				java.util.List<Module> all = Modules.all();
				for (int i = 0; i < all.size(); i++) {
					Module m = all.get(i);
					if (m.enabled && m.onKey(key, action, game)) {
						return true;
					}
				}
			} catch (Throwable t) {
				Log.warn("Module key failed: {}", t.toString());
			}
		}
		if (action == 1 && key == config.relayKey && mods == 0) {
			try {
				if (mc.screen() == null && mc.inWorld() && RelayClient.get() != null) {
					String latest = toasts == null ? null : toasts.latestKey();
					if (latest != null) {
						RelayView.selectNext(latest);
					}
					openRelay(null);
					return true;
				}
			} catch (Throwable t) {
				fail(t);
			}
		}
		return false;
	}

	public static String keyName(int key) {
		return xyz.nativelaunch.ui.mod.Keys.name(key);
	}

	/** Key for the chat setting: next press becomes the binding. */
	public static void setRelayKey(int key) {
		config.relayKey = key;
		config.save();
	}
}
