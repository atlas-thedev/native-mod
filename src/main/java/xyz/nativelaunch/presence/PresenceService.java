package xyz.nativelaunch.presence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import xyz.nativelaunch.core.Http;
import xyz.nativelaunch.core.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What the player is doing, shared two ways:
 * <ul>
 *   <li>Discord Rich Presence: server or world icon, player count, how long they've been playing.</li>
 *   <li>{@code <game dir>/.native/presence.json}: the launcher reads it to keep Relay (friends'
 *       "In-game: Hypixel" line, the Join button, the online dot) exactly in sync with the game.</li>
 * </ul>
 * One daemon thread; nothing here ever runs on the render thread.
 */
public final class PresenceService implements Runnable {
	static final String CLIENT_ID = "1465139441457827972";
	private static final String SITE = "https://playnative.fun";
	private static final long TICK_MS = 2000;
	private static final long PING_MS = 60_000;
	private static final long DISCORD_RETRY_MS = 15_000;
	private static final long DISCORD_MIN_GAP_MS = 4_000;

	private static volatile PresenceService running;

	private final Path gameDir;
	private final String api;
	private final String ticket;
	private final boolean discordEnabled;
	private final String mcVersion;
	private final GameProbe probe = new GameProbe();
	private final DiscordIpc discord = new DiscordIpc(CLIENT_ID);
	private final Map<String, String> iconUrls = new HashMap<String, String>();

	private String stateKey;
	private long stateSince = System.currentTimeMillis();
	private long nextPing;
	private ServerPing.Result ping;
	private String lastFile;
	private String lastActivity;
	private long lastDiscordPush;
	private long nextDiscordTry;

	PresenceService(Path gameDir, String api, String ticket, boolean discordEnabled) {
		this.gameDir = gameDir;
		this.api = api;
		this.ticket = ticket;
		this.discordEnabled = discordEnabled;
		this.mcVersion = minecraftVersion();
	}

	/** Idempotent. */
	public static synchronized void start(Path gameDir, String api, String ticket) {
		if (running != null || gameDir == null) {
			return;
		}
		boolean discord = discordSetting(gameDir) && !"false".equalsIgnoreCase(System.getProperty("native.discord"));
		running = new PresenceService(gameDir, api, ticket, discord);
		Thread thread = new Thread(running, "Native-Presence");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				PresenceService s = running;
				if (s != null) {
					s.shutdown();
				}
			}
		}, "Native-Presence-Stop"));
	}

	@Override
	public void run() {
		Log.info("Presence started{}.", discordEnabled ? " (Discord Rich Presence on)" : " (Discord Rich Presence off)");
		while (true) {
			try {
				tick();
			} catch (Throwable t) {
				Log.debug("Presence tick failed: {}", t.toString());
			}
			try {
				Thread.sleep(TICK_MS);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	private void tick() {
		GameProbe.Snapshot s = probe.read();
		if (s == null) {
			s = new GameProbe.Snapshot();
		}
		long now = System.currentTimeMillis();
		String key = s.key();
		if (!key.equals(stateKey)) {
			stateKey = key;
			stateSince = now;
			ping = null;
			nextPing = now; // ping the new server straight away
		}
		if (s.kind == GameProbe.Kind.MULTIPLAYER && s.address != null && !s.lan && now >= nextPing) {
			nextPing = now + PING_MS;
			try {
				ping = ServerPing.ping(s.address, 5000);
			} catch (IOException | RuntimeException e) {
				if (ping == null) {
					ping = new ServerPing.Result();
				}
			}
		}
		writeFile(s);
		if (discordEnabled) {
			updateDiscord(s, now);
		}
	}

	/* ── Relay hand-off file ───────────────────────────────────────── */

	private void writeFile(GameProbe.Snapshot s) {
		JsonObject o = new JsonObject();
		o.addProperty("v", 1);
		o.addProperty("state", s.kind.name().toLowerCase(Locale.ROOT));
		o.addProperty("since", stateSince);
		if (s.kind == GameProbe.Kind.MULTIPLAYER) {
			boolean open = isPublicAddress(s.address) && !s.lan;
			o.addProperty("label", serverLabel(s));
			if (open) {
				o.addProperty("address", normalizeAddress(s.address));
			}
			int[] count = players(s);
			if (count[0] >= 0) {
				o.addProperty("online", count[0]);
			}
			if (count[1] > 0) {
				o.addProperty("max", count[1]);
			}
		} else if (s.kind == GameProbe.Kind.SINGLEPLAYER) {
			if (s.worldName != null) {
				o.addProperty("world", s.worldName);
			}
			o.addProperty("lan", s.published);
		}
		String body = o.toString();
		if (body.equals(lastFile)) {
			return;
		}
		lastFile = body;
		JsonObject stamped = new JsonParser().parse(body).getAsJsonObject();
		stamped.addProperty("updatedAt", System.currentTimeMillis());
		try {
			Path dir = gameDir.resolve(".native");
			Files.createDirectories(dir);
			Path tmp = dir.resolve("presence.json.tmp");
			Files.write(tmp, stamped.toString().getBytes(StandardCharsets.UTF_8));
			Files.move(tmp, dir.resolve("presence.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException | RuntimeException e) {
			try {
				Files.write(gameDir.resolve(".native").resolve("presence.json"), stamped.toString().getBytes(StandardCharsets.UTF_8));
			} catch (IOException | RuntimeException ignored) {
				// the launcher falls back to the game log
			}
		}
	}

	/* ── Discord ───────────────────────────────────────────────────── */

	private void updateDiscord(GameProbe.Snapshot s, long now) {
		if (!discord.connected()) {
			if (now < nextDiscordTry) {
				return;
			}
			nextDiscordTry = now + DISCORD_RETRY_MS;
			if (!discord.connect()) {
				return;
			}
			lastActivity = null;
			Log.info("Connected to Discord.");
		}
		try {
			discord.pump();
		} catch (IOException e) {
			nextDiscordTry = now + DISCORD_RETRY_MS;
			return;
		}
		JsonObject activity = activity(s);
		String body = activity.toString();
		if (body.equals(lastActivity) || now - lastDiscordPush < DISCORD_MIN_GAP_MS) {
			return;
		}
		try {
			discord.setActivity(activity);
			lastActivity = body;
			lastDiscordPush = now;
		} catch (IOException e) {
			nextDiscordTry = now + DISCORD_RETRY_MS;
		}
	}

	JsonObject activity(GameProbe.Snapshot s) {
		JsonObject a = new JsonObject();
		JsonObject assets = new JsonObject();
		String version = mcVersion == null ? "Minecraft" : "Minecraft " + mcVersion;
		assets.addProperty("small_image", "logo");
		assets.addProperty("small_text", "Native Client · " + version);
		switch (s.kind) {
			case MULTIPLAYER: {
				String label = serverLabel(s);
				boolean open = isPublicAddress(s.address) && !s.lan;
				a.addProperty("details", open ? "Playing on " + label : (s.lan ? "Playing on LAN" : "Playing on a private server"));
				a.addProperty("state", open ? displayHost(s.address) : "Multiplayer");
				int[] count = players(s);
				if (count[0] > 0 && count[1] >= count[0]) {
					JsonObject party = new JsonObject();
					party.addProperty("id", "native-" + sha256(normalizeAddress(s.address == null ? "" : s.address)).substring(0, 16));
					JsonArray size = new JsonArray();
					size.add(new com.google.gson.JsonPrimitive(count[0]));
					size.add(new com.google.gson.JsonPrimitive(count[1]));
					party.add("size", size);
					a.add("party", party);
				}
				String icon = open && ping != null && ping.icon != null ? iconUrl("server:" + normalizeAddress(s.address), ping.icon) : null;
				if (icon == null && open) {
					icon = "https://api.mcsrvstat.us/icon/" + normalizeAddress(s.address);
				}
				assets.addProperty("large_image", icon == null ? "logo" : icon);
				assets.addProperty("large_text", open ? displayHost(s.address) : (s.lan ? "LAN world" : "Private server"));
				break;
			}
			case SINGLEPLAYER: {
				String world = s.worldName == null || s.worldName.trim().isEmpty() ? "a world" : s.worldName.trim();
				a.addProperty("details", "Playing Singleplayer");
				a.addProperty("state", s.published ? world + " · Open to LAN" : world);
				String icon = worldIcon(s.worldId);
				assets.addProperty("large_image", icon == null ? "logo" : icon);
				assets.addProperty("large_text", world);
				break;
			}
			case REALMS:
				a.addProperty("details", "Playing on Realms");
				a.addProperty("state", version);
				assets.addProperty("large_image", "logo");
				assets.addProperty("large_text", "Minecraft Realms");
				break;
			default:
				a.addProperty("details", "In the menus");
				a.addProperty("state", version);
				assets.addProperty("large_image", "logo");
				assets.addProperty("large_text", "Native Client");
				break;
		}
		JsonObject timestamps = new JsonObject();
		timestamps.addProperty("start", stateSince);
		a.add("timestamps", timestamps);
		a.add("assets", assets);
		JsonArray buttons = new JsonArray();
		JsonObject get = new JsonObject();
		get.addProperty("label", "Get Native Client");
		get.addProperty("url", SITE);
		buttons.add(get);
		a.add("buttons", buttons);
		return a;
	}

	private int[] players(GameProbe.Snapshot s) {
		int online = ping != null && ping.online >= 0 ? ping.online : s.tabPlayers;
		int max = ping != null && ping.max > 0 ? ping.max : -1;
		if (max > 0 && online > max) {
			max = online;
		}
		return new int[] {online, max};
	}

	/* ── Icons (Discord needs a URL, so they are hosted by Native) ── */

	private String worldIcon(String worldId) {
		if (worldId == null || worldId.contains("..") || worldId.contains("/") || worldId.contains("\\")) {
			return null;
		}
		Path icon = gameDir.resolve("saves").resolve(worldId).resolve("icon.png");
		try {
			if (!Files.isRegularFile(icon) || Files.size(icon) > 256 * 1024) {
				return null;
			}
			return iconUrl("world:" + worldId + ":" + Files.getLastModifiedTime(icon).toMillis(), Files.readAllBytes(icon));
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	private String iconUrl(String cacheKey, byte[] png) {
		if (iconUrls.containsKey(cacheKey)) {
			return iconUrls.get(cacheKey);
		}
		String url = null;
		if (ticket != null && png != null && png.length > 8 && png.length <= 256 * 1024) {
			try {
				String body = Http.postBytes(api + "/v1/mod/presence-icon", ticket, png, "image/png");
				JsonElement root = new JsonParser().parse(body);
				if (root.isJsonObject() && root.getAsJsonObject().has("url")) {
					String candidate = root.getAsJsonObject().get("url").getAsString();
					if (candidate.startsWith("https://")) {
						url = candidate;
					}
				}
			} catch (IOException | RuntimeException e) {
				Log.debug("Could not share the presence icon: {}", e.toString());
			}
		}
		iconUrls.put(cacheKey, url);
		return url;
	}

	/* ── Helpers ───────────────────────────────────────────────────── */

	private static final String[][] KNOWN = {
		{"hypixel.net", "Hypixel"}, {"donutsmp.net", "Donut SMP"}, {"cubecraft.net", "CubeCraft"},
		{"playhive.com", "The Hive"}, {"minemen.club", "Minemen Club"}, {"mineplex.com", "Mineplex"},
		{"2b2t.org", "2b2t"}, {"wynncraft.com", "Wynncraft"}, {"mccentral.org", "MC Central"},
		{"pvp.land", "PvP Land"}, {"lunar.gg", "Lunar Network"}, {"minehut.gg", "Minehut"}
	};

	static String serverLabel(GameProbe.Snapshot s) {
		String host = displayHost(s.address).toLowerCase(Locale.ROOT);
		for (String[] known : KNOWN) {
			if (host.equals(known[0]) || host.endsWith("." + known[0])) {
				return known[1];
			}
		}
		String name = s.serverName == null ? "" : s.serverName.trim();
		if (!name.isEmpty() && !name.equalsIgnoreCase("Minecraft Server") && !name.equalsIgnoreCase(host) && name.length() <= 40) {
			return name;
		}
		if (host.isEmpty() || host.matches("[0-9.]+") || host.contains(":")) {
			return "a server";
		}
		String[] parts = host.split("\\.");
		String main = parts.length >= 2 ? parts[parts.length - 2] : parts[0];
		return main.isEmpty() ? host : Character.toUpperCase(main.charAt(0)) + main.substring(1);
	}

	static String displayHost(String address) {
		String host = ServerPing.split(address)[0];
		return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
	}

	/** host, or host:port when it is not the default port. */
	static String normalizeAddress(String address) {
		String[] parts = ServerPing.split(address);
		String host = displayHost(address).toLowerCase(Locale.ROOT);
		String port = parts[1] == null ? null : parts[1].trim();
		if (port == null || port.isEmpty() || "25565".equals(port)) {
			return host;
		}
		return (host.contains(":") ? "[" + host + "]" : host) + ":" + port;
	}

	/** Loopback, LAN and link-local addresses are useless (and private) for friends and Discord. */
	static boolean isPublicAddress(String address) {
		if (address == null || address.trim().isEmpty()) {
			return false;
		}
		String host = displayHost(address).toLowerCase(Locale.ROOT);
		if (host.equals("localhost") || host.endsWith(".local") || host.endsWith(".lan") || !host.contains(".") && !host.contains(":")) {
			return false;
		}
		if (host.matches("[0-9.]+") || host.contains(":")) {
			try {
				InetAddress ip = InetAddress.getByName(host); // a literal: no DNS lookup
				return !(ip.isLoopbackAddress() || ip.isSiteLocalAddress() || ip.isLinkLocalAddress() || ip.isAnyLocalAddress()
						|| host.startsWith("100.64.") || host.startsWith("fc") || host.startsWith("fd"));
			} catch (IOException e) {
				return false;
			}
		}
		return true;
	}

	static boolean discordSetting(Path gameDir) {
		for (String dirName : new String[] {".native", ".noctra"}) {
			Path info = gameDir.resolve(dirName).resolve("launcher.json");
			try {
				if (!Files.isRegularFile(info) || Files.size(info) > 64 * 1024) {
					continue;
				}
				JsonElement root = new JsonParser().parse(new String(Files.readAllBytes(info), StandardCharsets.UTF_8));
				if (root.isJsonObject() && root.getAsJsonObject().has("presence") && root.getAsJsonObject().get("presence").isJsonObject()) {
					JsonObject p = root.getAsJsonObject().getAsJsonObject("presence");
					if (p.has("discord") && p.get("discord").isJsonPrimitive()) {
						return p.get("discord").getAsBoolean();
					}
				}
				return true;
			} catch (IOException | RuntimeException ignored) {
				// default on
			}
		}
		return true;
	}

	private static String minecraftVersion() {
		try {
			return FabricLoader.getInstance().getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null);
		} catch (Throwable t) {
			return null;
		}
	}

	private static String sha256(String value) {
		try {
			byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : d) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			return Integer.toHexString(value.hashCode()) + "0000000000000000";
		}
	}

	private void shutdown() {
		try {
			if (discord.connected()) {
				discord.setActivity(null);
			}
		} catch (IOException ignored) {
			// Discord drops the activity when the pipe closes anyway
		}
		discord.close();
		try {
			Files.deleteIfExists(gameDir.resolve(".native").resolve("presence.json"));
		} catch (IOException ignored) {
			// the launcher ignores stale files once the game exits
		}
	}
}
