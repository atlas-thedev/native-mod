package xyz.nativelaunch.ui26;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import xyz.nativelaunch.ui.McBridge;
import xyz.nativelaunch.ui.gfx.Renderer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * McBridge for Minecraft 26.x, written against the official (unobfuscated) names. Members that moved between
 * 26.1, 26.2 and 26.3 (screen handling moved from Minecraft to Gui, the HUD to Hud, ...) are looked up reflectively.
 */
public final class MojangMc implements McBridge {
	private final String version;
	private final GuiRenderer26 renderer = new GuiRenderer26();
	private Constructor<?> modsScreen;
	private boolean modsLooked;

	public MojangMc(String version) {
		this.version = version == null ? "" : version;
	}

	private static Minecraft client() {
		return Minecraft.getInstance();
	}

	// ── reflection helpers ────────────────────────────────────────────────

	private static final Object MISSING = new Object();
	private final Map<String, Object> members = new HashMap<String, Object>();

	private Field field(Class<?> owner, String name) {
		String key = owner.getName() + "#" + name;
		Object m = members.get(key);
		if (m == null) {
			m = MISSING;
			for (Class<?> c = owner; c != null && m == MISSING; c = c.getSuperclass()) {
				try {
					Field f = c.getDeclaredField(name);
					f.setAccessible(true);
					m = f;
				} catch (Throwable ignored) {
					// keep looking up the hierarchy
				}
			}
			members.put(key, m);
		}
		return m == MISSING ? null : (Field) m;
	}

	/** First method with this name and parameter count, public or not, up the hierarchy. */
	private Method named(Class<?> owner, String name, int params) {
		String key = owner.getName() + "~" + name + params;
		Object m = members.get(key);
		if (m == null) {
			m = MISSING;
			for (Method x : owner.getMethods()) {
				if (x.getName().equals(name) && x.getParameterCount() == params) {
					x.setAccessible(true);
					m = x;
					break;
				}
			}
			for (Class<?> c = owner; c != null && m == MISSING; c = c.getSuperclass()) {
				for (Method x : c.getDeclaredMethods()) {
					if (x.getName().equals(name) && x.getParameterCount() == params) {
						x.setAccessible(true);
						m = x;
						break;
					}
				}
			}
			members.put(key, m);
		}
		return m == MISSING ? null : (Method) m;
	}

	private Object get(Object target, String name) {
		if (target == null) {
			return null;
		}
		Field f = field(target.getClass(), name);
		try {
			return f == null ? null : f.get(target);
		} catch (Throwable t) {
			return null;
		}
	}

	private Object call(Object target, String name, Object... args) {
		if (target == null) {
			return null;
		}
		Method m = named(target.getClass(), name, args.length);
		try {
			return m == null ? null : m.invoke(target, args);
		} catch (Throwable t) {
			return null;
		}
	}

	/** 26.1: screens live on Minecraft; 26.2+: on Gui. */
	private Object screenOwner() {
		Minecraft c = client();
		return named(Minecraft.class, "setScreen", 1) != null ? c : c.gui;
	}

	/** 26.1: the HUD is Gui; 26.2+: Gui.hud. */
	private Object hud() {
		Object gui = client().gui;
		Object hud = get(gui, "hud");
		return hud != null ? hud : gui;
	}

	// ── McBridge ──────────────────────────────────────────────────────────

	@Override
	public Renderer renderer() {
		return renderer;
	}

	@Override
	public boolean guiFrames() {
		return true;
	}

	@Override
	public boolean handlesInput() {
		return true;
	}

	@Override
	public boolean cursor(double[] out) {
		Minecraft c = client();
		if (c == null || c.mouseHandler == null) {
			return false;
		}
		out[0] = c.mouseHandler.xpos();
		out[1] = c.mouseHandler.ypos();
		return true;
	}

	@Override
	public String clipboard() {
		try {
			return client().keyboardHandler.getClipboard();
		} catch (Throwable t) {
			return null;
		}
	}

	@Override
	public void setClipboard(String text) {
		try {
			client().keyboardHandler.setClipboard(text);
		} catch (Throwable ignored) {
			// leave it
		}
	}

	@Override
	public boolean ready() {
		Minecraft c = client();
		return c != null && c.getWindow() != null;
	}

	@Override
	public Object screen() {
		Object owner = screenOwner();
		Object s = owner == client() ? get(owner, "screen") : call(owner, "screen");
		return s;
	}

	@Override
	public boolean isVanillaTitle(Object screen) {
		return screen instanceof TitleScreen;
	}

	@Override
	public int hostKind(Object screen) {
		return screen instanceof NativeHostScreen26 ? ((NativeHostScreen26) screen).kind : 0;
	}

	@Override
	public Object hostParent(Object screen) {
		return screen instanceof NativeHostScreen26 ? ((NativeHostScreen26) screen).parent : null;
	}

	@Override
	public Object newHost(int kind, Object parent) {
		return new NativeHostScreen26(kind, parent instanceof Screen ? (Screen) parent : null);
	}

	@Override
	public boolean isVanillaPause(Object screen) {
		return screen instanceof PauseScreen;
	}

	@Override
	public void exitWorld() {
		xyz.nativelaunch.ui.UiRuntime.pauseBypass = true;
		try {
			Screen vanilla = new PauseScreen(true);
			setScreen(vanilla);
			String quit = tr("menu.returnToMenu", "Save and Quit to Title"), leave = tr("menu.disconnect", "Disconnect");
			Object target = null;
			for (Object w : vanilla.children()) {
				Object msg = call(w, "getMessage");
				Object text = call(msg, "getString");
				if (text instanceof String && (text.equals(quit) || text.equals(leave))) {
					target = w;
				}
			}
			if (target == null) {
				return; // the vanilla pause screen stays open: the player can press its button
			}
			Object nul = null;
			for (Class<?> k = target.getClass(); k != null; k = k.getSuperclass()) {
				for (Method m : k.getDeclaredMethods()) {
					if (m.getName().equals("onPress") && m.getParameterCount() <= 1) {
						m.setAccessible(true);
						if (m.getParameterCount() == 0) {
							m.invoke(target);
						} else {
							m.invoke(target, nul);
						}
						return;
					}
				}
			}
		} catch (Throwable t) {
			// leave the vanilla screen open
		} finally {
			xyz.nativelaunch.ui.UiRuntime.pauseBypass = false;
		}
	}

	@Override
	public Object newVanillaTitle() {
		return new TitleScreen();
	}

	@Override
	public void setScreen(Object screen) {
		Object owner = screenOwner();
		Method m = named(owner.getClass(), "setScreen", 1);
		try {
			m.invoke(owner, screen);
		} catch (RuntimeException e) {
			throw e;
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}

	@Override
	public boolean open(String action, Object parent) {
		Screen p = parent instanceof Screen ? (Screen) parent : null;
		Object next = null;
		try {
			if ("singleplayer".equals(action)) {
				next = Class.forName("net.minecraft.client.gui.screens.worldselection.SelectWorldScreen").getConstructor(Screen.class).newInstance(p);
			} else if ("multiplayer".equals(action)) {
				next = Class.forName("net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen").getConstructor(Screen.class).newInstance(p);
			} else if ("realms".equals(action)) {
				next = Class.forName("com.mojang.realmsclient.RealmsMainScreen").getConstructor(Screen.class).newInstance(p);
			} else if ("options".equals(action)) {
				Class<?> cls = Class.forName("net.minecraft.client.gui.screens.options.OptionsScreen");
				for (Constructor<?> ctor : cls.getConstructors()) {
					Class<?>[] t = ctor.getParameterTypes();
					if (t.length == 2 && t[0] == Screen.class && t[1] == Options.class) {
						next = ctor.newInstance(p, client().options); // 26.3
					} else if (t.length == 3 && t[0] == Screen.class && t[1] == Options.class && t[2] == boolean.class) {
						next = ctor.newInstance(p, client().options, false); // 26.1 / 26.2
					}
					if (next != null) {
						break;
					}
				}
			} else if ("advancements".equals(action) || "stats".equals(action) || "lan".equals(action)) {
				Object player = client().player;
				Object connection = client().getConnection();
				String cls = "advancements".equals(action) ? "net.minecraft.client.gui.screens.advancements.AdvancementsScreen"
						: "stats".equals(action) ? "net.minecraft.client.gui.screens.achievement.StatsScreen"
						: "net.minecraft.client.gui.screens.ShareToLanScreen";
				next = xyz.nativelaunch.ui.ScreenFactory.create(cls, p, Screen.class, player, connection);
			} else if ("mods".equals(action)) {
				Constructor<?> ctor = mods();
				if (ctor != null) {
					next = ctor.newInstance(p);
				}
			}
		} catch (Throwable t) {
			return false;
		}
		if (next == null) {
			return false;
		}
		setScreen(next);
		return true;
	}

	private Constructor<?> mods() {
		if (!modsLooked) {
			modsLooked = true;
			try {
				Class<?> cls = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
				for (Constructor<?> c : cls.getConstructors()) {
					if (c.getParameterTypes().length == 1 && c.getParameterTypes()[0].isAssignableFrom(Screen.class)) {
						modsScreen = c;
						break;
					}
				}
			} catch (Throwable ignored) {
				// not installed
			}
		}
		return modsScreen;
	}

	@Override
	public boolean connect(String address, Object parent) {
		Object r = xyz.nativelaunch.ui.ServerJoin.start(client(), parent instanceof Screen ? parent : null, Screen.class,
				"net.minecraft.client.gui.screens.ConnectScreen", "net.minecraft.client.multiplayer.resolver.ServerAddress", "parseString",
				"net.minecraft.client.multiplayer.ServerData", address);
		if (r instanceof Screen) {
			setScreen(r);
		}
		return r != null;
	}

	@Override
	public boolean hasMods() {
		return mods() != null;
	}

	@Override
	public void quit() {
		client().stop();
	}

	@Override
	public boolean inWorld() {
		return client().level != null;
	}

	@Override
	public boolean overlay() {
		Minecraft c = client();
		Object o = named(Minecraft.class, "getOverlay", 0) != null ? call(c, "getOverlay") : call(c.gui, "overlay");
		return o != null;
	}

	private static Window win() {
		return client().getWindow();
	}

	@Override
	public long window() {
		return win().handle();
	}

	// ── keyboard / window state ──────────────────────────────────────────
	// 26.1 / 26.2 still run on GLFW and InputConstants can poll the window; 26.3 switched to SDL, the GLFW
	// classes are gone and InputConstants.isKeyDown with them. There we report "unknown" (-1) and the UI falls
	// back to the key state it tracks from the game's own key events, which works on either backend.

	private boolean inputLooked;
	private Method isKeyDown, getKeyByCode, displayName, textString;

	private void lookUpInput() {
		if (inputLooked) {
			return;
		}
		inputLooked = true;
		try {
			Class<?> input = Class.forName("com.mojang.blaze3d.platform.InputConstants");
			for (Method m : input.getMethods()) {
				if (m.getName().equals("isKeyDown") && m.getParameterCount() == 2) {
					isKeyDown = m;
				} else if (m.getName().equals("getKey") && m.getParameterCount() == 2) {
					getKeyByCode = m;
				}
			}
			if (getKeyByCode != null) {
				displayName = getKeyByCode.getReturnType().getMethod("getDisplayName");
				textString = displayName.getReturnType().getMethod("getString");
			}
		} catch (Throwable ignored) {
			// 26.3: SDL, no InputConstants to poll
		}
	}

	@Override
	public int keyState(int code) {
		if (code < 0 || code <= 7) {
			return -1; // mouse buttons: tracked from the game's own events
		}
		lookUpInput();
		if (isKeyDown == null) {
			return -1;
		}
		try {
			Object down = isKeyDown.invoke(null, Long.valueOf(window()), Integer.valueOf(xyz.nativelaunch.ui.SdlKeys.toGame(code)));
			return Boolean.TRUE.equals(down) ? 1 : 0;
		} catch (Throwable t) {
			isKeyDown = null; // SDL build: never ask again
			return -1;
		}
	}

	@Override
	public String keyLabel(int code) {
		lookUpInput();
		if (getKeyByCode == null || displayName == null || textString == null) {
			return null;
		}
		try {
			Object key = getKeyByCode.invoke(null, Integer.valueOf(xyz.nativelaunch.ui.SdlKeys.toGame(code)), Integer.valueOf(0));
			Object text = displayName.invoke(key);
			Object label = textString.invoke(text);
			return label instanceof String ? (String) label : null;
		} catch (Throwable t) {
			getKeyByCode = null;
			return null;
		}
	}

	@Override
	public boolean windowFocused() {
		try {
			return client().isWindowActive();
		} catch (Throwable t) {
			return true;
		}
	}

	@Override
	public boolean windowMinimized() {
		Window w;
		try {
			w = win();
		} catch (Throwable t) {
			return false;
		}
		Object value = call(w, "isIconified");
		if (!(value instanceof Boolean)) {
			value = call(w, "isMinimized");
		}
		if (!(value instanceof Boolean)) {
			// no flag on this release: a zero-sized framebuffer means the window is not being drawn
			return w.getWidth() <= 0 || w.getHeight() <= 0;
		}
		return (Boolean) value;
	}

	@Override
	public int fbWidth() {
		return win().getWidth();
	}

	@Override
	public int fbHeight() {
		return win().getHeight();
	}

	@Override
	public int windowWidth() {
		return win().getScreenWidth();
	}

	@Override
	public int windowHeight() {
		return win().getScreenHeight();
	}

	@Override
	public String tr(String key, String fallback) {
		try {
			String s = I18n.get(key);
			return s == null || s.equals(key) ? fallback : s;
		} catch (Throwable t) {
			return fallback;
		}
	}

	@Override
	public String version() {
		return version;
	}

	// ── client mods ───────────────────────────────────────────────────────

	@Override
	public boolean isChat(Object screen) {
		return screen instanceof ChatScreen;
	}

	@Override
	public boolean player(double[] out) {
		LocalPlayer p = client().player;
		if (p == null) {
			return false;
		}
		out[0] = p.getX();
		out[1] = p.getY();
		out[2] = p.getZ();
		out[3] = p.getYRot();
		out[4] = p.getXRot();
		return true;
	}

	@Override
	public int ping() {
		Minecraft c = client();
		LocalPlayer p = c.player;
		ClientPacketListener handler = c.getConnection();
		if (p == null || handler == null) {
			return -1;
		}
		PlayerInfo info = handler.getPlayerInfo(p.getUUID());
		return info == null ? -1 : info.getLatency();
	}

	@Override
	public String server() {
		Minecraft c = client();
		ServerData info = c.getCurrentServer();
		if (info != null && info.ip != null && !info.ip.isEmpty()) {
			return info.ip;
		}
		return c.isLocalServer() ? "Singleplayer" : null;
	}

	@Override
	public boolean hudHidden() {
		Object old = get(client().options, "hideGui"); // 26.1
		if (old instanceof Boolean) {
			return (Boolean) old;
		}
		return Boolean.TRUE.equals(call(hud(), "isHidden")); // 26.2+
	}

	@Override
	public boolean debugOpen() {
		try {
			return client().getDebugOverlay().showDebugScreen();
		} catch (Throwable t) {
			return false;
		}
	}

	private KeyMapping binding(String control) {
		Options o = client().options;
		switch (control) {
			case "forward": return o.keyUp;
			case "left": return o.keyLeft;
			case "back": return o.keyDown;
			case "right": return o.keyRight;
			case "jump": return o.keyJump;
			case "sneak": return o.keyShift;
			case "sprint": return o.keySprint;
			case "attack": return o.keyAttack;
			case "use": return o.keyUse;
			default: return null;
		}
	}

	@Override
	public int boundKey(String control) {
		KeyMapping b = binding(control);
		Object key = get(b, "key");
		Object code = call(key, "getValue");
		if (!(code instanceof Integer)) {
			return -1;
		}
		int value = (Integer) code;
		if (!xyz.nativelaunch.ui.SdlKeys.active()) {
			return value;
		}
		// 26.3 stores its bindings as SDL scancodes / SDL mouse buttons; the UI works in GLFW codes
		Object type = call(key, "getType");
		String name = type == null ? "" : String.valueOf(type).toUpperCase(java.util.Locale.ROOT);
		return name.contains("MOUSE") ? xyz.nativelaunch.ui.SdlKeys.mouseToGlfw(value) : xyz.nativelaunch.ui.SdlKeys.toGlfw(value);
	}

	@Override
	public void setPressed(String control, boolean pressed) {
		KeyMapping b = binding(control);
		if (b != null) {
			b.setDown(pressed);
		}
	}

	@Override
	public double gamma() {
		Object value = get(get(client().options, "gamma"), "value");
		return value instanceof Double ? (Double) value : Double.NaN;
	}

	@Override
	public void setGamma(double value) {
		Object option = get(client().options, "gamma");
		Field v = option == null ? null : field(option.getClass(), "value");
		try {
			if (v != null) {
				v.set(option, Double.valueOf(value)); // set directly: the slider's validator would clamp 16 to 1
			}
		} catch (Throwable ignored) {
			// leave brightness alone
		}
	}

	@Override
	public boolean smoothCamera() {
		return client().options.smoothCamera;
	}

	@Override
	public void setSmoothCamera(boolean on) {
		client().options.smoothCamera = on;
	}

	@Override
	public String username() {
		try {
			return client().getUser().getName();
		} catch (Throwable t) {
			return null;
		}
	}

	@Override
	public boolean guiSize(float[] out) {
		Window w = win();
		out[0] = w.getGuiScaledWidth();
		out[1] = w.getGuiScaledHeight();
		out[2] = w.getGuiScale();
		return true;
	}

	// ── scoreboard sidebar + boss bars ────────────────────────────────────

	@Override
	public boolean sidebar(xyz.nativelaunch.ui.mod.Overlays.Sidebar out) {
		return Overlays26.sidebar(this, out);
	}

	@Override
	public boolean bossBars(xyz.nativelaunch.ui.mod.Overlays.Bars out) {
		return Overlays26.bossBars(this, out);
	}

	Object hudObject() {
		return hud();
	}
}
