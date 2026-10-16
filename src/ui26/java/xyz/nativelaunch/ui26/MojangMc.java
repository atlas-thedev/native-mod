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
	private final xyz.nativelaunch.ui.MemberCache members = new xyz.nativelaunch.ui.MemberCache();

	private Field field(Class<?> owner, String name) {
		Map<String, Object> table = members.fields(owner);
		Object m = table.get(name);
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
			table.put(name, m);
		}
		return m == MISSING ? null : (Field) m;
	}

	/** First method with this name and parameter count, public or not, up the hierarchy. */
	private Method named(Class<?> owner, String name, int params) {
		Map<String, Object> table = members.named(owner, params);
		Object m = table.get(name);
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
			table.put(name, m);
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
	public java.util.concurrent.Executor gameThread() {
		return client();
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

	/** 26.3+ World Options screen, built directly: {@code new WorldOptionsScreen(Screen parent, Level level)}. */
	private static Object worldOptions(Screen parent, Object level) {
		if (level == null) {
			return null;
		}
		try {
			Class<?> cls = Class.forName("net.minecraft.client.gui.screens.WorldOptionsScreen");
			for (Constructor<?> ctor : cls.getConstructors()) {
				Class<?>[] t = ctor.getParameterTypes();
				if (t.length == 2 && t[0].isAssignableFrom(Screen.class) && t[1].isInstance(level)) {
					return ctor.newInstance(parent, level);
				}
			}
		} catch (ClassNotFoundException e) {
			return null; // before 26.3
		} catch (Throwable t) {
			xyz.nativelaunch.core.Log.warn("Couldn't open World Options directly: {}", String.valueOf(t.getCause() != null ? t.getCause() : t));
		}
		return null;
	}

	/** Opens the vanilla pause screen and presses the button with this label; false when it isn't there. */
	private boolean pressVanillaPause(String label) {
		xyz.nativelaunch.ui.UiRuntime.pauseBypass = true;
		try {
			Screen vanilla = new PauseScreen(true);
			setScreen(vanilla);
			Object target = null;
			for (Object w : vanilla.children()) {
				Object text = call(call(w, "getMessage"), "getString");
				if (label.equals(text)) {
					target = w;
				}
			}
			if (target == null) {
				return true; // the vanilla pause screen stays open: the player can press its button
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
						return true;
					}
				}
			}
			return true;
		} catch (Throwable t) {
			xyz.nativelaunch.core.Log.warn("Couldn't press the vanilla {} button: {}", label, String.valueOf(t));
			return true;
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
				Object level = client().level;
				if ("lan".equals(action)) {
					// 26.3 replaced Open to LAN with World Options (Screen parent, Level)
					next = worldOptions(p, level);
				}
				if (next == null) {
					next = xyz.nativelaunch.ui.ScreenFactory.create(cls, p, Screen.class, player, connection, level);
				}
				if (next == null && "lan".equals(action)) {
					// last resort: press the button on the vanilla pause screen, which knows how to build it
					String label = tr("options.worldOptions.button", null);
					return pressVanillaPause(label != null ? label : tr("menu.shareToLan", "Open to LAN"));
				}
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
	public String dimension() {
		Object key = call(client().level, "dimension");
		if (key == null) {
			return null;
		}
		String s = key.toString(); // ResourceKey[minecraft:dimension / minecraft:the_nether]
		int slash = s.lastIndexOf('/');
		if (slash >= 0) {
			s = s.substring(slash + 1).replace("]", "").trim();
		}
		int colon = s.indexOf(':');
		return colon >= 0 ? s.substring(colon + 1) : s;
	}

	@Override
	public long dayTime() {
		Object level = client().level;
		Object t = call(level, "getOverworldClockTime"); // 26.3: world clocks
		if (!(t instanceof Number)) {
			t = call(level, "getDayTime");
		}
		return t instanceof Number ? ((Number) t).longValue() : -1;
	}

	@Override
	public double reach() {
		try {
			net.minecraft.world.phys.HitResult hit = client().hitResult;
			LocalPlayer p = client().player;
			if (hit == null || p == null || hit.getType() != net.minecraft.world.phys.HitResult.Type.ENTITY) {
				return Double.NaN;
			}
			return p.getEyePosition().distanceTo(hit.getLocation());
		} catch (Throwable t) {
			return Double.NaN;
		}
	}

	@Override
	public boolean setHurtTint(int abgr) {
		try {
			Object overlay = call(client().gameRenderer, "overlayTexture");
			Object texture = get(overlay, "texture");
			Object image = call(texture, "getPixels");
			if (image == null) {
				return false;
			}
			for (int y = 0; y < 8; y++) {
				for (int x = 0; x < 16; x++) {
					call(image, "setPixelABGR", x, y, abgr);
				}
			}
			call(texture, "upload");
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	@Override
	public int cooldowns(String[] ids, float[] progress) {
		try {
			LocalPlayer p = client().player;
			if (p == null) {
				return 0;
			}
			net.minecraft.world.item.ItemCooldowns manager = p.getCooldowns();
			net.minecraft.world.entity.player.Inventory inv = p.getInventory();
			java.util.Set<String> seen = new java.util.HashSet<String>();
			int n = 0;
			for (int i = 0; i <= inv.getContainerSize(); i++) {
				net.minecraft.world.item.ItemStack st = i < inv.getContainerSize() ? inv.getItem(i) : p.getOffhandItem();
				if (st == null || st.isEmpty()) {
					continue;
				}
				String id = xyz.nativelaunch.ui.mod.Game.bareId(st.getItem().toString());
				if (id == null || !seen.add(id)) {
					continue;
				}
				float f = manager.getCooldownPercent(st, 0f);
				if (f > 0.001f && n < ids.length) {
					ids[n] = id;
					progress[n++] = f;
				}
			}
			return n;
		} catch (Throwable t) {
			return 0;
		}
	}

	@Override
	public boolean shulkerInHand(xyz.nativelaunch.ui.mod.Game.Slot[] out) {
		try {
			LocalPlayer p = client().player;
			net.minecraft.world.item.ItemStack st = p == null ? null : p.getMainHandItem();
			if (st == null || st.isEmpty() || !st.getItem().toString().endsWith("shulker_box")) {
				return false;
			}
			for (xyz.nativelaunch.ui.mod.Game.Slot s : out) {
				s.clear();
			}
			Object contents = st.get(net.minecraft.core.component.DataComponents.CONTAINER);
			Object stream = contents == null ? null : call(contents, "itemCopies");
			if (!(stream instanceof java.util.stream.Stream)) {
				stream = contents == null ? null : call(contents, "stream");
			}
			if (stream instanceof java.util.stream.Stream) {
				Object[] stacks = ((java.util.stream.Stream<?>) stream).toArray();
				for (int i = 0; i < Math.min(out.length, stacks.length); i++) {
					stack((net.minecraft.world.item.ItemStack) stacks[i], out[i]);
				}
			}
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	@Override
	public boolean playerPos(String name, double[] out) {
		net.minecraft.client.multiplayer.ClientLevel level = client().level;
		if (level == null) {
			return false;
		}
		for (net.minecraft.client.player.AbstractClientPlayer p : level.players()) {
			if (name.equalsIgnoreCase(p.getName().getString())) {
				out[0] = p.getX();
				out[1] = p.getY() + 1.6;
				out[2] = p.getZ();
				return true;
			}
		}
		return false;
	}

	// ── player state for the status modules ──

	private static void stack(net.minecraft.world.item.ItemStack st, xyz.nativelaunch.ui.mod.Game.Slot out) {
		if (st == null || st.isEmpty()) {
			out.clear();
			return;
		}
		String id = xyz.nativelaunch.ui.mod.Game.bareId(st.getItem().toString());
		if (id == null || id.isEmpty()) {
			out.clear();
			return;
		}
		boolean worn = st.isDamageableItem();
		out.set(id, st.getCount(), worn ? st.getDamageValue() : 0, worn ? st.getMaxDamage() : 0);
		try {
			Object model = st.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
			net.minecraft.world.item.component.CustomModelData data = st.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
			Float f = data == null ? null : data.getFloat(0);
			out.custom(model == null ? null : model.toString(), f == null ? Float.NaN : f);
		} catch (Throwable ignored) {
			// plain icon
		}
		try {
			int tint = itemTint(st, id);
			if (tint != 0) {
				out.setTint(tint);
			}
		} catch (Throwable ignored) {
			// no tint; the layered icon still renders with a sensible default
		}
	}

	/**
	 * The item's ARGB layer0 tint on 26.x: potion colour, leather dye, firework star, map base. Looked up through
	 * reflection so a mapping shift (names on 26.x are not stable yet) silently drops back to 0.
	 */
	private static int itemTint(net.minecraft.world.item.ItemStack st, String id) {
		boolean potion = id.equals("potion") || id.equals("splash_potion") || id.equals("lingering_potion") || id.equals("tipped_arrow");
		boolean leather = id.startsWith("leather_");
		boolean firework = id.equals("firework_star");
		boolean map = id.equals("filled_map");
		if (!potion && !leather && !firework && !map) {
			return 0;
		}
		String[] types = potion ? new String[] {"POTION_CONTENTS"}
				: leather ? new String[] {"DYED_COLOR"}
						: firework ? new String[] {"FIREWORK_EXPLOSION"}
								: new String[] {"MAP_COLOR"};
		Class<?> dc = net.minecraft.core.component.DataComponents.class;
		for (String name : types) {
			try {
				java.lang.reflect.Field f = dc.getField(name);
				Object type = f.get(null);
				Object comp = st.get((net.minecraft.core.component.DataComponentType<?>) type);
				int c = colorOf(comp);
				if (c != 0) {
					return c | 0xFF000000;
				}
			} catch (Throwable ignored) {
				// try the next type
			}
		}
		return 0;
	}

	private static int colorOf(Object comp) {
		if (comp == null) {
			return 0;
		}
		for (java.lang.reflect.Method m : comp.getClass().getMethods()) {
			if (m.getParameterCount() != 0 || m.getReturnType() != int.class) {
				continue;
			}
			String n = m.getName().toLowerCase();
			if (!n.contains("color") && !n.contains("rgb") && !n.equals("comp_2384") && !n.equals("comp_2387")) {
				continue;
			}
			try {
				Object r = m.invoke(comp);
				if (r instanceof Integer && (Integer) r != 0) {
					return (Integer) r;
				}
			} catch (Throwable ignored) {
				// try the next getter
			}
		}
		return 0;
	}

	@Override
	public boolean vitals(xyz.nativelaunch.ui.mod.Game g, int needs) {
		LocalPlayer p = client().player;
		if (p == null) {
			return false;
		}
		if ((needs & xyz.nativelaunch.ui.mod.Game.NEED_VITALS) != 0) {
			g.health = p.getHealth();
			g.maxHealth = Math.max(1f, p.getMaxHealth());
			g.absorption = p.getAbsorptionAmount();
			g.armorValue = p.getArmorValue();
			g.food = p.getFoodData().getFoodLevel();
			g.saturation = p.getFoodData().getSaturationLevel();
		}
		if ((needs & xyz.nativelaunch.ui.mod.Game.NEED_EFFECTS) != 0) {
			int n = 0;
			for (net.minecraft.world.effect.MobEffectInstance e : p.getActiveEffects()) {
				if (n >= g.effects.length) {
					break;
				}
				xyz.nativelaunch.ui.mod.Game.Effect fx = g.effects[n++];
				fx.id = xyz.nativelaunch.ui.mod.Game.effectId(e.getDescriptionId());
				fx.amp = e.getAmplifier();
				fx.ticks = e.getDuration();
				fx.ambient = e.isAmbient();
				fx.infinite = e.isInfiniteDuration() || fx.ticks < 0;
			}
			g.effectCount = n;
		}
		if ((needs & xyz.nativelaunch.ui.mod.Game.NEED_ARMOR) != 0) {
			stack(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD), g.armor[0]);
			stack(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST), g.armor[1]);
			stack(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS), g.armor[2]);
			stack(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET), g.armor[3]);
			stack(p.getMainHandItem(), g.hand);
			stack(p.getOffhandItem(), g.offhand);
		}
		if ((needs & xyz.nativelaunch.ui.mod.Game.NEED_INVENTORY) != 0) {
			net.minecraft.world.entity.player.Inventory inv = p.getInventory();
			for (int i = 0; i < g.inv.length; i++) {
				stack(inv.getItem(i), g.inv[i]);
			}
			g.selected = Math.max(0, Math.min(8, inv.getSelectedSlot()));
		}
		if ((needs & xyz.nativelaunch.ui.mod.Game.NEED_TARGET) != 0) {
			net.minecraft.world.entity.Entity t = client().crosshairPickEntity;
			g.hasTarget = false;
			if (t instanceof net.minecraft.world.entity.LivingEntity && !((net.minecraft.world.entity.LivingEntity) t).isDeadOrDying()) {
				net.minecraft.world.entity.LivingEntity l = (net.minecraft.world.entity.LivingEntity) t;
				g.targetName = l.getName().getString();
				g.targetHealth = l.getHealth();
				g.targetMax = Math.max(1f, l.getMaxHealth());
				g.hasTarget = true;
			}
		}
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
			// only the real F3 screen: showDebugScreen() is also true for always-on debug entries
			return client().debugEntries.isOverlayVisible();
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
	public double videoOption(String key) {
		return xyz.nativelaunch.ui.VideoOptions.read(client().options, "particles".equals(key) ? "particles" : "entityDistanceScaling", "value");
	}

	@Override
	public void setVideoOption(String key, double value) {
		xyz.nativelaunch.ui.VideoOptions.write(client().options, "particles".equals(key) ? "particles" : "entityDistanceScaling", "value", value);
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
