package xyz.nativelaunch.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Path;
import java.util.UUID;

/**
 * The mod's single shared state: the skin directory, its sync thread and the
 * account the launcher signed into. Everything here is non-blocking for callers
 * on the render thread.
 */
public final class NativeState {
	public static final String DEFAULT_API = "https://api.playnative.fun";

	private static final NativeState INSTANCE = new NativeState();

	private final SkinDirectory directory = new SkinDirectory();
	private volatile SkinSync sync;
	private volatile boolean started;
	private volatile AccountInfo account;
	private volatile String api = DEFAULT_API;

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

	/** Idempotent. Called from the mod entrypoint and lazily by the hooks. */
	public synchronized void start(Path gameDir) {
		if (started) {
			return;
		}
		started = true;
		TextureCache.init(gameDir);
		directory.setLocal(TextureCache.localLook(gameDir));
		Handoff handoff = Handoff.read(gameDir);
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

	private static String text(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
	}

	/** Only https (or loopback http, for development) is ever accepted as the API address. */
	static String chooseApi(String override, String fromHandoff) {
		for (String candidate : new String[] {override, fromHandoff}) {
			if (candidate != null && acceptable(candidate.trim())) {
				return candidate.trim().replaceAll("/+$", "");
			}
		}
		return DEFAULT_API;
	}

	/** Parses the address instead of prefix-matching, so "http://localhost.evil.com" is not loopback. */
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
		if ("https".equalsIgnoreCase(scheme)) {
			return true;
		}
		return "http".equalsIgnoreCase(scheme) && (host.equals("localhost") || host.equals("127.0.0.1"));
	}

	/**
	 * What to show for a player, or null to leave vanilla alone. Never blocks
	 * (beyond a one-time 1.5 s grace for the very first snapshot).
	 */
	public SkinOverride lookup(String name, UUID id) {
		return lookup(name, id, false);
	}

	/** @param premiumSession the game already has Mojang-signed textures for this player */
	public SkinOverride lookup(String name, UUID id, boolean premiumSession) {
		SkinSync current = sync;
		if (current == null) {
			return null;
		}
		current.awaitFirstAttempt(1500);
		return directory.find(name, id, premiumSession);
	}
}
