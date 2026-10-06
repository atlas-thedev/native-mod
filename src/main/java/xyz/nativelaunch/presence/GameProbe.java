package xyz.nativelaunch.presence;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

/**
 * Reads what the player is doing straight from the running game, without mixins, so one jar
 * works on Minecraft 1.16 through 26.x. Minecraft 26.x runs under its own (unobfuscated) names;
 * 1.16 - 1.21.x run under Fabric intermediary names, which stay the same across those releases:
 *
 * <pre>
 *   Minecraft.getInstance()             class_310.method_1551
 *   Minecraft.getConnection()           class_310.method_1562   (null outside a world)
 *   Minecraft.getCurrentServer()        class_310.method_1558   (ServerData, null in singleplayer)
 *   Minecraft.getSingleplayerServer()   class_310.method_1576
 *   ServerData.name / ip                class_642.field_3752 / field_3761
 *   ServerData.isLan() / isRealm()      class_642.method_2994 / method_52811 (1.20.2+)
 *   ClientPacketListener.getOnlinePlayers()  class_634.method_2880
 *   MinecraftServer.getWorldData().getLevelName()   method_27728 / method_150
 *   MinecraftServer.storageSource.getLevelId()      field_23784 / method_27005
 * </pre>
 *
 * Every lookup is optional: anything missing just leaves that detail out.
 */
final class GameProbe {
	enum Kind { MENUS, SINGLEPLAYER, MULTIPLAYER, REALMS }

	static final class Snapshot {
		Kind kind = Kind.MENUS;
		String serverName;
		String address;
		boolean lan;
		String worldName;
		String worldId;
		boolean published;
		int tabPlayers = -1;

		String key() {
			return kind + "|" + (address == null ? "" : address.toLowerCase()) + "|" + (worldId == null ? "" : worldId);
		}
	}

	private final boolean mojang;
	private Method getInstance;
	private Method getConnection;
	private Method getCurrentServer;
	private Method getSingleplayerServer;
	private boolean ready;
	private boolean failed;

	GameProbe() {
		boolean named;
		try {
			Class.forName("net.minecraft.client.Minecraft", false, GameProbe.class.getClassLoader());
			named = true;
		} catch (Throwable t) {
			named = false;
		}
		mojang = named;
	}

	private String n(String mojangName, String intermediary) {
		return mojang ? mojangName : intermediary;
	}

	private synchronized boolean init() {
		if (ready || failed) {
			return ready;
		}
		try {
			String cls = mojang ? "net.minecraft.client.Minecraft" : runtimeClass("net.minecraft.class_310");
			Class<?> minecraft = Class.forName(cls, false, GameProbe.class.getClassLoader());
			getInstance = minecraft.getMethod(n("getInstance", "method_1551"));
			getConnection = minecraft.getMethod(n("getConnection", "method_1562"));
			getCurrentServer = minecraft.getMethod(n("getCurrentServer", "method_1558"));
			getSingleplayerServer = minecraft.getMethod(n("getSingleplayerServer", "method_1576"));
			ready = true;
		} catch (Throwable t) {
			failed = true;
		}
		return ready;
	}

	private static String runtimeClass(String intermediary) {
		try {
			return FabricLoader.getInstance().getMappingResolver().mapClassName("intermediary", intermediary);
		} catch (Throwable t) {
			return intermediary;
		}
	}

	/** @return what the game shows right now, or null when the game is not reachable (yet). */
	Snapshot read() {
		if (!init()) {
			return null;
		}
		Snapshot s = new Snapshot();
		try {
			Object mc = getInstance.invoke(null);
			if (mc == null) {
				return s;
			}
			Object connection = getConnection.invoke(mc);
			if (connection == null) {
				return s; // title screen, server list, loading screens
			}
			s.tabPlayers = tabSize(connection);
			Object integrated = getSingleplayerServer.invoke(mc);
			if (integrated != null) {
				s.kind = Kind.SINGLEPLAYER;
				readWorld(integrated, s);
				return s;
			}
			Object server = getCurrentServer.invoke(mc);
			if (server == null) {
				s.kind = Kind.REALMS;
				return s;
			}
			s.serverName = stringField(server, n("name", "field_3752"));
			s.address = stringField(server, n("ip", "field_3761"));
			s.lan = bool(server, n("isLan", "method_2994"));
			s.kind = bool(server, n("isRealm", "method_52811")) ? Kind.REALMS : Kind.MULTIPLAYER;
			return s;
		} catch (Throwable t) {
			return s;
		}
	}

	private int tabSize(Object connection) {
		try {
			Object players = connection.getClass().getMethod(n("getOnlinePlayers", "method_2880")).invoke(connection);
			return players instanceof Collection ? ((Collection<?>) players).size() : -1;
		} catch (Throwable t) {
			return -1;
		}
	}

	private void readWorld(Object server, Snapshot s) {
		try {
			Object data = server.getClass().getMethod(n("getWorldData", "method_27728")).invoke(server);
			Object name = data == null ? null : data.getClass().getMethod(n("getLevelName", "method_150")).invoke(data);
			if (name instanceof String) {
				s.worldName = (String) name;
			}
		} catch (Throwable ignored) {
			// leave the name out
		}
		try {
			Field field = findField(server.getClass(), n("storageSource", "field_23784"));
			Object access = field == null ? null : field.get(server);
			Object id = access == null ? null : access.getClass().getMethod(n("getLevelId", "method_27005")).invoke(access);
			if (id instanceof String) {
				s.worldId = (String) id;
			}
		} catch (Throwable ignored) {
			// no icon then
		}
		s.published = bool(server, n("isPublished", "method_3860"));
	}

	private static Field findField(Class<?> type, String name) {
		for (Class<?> c = type; c != null; c = c.getSuperclass()) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f;
			} catch (NoSuchFieldException ignored) {
				// keep walking up
			} catch (Throwable t) {
				return null;
			}
		}
		return null;
	}

	private static String stringField(Object target, String name) {
		try {
			Field f = findField(target.getClass(), name);
			Object v = f == null ? null : f.get(target);
			return v instanceof String ? (String) v : null;
		} catch (Throwable t) {
			return null;
		}
	}

	private static boolean bool(Object target, String method) {
		try {
			Method m = findMethod(target.getClass(), method);
			Object v = m == null ? null : m.invoke(target);
			return Boolean.TRUE.equals(v);
		} catch (Throwable t) {
			return false;
		}
	}

	private static Method findMethod(Class<?> type, String name) {
		for (Class<?> c = type; c != null; c = c.getSuperclass()) {
			try {
				Method m = c.getDeclaredMethod(name);
				m.setAccessible(true);
				return m;
			} catch (NoSuchMethodException ignored) {
				// keep walking up
			} catch (Throwable t) {
				return null;
			}
		}
		return null;
	}
}
