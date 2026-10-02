package dev.noctra.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Builds and reads the "textures" profile property that newer Minecraft
 * versions (1.20.3+) pass around. Our version carries a {@code "noctra":true}
 * marker so the session-service hook can recognise it and skip Mojang's
 * texture-domain whitelist, which would otherwise reject our URLs.
 */
public final class Textures {
	private Textures() {
	}

	/** The decoded contents of a Noctra textures property. */
	public static final class Parsed {
		public final String skinUrl;
		public final boolean slim;
		public final String capeUrl;
		public final String elytraUrl;

		Parsed(String skinUrl, boolean slim, String capeUrl, String elytraUrl) {
			this.skinUrl = skinUrl;
			this.slim = slim;
			this.capeUrl = capeUrl;
			this.elytraUrl = elytraUrl;
		}
	}

	/**
	 * Merge Noctra's textures over whatever the profile already had (Mojang's
	 * skin stays when Noctra only supplies a cape, and the other way round).
	 *
	 * @param existingValue the profile's current base64 "textures" value, or null
	 * @return the new base64 value
	 */
	public static String pack(String existingValue, SkinOverride override, UUID id, String name) {
		JsonObject root = new JsonObject();
		JsonObject textures = new JsonObject();
		if (existingValue != null) {
			try {
				JsonObject old = parseObject(decode(existingValue));
				if (old != null && old.has("textures") && old.get("textures").isJsonObject()) {
					for (java.util.Map.Entry<String, JsonElement> e : old.getAsJsonObject("textures").entrySet()) {
						textures.add(e.getKey(), e.getValue());
					}
				}
			} catch (RuntimeException ignored) {
				// unreadable existing property: start from nothing
			}
		}
		if (override.skinUrl != null) {
			JsonObject skin = new JsonObject();
			skin.addProperty("url", override.skinUrl);
			if (override.slim) {
				JsonObject metadata = new JsonObject();
				metadata.addProperty("model", "slim");
				skin.add("metadata", metadata);
			}
			textures.add("SKIN", skin);
		}
		if (override.capeUrl != null) {
			JsonObject cape = new JsonObject();
			cape.addProperty("url", override.capeUrl);
			textures.add("CAPE", cape);
		}
		root.addProperty("timestamp", System.currentTimeMillis());
		if (id != null) {
			root.addProperty("profileId", id.toString().replace("-", ""));
		}
		root.addProperty("profileName", name);
		root.addProperty("noctra", true);
		root.add("textures", textures);
		return encode(root.toString());
	}

	/** Decode a property value; null unless it was produced by {@link #pack}. */
	public static Parsed parse(String value) {
		if (value == null || value.length() < 16) {
			return null;
		}
		JsonObject root;
		try {
			root = parseObject(decode(value));
		} catch (RuntimeException e) {
			return null;
		}
		if (root == null || !root.has("noctra") || !root.get("noctra").isJsonPrimitive() || !root.get("noctra").getAsBoolean()) {
			return null;
		}
		JsonObject textures = root.has("textures") && root.get("textures").isJsonObject() ? root.getAsJsonObject("textures") : new JsonObject();
		JsonObject skin = obj(textures, "SKIN");
		boolean slim = false;
		if (skin != null && skin.has("metadata") && skin.get("metadata").isJsonObject()) {
			JsonObject metadata = skin.getAsJsonObject("metadata");
			slim = metadata.has("model") && "slim".equals(metadata.get("model").getAsString());
		}
		return new Parsed(url(skin), slim, url(obj(textures, "CAPE")), url(obj(textures, "ELYTRA")));
	}

	private static JsonObject obj(JsonObject parent, String key) {
		return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
	}

	private static String url(JsonObject texture) {
		return texture != null && texture.has("url") && texture.get("url").isJsonPrimitive() ? texture.get("url").getAsString() : null;
	}

	private static JsonObject parseObject(String json) {
		JsonElement element = new JsonParser().parse(json);
		return element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	private static String decode(String base64) {
		return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
	}

	private static String encode(String json) {
		return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}
}
