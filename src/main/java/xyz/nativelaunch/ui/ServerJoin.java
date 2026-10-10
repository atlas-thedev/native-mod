package xyz.nativelaunch.ui;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Joins a server through the game's own connect screen, found by reflection so one piece of code covers every
 * version: {@code startConnecting(Screen, Minecraft, ServerAddress, ServerData, [boolean], [TransferState])} on
 * 1.17+ and 26.x, the {@code ConnectScreen(Screen, Minecraft, ServerData)} constructor on 1.16.
 */
public final class ServerJoin {
	private ServerJoin() {
	}

	/** @return the screen to show (1.16 constructor), {@code Boolean.TRUE} when the game switched itself, null on failure. */
	public static Object start(Object client, Object parent, Class<?> screenType, String connectScreen, String serverAddress,
			String parseMethod, String serverData, String address) {
		try {
			Class<?> connect = Class.forName(connectScreen);
			Class<?> data = Class.forName(serverData);
			Object info = newServerData(data, address);
			Class<?> addrType = null;
			Object addr = null;
			try {
				addrType = Class.forName(serverAddress);
				addr = addrType.getMethod(parseMethod, String.class).invoke(null, address);
			} catch (Throwable ignored) {
				// 1.16: no ServerAddress parse, uses the constructor below
			}
			if (addr != null) {
				for (Method m : connect.getMethods()) {
					Class<?>[] t = m.getParameterTypes();
					if (!Modifier.isStatic(m.getModifiers()) || t.length < 4 || t[0] != screenType || !has(t, addrType) || !has(t, data)) {
						continue;
					}
					Object[] args = new Object[t.length];
					for (int i = 0; i < t.length; i++) {
						args[i] = value(t[i], client, parent, screenType, addrType, addr, data, info);
					}
					m.invoke(null, args);
					return Boolean.TRUE;
				}
			}
			for (Constructor<?> c : connect.getConstructors()) {
				Class<?>[] t = c.getParameterTypes();
				if (t.length == 3 && t[0] == screenType && t[2] == data && t[1].isInstance(client)) {
					return c.newInstance(parent, client, info);
				}
			}
		} catch (Throwable ignored) {
			// fall through
		}
		return null;
	}

	private static boolean has(Class<?>[] types, Class<?> type) {
		for (Class<?> t : types) {
			if (t == type) {
				return true;
			}
		}
		return false;
	}

	private static Object value(Class<?> t, Object client, Object parent, Class<?> screenType, Class<?> addrType, Object addr,
			Class<?> data, Object info) {
		if (t == screenType) {
			return parent;
		}
		if (t == addrType) {
			return addr;
		}
		if (t == data) {
			return info;
		}
		if (t == boolean.class) {
			return Boolean.FALSE;
		}
		if (t.isInstance(client)) {
			return client;
		}
		return null;
	}

	/** {@code new ServerData(name, ip, Type.OTHER)} on 1.20.2+, {@code (name, ip, false)} before. */
	private static Object newServerData(Class<?> data, String address) throws Exception {
		for (Constructor<?> c : data.getConstructors()) {
			Class<?>[] t = c.getParameterTypes();
			if (t.length != 3 || t[0] != String.class || t[1] != String.class) {
				continue;
			}
			if (t[2] == boolean.class) {
				return c.newInstance(address, address, false);
			}
			if (t[2].isEnum()) {
				Object[] all = t[2].getEnumConstants();
				Object pick = all[all.length - 1];
				for (Object e : all) {
					if ("OTHER".equals(((Enum<?>) e).name())) {
						pick = e;
					}
				}
				return c.newInstance(address, address, pick);
			}
		}
		throw new IllegalStateException("no ServerData constructor");
	}
}
