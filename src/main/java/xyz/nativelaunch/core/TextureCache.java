package xyz.nativelaunch.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * The launcher's shared texture cache: {@code <hash>.png} files named by the sha256 of
 * their bytes (the same hashes as the API's {@code /csl/textures/<hash>} URLs). The
 * launcher writes its location to {@code <game dir>/.native/launcher.json} (or the older {@code .noctra/}); without it the
 * mod falls back to {@code <game dir>/.native/textures}. Capes the launcher already
 * downloaded in the store are read from disk instead of the network.
 */
public final class TextureCache {
	private static final Pattern HASH = Pattern.compile("^[a-f0-9]{64}$");
	private static volatile Path dir;

	private TextureCache() {
	}

	static void init(Path gameDir) {
		Path chosen = null;
		try {
			Path info = gameDir.resolve(".native").resolve("launcher.json");
			if (!Files.isRegularFile(info)) {
				info = gameDir.resolve(".noctra").resolve("launcher.json");
			}
			if (Files.isRegularFile(info) && Files.size(info) < 16 * 1024) {
				JsonElement root = new JsonParser().parse(new String(Files.readAllBytes(info), StandardCharsets.UTF_8));
				if (root.isJsonObject()) {
					JsonObject o = root.getAsJsonObject();
					if (o.has("textureCache") && o.get("textureCache").isJsonPrimitive()) {
						Path p = Paths.get(o.get("textureCache").getAsString());
						if (p.isAbsolute()) {
							chosen = p;
						}
					}
				}
			}
		} catch (Exception ignored) {
			// fall back below
		}
		dir = chosen != null ? chosen : gameDir.resolve(".native").resolve("textures");
	}

	/**
	 * The offline account's own look from {@code launcher.json} ({@code "local": {name, skin, cape, slim}}),
	 * or null. It is shown on this PC only and never sent anywhere.
	 */
	static SkinEntry localLook(Path gameDir) {
		try {
			Path info = gameDir.resolve(".native").resolve("launcher.json");
			if (!Files.isRegularFile(info)) {
				info = gameDir.resolve(".noctra").resolve("launcher.json");
			}
			if (!Files.isRegularFile(info) || Files.size(info) >= 16 * 1024) {
				return null;
			}
			JsonElement root = new JsonParser().parse(new String(Files.readAllBytes(info), StandardCharsets.UTF_8));
			if (!root.isJsonObject() || !root.getAsJsonObject().has("local") || !root.getAsJsonObject().get("local").isJsonObject()) {
				return null;
			}
			JsonObject local = root.getAsJsonObject().getAsJsonObject("local");
			String name = text(local, "name");
			if (name == null || !name.matches("^[A-Za-z0-9_]{1,16}$")) {
				return null;
			}
			String skin = text(local, "skin");
			String cape = text(local, "cape");
			skin = skin != null && HASH.matcher(skin).matches() ? skin : null;
			cape = cape != null && HASH.matcher(cape).matches() ? cape : null;
			if (skin == null && cape == null) {
				return null;
			}
			boolean slim = local.has("slim") && local.get("slim").isJsonPrimitive() && local.get("slim").getAsBoolean();
			return new SkinEntry(name, slim, skin, cape, null, 0);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String text(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
	}

	/** Cached bytes for a texture hash (verified), or null. */
	public static byte[] read(String hash) {
		Path base = dir;
		if (base == null || hash == null || !HASH.matcher(hash).matches()) {
			return null;
		}
		Path file = base.resolve(hash + ".png");
		try {
			if (!Files.isRegularFile(file)) {
				return null;
			}
			byte[] bytes = Files.readAllBytes(file);
			if (hash.equals(sha256(bytes))) {
				return bytes;
			}
			Files.deleteIfExists(file);
		} catch (Exception ignored) {
			// treat as a miss
		}
		return null;
	}

	/** Saves bytes when they match their hash. Best-effort. */
	public static void write(String hash, byte[] bytes) {
		Path base = dir;
		if (base == null || bytes == null || hash == null || !HASH.matcher(hash).matches()) {
			return;
		}
		try {
			if (!hash.equals(sha256(bytes))) {
				return;
			}
			Files.createDirectories(base);
			Path file = base.resolve(hash + ".png");
			if (Files.exists(file)) {
				return;
			}
			Path tmp = base.resolve(hash + ".png." + Thread.currentThread().getId() + ".tmp");
			Files.write(tmp, bytes);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception ignored) {
			// cache is optional
		}
	}

	/** The texture from the cache, otherwise downloaded from {@code base + hash} and cached. */
	public static byte[] getOrDownload(String base, String hash, int maxBytes) throws IOException {
		byte[] cached = read(hash);
		if (cached != null) {
			return cached;
		}
		byte[] bytes = Http.getBytes(base + hash, maxBytes);
		write(hash, bytes);
		return bytes;
	}

	static String sha256(byte[] bytes) throws Exception {
		byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
		StringBuilder out = new StringBuilder(64);
		for (byte b : digest) {
			out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
		}
		return out.toString();
	}
}
