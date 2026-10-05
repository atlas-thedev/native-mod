package xyz.nativelaunch.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Keeps a {@link SkinDirectory} current: one snapshot up front, then a
 * Server-Sent-Events stream so a skin or cape change anywhere on Native reaches
 * this game within moments. Runs on a single daemon thread and reconnects
 * with back-off, so it can never block or crash the game.
 */
public final class SkinSync {
	private final SkinDirectory directory;
	private final String api;
	private final CountDownLatch firstAttempt = new CountDownLatch(1);
	private volatile boolean started;
	private volatile boolean stopped;

	public SkinSync(SkinDirectory directory, String api) {
		this.directory = directory;
		this.api = api.endsWith("/") ? api.substring(0, api.length() - 1) : api;
	}

	public synchronized void start() {
		if (started) {
			return;
		}
		started = true;
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				loop();
			}
		}, "Native-Skins");
		thread.setDaemon(true);
		thread.start();
	}

	public void stop() {
		stopped = true;
	}

	/** Wait (briefly) for the first snapshot so the very first lookup is not empty. */
	public void awaitFirstAttempt(long millis) {
		try {
			firstAttempt.await(millis, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** A stream that stayed up this long counts as healthy: only then is the retry delay reset. */
	private static final long HEALTHY_STREAM_NANOS = TimeUnit.SECONDS.toNanos(30);

	private void loop() {
		long backoff = 2000;
		while (!stopped) {
			long streamStarted = 0;
			try {
				fetchDirectory();
				firstAttempt.countDown();
				streamStarted = System.nanoTime();
				stream();
			} catch (IOException | RuntimeException e) {
				Log.debug("Skin sync interrupted: {}", e.toString());
			} finally {
				firstAttempt.countDown();
			}
			if (stopped) {
				return;
			}
			// A stream that dies at once (e.g. HTTP 429) must back off, not retry every 2 seconds.
			if (streamStarted != 0 && System.nanoTime() - streamStarted >= HEALTHY_STREAM_NANOS) {
				backoff = 2000;
			}
			try {
				Thread.sleep(backoff);
			} catch (InterruptedException e) {
				return;
			}
			backoff = Math.min(backoff * 2, 60_000);
		}
	}

	String query() {
		return "?epoch=" + directory.epoch() + "&since=" + directory.revision();
	}

	void fetchDirectory() throws IOException {
		String body = Http.getJson(api + "/v1/skins/directory" + query(), null);
		applySnapshot(directory, body);
		Log.info("Skin directory ready: {} players with custom looks", directory.size());
	}

	/** Visible for tests. */
	static void applySnapshot(SkinDirectory directory, String body) {
		JsonObject root = new JsonParser().parse(body).getAsJsonObject();
		long epoch = longOf(root, "epoch", -1);
		long rev = longOf(root, "rev", 0);
		String base = root.has("textureBase") ? root.get("textureBase").getAsString() : "";
		boolean full = !root.has("full") || root.get("full").getAsBoolean();
		JsonArray array = root.has("entries") ? root.getAsJsonArray("entries") : new JsonArray();
		List<SkinEntry> parsed = new ArrayList<SkinEntry>();
		for (JsonElement element : array) {
			SkinEntry entry = entry(element.getAsJsonObject());
			if (entry != null) {
				parsed.add(entry);
			}
		}
		if (full) {
			directory.replaceAll(epoch, rev, base, parsed);
		} else {
			directory.setTextureBase(base);
			for (SkinEntry entry : parsed) {
				directory.apply(entry, entry.revision);
			}
			directory.setRevision(rev);
		}
	}

	static SkinEntry entry(JsonObject o) {
		if (!o.has("n")) {
			return null;
		}
		String stripHash = null;
		int frames = 0;
		int fps = 0;
		// "a": {"h": strip hash, "f": frames, "p": fps} for animated capes; null or absent otherwise.
		// Parsed defensively: a malformed animation must never cost the player their skin or cape.
		try {
			if (o.has("a") && o.get("a").isJsonObject()) {
				JsonObject a = o.getAsJsonObject("a");
				stripHash = nullable(a, "h");
				frames = (int) longOf(a, "f", 0);
				fps = (int) longOf(a, "p", 0);
			}
		} catch (RuntimeException ignored) {
			stripHash = null;
		}
		// "p": {"c": cape hash, "a": animation} — the premium look for a same-name linked player.
		SkinEntry premium = null;
		try {
			if (o.has("p") && o.get("p").isJsonObject()) {
				JsonObject p = o.getAsJsonObject("p");
				String pStrip = null;
				int pFrames = 0;
				int pFps = 0;
				if (p.has("a") && p.get("a").isJsonObject()) {
					JsonObject a = p.getAsJsonObject("a");
					pStrip = nullable(a, "h");
					pFrames = (int) longOf(a, "f", 0);
					pFps = (int) longOf(a, "p", 0);
				}
				premium = new SkinEntry(o.get("n").getAsString(), false, null, nullable(p, "c"), nullable(o, "u"),
						longOf(o, "r", 0), pStrip, pFrames, pFps);
			}
		} catch (RuntimeException ignored) {
			premium = null;
		}
		return new SkinEntry(
				o.get("n").getAsString(),
				o.has("m") && "slim".equals(o.get("m").getAsString()),
				nullable(o, "s"),
				nullable(o, "c"),
				nullable(o, "u"),
				longOf(o, "r", 0),
				stripHash,
				frames,
				fps,
				premium);
	}

	private static String nullable(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
	}

	private static long longOf(JsonObject o, String key, long fallback) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsLong() : fallback;
	}

	private void stream() throws IOException {
		HttpURLConnection connection = Http.open(api + "/v1/skins/stream" + query(), null, 8000, 75_000, "text/event-stream");
		try {
			if (connection.getResponseCode() / 100 != 2) {
				throw new IOException("Stream HTTP " + connection.getResponseCode());
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
			String event = null;
			StringBuilder data = new StringBuilder();
			String line;
			while (!stopped && (line = reader.readLine()) != null) {
				if (line.isEmpty()) {
					if (event != null && data.length() > 0) {
						handle(event, data.toString());
					}
					event = null;
					data.setLength(0);
				} else if (line.startsWith("event:")) {
					event = line.substring(6).trim();
				} else if (line.startsWith("data:")) {
					data.append(line.substring(5).trim());
				}
				// ": ping" comments and id: lines need no handling
			}
		} finally {
			connection.disconnect();
		}
	}

	private void handle(String event, String data) throws IOException {
		JsonObject payload = new JsonParser().parse(data).getAsJsonObject();
		if ("hello".equals(event)) {
			if (payload.has("resync") && payload.get("resync").getAsBoolean()) {
				fetchDirectory();
			}
		} else if ("skin".equals(event)) {
			SkinEntry entry = entry(payload);
			if (entry != null) {
				directory.apply(entry, entry.revision);
				Log.debug("Skin update for {}", entry.name);
			}
		}
	}
}
