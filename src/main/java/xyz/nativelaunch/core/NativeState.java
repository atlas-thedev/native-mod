package xyz.nativelaunch.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/**
 * The mod's single shared state: the skin directory, its sync thread and the
 * account the launcher signed into. Everything here is non-blocking for callers
 * on the render thread.
 */
public final class NativeState {
	public static final String DEFAULT_API = "https://api.playnative.fun";

	/** Hosts (and their subdomains) the mod will send the game ticket to over https. */
	private static final String[] TRUSTED_HOSTS = {"playnative.fun", "nativelaunch.xyz"};

	private static final NativeState INSTANCE = new NativeState();

	private final SkinDirectory directory = new SkinDirectory();
	private volatile SkinSync sync;
	private volatile boolean started;
	private volatile AccountInfo account;
	private volatile String api = DEFAULT_API;
	private volatile Path gameDir;
	private volatile Handoff handoff;

	private NativeState() {
	}

	public static NativeState get() {
		return INSTANCE;
	}

	public SkinDirectory directory() {
		return directory;
	}

	/** The Native account the launcher connected, or null in guest mode. */
	public AccountInfo account() {
		return account;
	}

	public String api() {
		return api;
	}

	/** The instance folder the game runs in (null before start). */
	public Path gameDir() {
		return gameDir;
	}

	/**
	 * The launcher hand-off read once at start (null in guest mode). Read it from here instead of
	 * calling {@link Handoff#read} again, so every feature uses the same ticket.
	 */
	public Handoff handoff() {
		return handoff;
	}

	/** Idempotent. Called from the mod entrypoint and lazily by the hooks. */
	public synchronized void start(Path gameDir) {
		if (started) {
			return;
		}
		started = true;
		this.gameDir = gameDir;
		TextureCache.init(gameDir);
		directory.setLocal(TextureCache.localLook(gameDir));
		Handoff handoff = Handoff.read(gameDir);
		this.handoff = handoff;
		api = chooseApi(System.getProperty("native.api", System.getProperty("noctra.api")), handoff == null ? null : handoff.api);
		SkinRefresh.install(directory);
		sync = new SkinSync(directory, api);
		sync.start();
		if (handoff != null) {
			final Handoff h = handoff;
			Thread thread = new Thread(new Runnable() {
				@Override
				public void run() {
					verify(h);
				}
			}, "Native-Account");
			thread.setDaemon(true);
			thread.start();
		} else {
			Log.info("No launcher hand-off found: running as a guest (skins and capes still load).");
		}
	}

	private void verify(Handoff handoff) {
		try {
			String body = Http.getJson(api + "/v1/mod/me", handoff.ticket);
			JsonObject account = new JsonParser().parse(body).getAsJsonObject().getAsJsonObject("account");
			this.account = new AccountInfo(
					text(account, "id"), text(account, "name"), text(account, "uuid"), text(account, "model"));
			Log.info("Connected to Native account {}", this.account.name);
		} catch (Exception e) {
			Log.warn("Could not verify the Native account ({}). Continuing as a guest.", e.getMessage());
		}
	}

	/** Strings and numbers only: an object or array here is a malformed reply, not a value. */
	private static String text(JsonObject o, String key) {
		if (o == null || !o.has(key)) {
			return null;
		}
		JsonElement value = o.get(key);
		return value.isJsonPrimitive() ? value.getAsString() : null;
	}

	/** Only the Native API over https (or loopback http, for development) is ever accepted. */
	static String chooseApi(String override, String fromHandoff) {
		for (String candidate : new String[] {override, fromHandoff}) {
			if (candidate != null && acceptable(candidate.trim())) {
				return candidate.trim().replaceAll("/+$", "");
			}
		}
		return DEFAULT_API;
	}

	/**
	 * Parses the address instead of prefix-matching, so "http://localhost.evil.com" is not loopback.
	 * The game ticket is sent to this host, so https must also be one of Native's own domains:
	 * a tampered session.json can no longer point the mod at someone else's server.
	 */
	static boolean acceptable(String url) {
		java.net.URI uri;
		try {
			uri = new java.net.URI(url);
		} catch (java.net.URISyntaxException e) {
			return false;
		}
		String scheme = uri.getScheme();
		String host = uri.getHost();
		if (scheme == null || host == null || uri.getUserInfo() != null) {
			return false;
		}
		host = host.toLowerCase(Locale.ROOT);
		boolean loopback = host.equals("localhost") || host.equals("127.0.0.1");
		if ("https".equalsIgnoreCase(scheme)) {
			return loopback || trusted(host);
		}
		return "http".equalsIgnoreCase(scheme) && loopback;
	}

	private static boolean trusted(String host) {
		for (String domain : TRUSTED_HOSTS) {
			if (host.equals(domain) || host.endsWith("." + domain)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * What to show for a player, or null to leave vanilla alone. Never blocks: before the first
	 * snapshot arrives this returns null, and SkinRefresh re-applies skins once it lands.
	 */
	public SkinOverride lookup(String name, UUID id) {
		return lookup(name, id, false);
	}

	/** @param premiumSession the game already has Mojang-signed textures for this player */
	public SkinOverride lookup(String name, UUID id, boolean premiumSession) {
		if (sync == null) {
			return null;
		}
		return directory.find(name, id, premiumSession);
	}
}
