package xyz.nativelaunch.cosmetic;

import xyz.nativelaunch.core.Log;

import java.util.concurrent.Executor;

/**
 * Starts 3D cosmetics (hats, glasses, back items, shoes) on every supported release. Each Minecraft era has its
 * own small renderer compiled against that era's classes; this picks it and keeps it hooked into the player
 * renderers (they are rebuilt on every resource reload) by re-checking once a second on the game thread.
 */
public final class CosmeticsBoot {
	private static volatile boolean started;

	private CosmeticsBoot() {
	}

	/** The renderer package for a Minecraft version, or null when cosmetics are not available there. */
	public static String era(String version) {
		int[] v = parse(version);
		if (v == null) {
			return null;
		}
		if (v[0] >= 26) {
			return "cos26";
		}
		if (v[0] != 1) {
			return null;
		}
		int minor = v[1];
		int patch = v.length > 2 ? v[2] : 0;
		if (minor == 16) {
			return "cos116";
		}
		if (minor >= 17 && minor <= 20) {
			return "cos117";
		}
		if (minor == 21) {
			if (patch <= 1) {
				return "cos117";
			}
			if (patch <= 8) {
				return "cos1212";
			}
			return "cos1219";
		}
		return null;
	}

	public static synchronized void start(String version) {
		if (started) {
			return;
		}
		String era = era(version);
		if (era == null) {
			Log.info("3D cosmetics are not available on Minecraft {}.", version);
			return;
		}
		final Runnable installer;
		try {
			installer = (Runnable) Class.forName("xyz.nativelaunch." + era + ".Install").getDeclaredConstructor().newInstance();
		} catch (Throwable t) {
			Log.warn("3D cosmetics are unavailable on Minecraft {} ({}).", version, t.toString());
			return;
		}
		started = true;
		final Runnable safe = new Runnable() {
			private int failures;

			@Override
			public void run() {
				if (failures > 5) {
					return;
				}
				try {
					installer.run();
				} catch (Throwable t) {
					if (++failures <= 1 || failures > 5) {
						Log.warn("3D cosmetics hook failed ({}){}.", t.toString(), failures > 5 ? ": giving up" : "");
					}
				}
			}
		};
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						Thread.sleep(1000);
						Object game = net.fabricmc.loader.api.FabricLoader.getInstance().getGameInstance();
						if (game instanceof Executor) {
							((Executor) game).execute(safe);
						}
					} catch (InterruptedException e) {
						return;
					} catch (Throwable ignored) {
						// game not ready yet
					}
				}
			}
		}, "Native cosmetics hook");
		thread.setDaemon(true);
		thread.start();
		Log.info("3D cosmetics enabled ({}).", era);
	}

	/** "1.21.5" -> {1,21,5}; "26.3" -> {26,3}; snapshots and junk -> null. */
	static int[] parse(String version) {
		if (version == null) {
			return null;
		}
		String core = version.trim();
		for (char stop : new char[] {'-', '+', ' '}) {
			int at = core.indexOf(stop);
			if (at >= 0) {
				core = core.substring(0, at);
			}
		}
		String[] pieces = core.split("\\.");
		if (pieces.length == 1 && !pieces[0].isEmpty()) {
			pieces = new String[] {pieces[0], "0"};
		}
		if (pieces.length < 2 || pieces.length > 3) {
			return null;
		}
		int[] out = new int[pieces.length];
		for (int i = 0; i < pieces.length; i++) {
			if (!pieces[i].matches("[0-9]{1,4}")) {
				return null;
			}
			out[i] = Integer.parseInt(pieces[i]);
		}
		return out;
	}
}
