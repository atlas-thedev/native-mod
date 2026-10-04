package xyz.nativelaunch.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Builds and reads the "textures" profile property that newer Minecraft
 * versions (1.20.3+) pass around. Our version carries a {@code "native":true} (or the legacy {@code "noctra":true})
 * marker so the session-service hook can recognise it and skip Mojang's
 * texture-domain whitelist, which would otherwise reject our URLs.
 */
public final class Textures {
	private Textures() {
	}

	/** CAPE metadata keys describing an animated cape. */
	public static final String ANIM_STRIP = "native_anim_strip";
	public static final String ANIM_FRAMES = "native_anim_frames";
	public static final String ANIM_FPS = "native_anim_fps";
	public static final String LEGACY_ANIM_STRIP = "noctra_anim_strip";
	public static final String LEGACY_ANIM_FRAMES = "noctra_anim_frames";
	public static final String LEGACY_ANIM_FPS = "noctra_anim_fps";

	/** The decoded contents of a Native textures property. */
	public static final class Parsed {
		public final String skinUrl;
		public final boolean slim;
		public final String capeUrl;
		public final String elytraUrl;
		/** Animated cape strip URL from the CAPE metadata, or null. */
		public final String capeStripUrl;
		public final int capeFrames;
		public final int capeFps;

		Parsed(String skinUrl, boolean slim, String capeUrl, String elytraUrl) {
			this(skinUrl, slim, capeUrl, elytraUrl, null, 0, 0);
		}

		Parsed(String skinUrl, boolean slim, String capeUrl, String elytraUrl, String capeStripUrl, int capeFrames, int capeFps) {
			this.skinUrl = skinUrl;
			this.slim = slim;
			this.capeUrl = capeUrl;
			this.elytraUrl = elytraUrl;
			this.capeStripUrl = capeStripUrl;
			this.capeFrames = capeFrames;
			this.capeFps = capeFps;
		}
	}

	/**
	 * Merge Native's textures over whatever the profile already had (Mojang's
	 * skin stays when Native only supplies a cape, and the other way round).
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
			if (override.hasAnimatedCape()) {
				// Texture metadata is a string map in authlib; vanilla ignores unknown keys and keeps
				// showing "url" (the first frame). Renderers that can animate read the strip from here.
				JsonObject metadata = new JsonObject();
				metadata.addProperty(ANIM_STRIP, override.capeStripUrl);
				metadata.addProperty(ANIM_FRAMES, Integer.toString(override.capeFrames));
				metadata.addProperty(ANIM_FPS, Integer.toString(override.capeFps));
				metadata.addProperty(LEGACY_ANIM_STRIP, override.capeStripUrl);
				metadata.addProperty(LEGACY_ANIM_FRAMES, Integer.toString(override.capeFrames));
				metadata.addProperty(LEGACY_ANIM_FPS, Integer.toString(override.capeFps));
				cape.add("metadata", metadata);
			}
			textures.add("CAPE", cape);
		}
		root.addProperty("timestamp", System.currentTimeMillis());
		if (id != null) {
			root.addProperty("profileId", id.toString().replace("-", ""));
		}
		root.addProperty("profileName", name);
		root.addProperty("native", true);
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
		boolean isOurs = (root != null) &&
				((root.has("native") && root.get("native").isJsonPrimitive() && root.get("native").getAsBoolean()) ||
				 (root.has("noctra") && root.get("noctra").isJsonPrimitive() && root.get("noctra").getAsBoolean()));
		if (!isOurs) {
			return null;
		}
		JsonObject textures = root.has("textures") && root.get("textures").isJsonObject() ? root.getAsJsonObject("textures") : new JsonObject();
		JsonObject skin = obj(textures, "SKIN");
		boolean slim = false;
		if (skin != null && skin.has("metadata") && skin.get("metadata").isJsonObject()) {
			JsonObject metadata = skin.getAsJsonObject("metadata");
			slim = metadata.has("model") && "slim".equals(metadata.get("model").getAsString());
		}
		JsonObject cape = obj(textures, "CAPE");
		String strip = null;
		int frames = 0;
		int fps = 0;
		try {
			JsonObject meta = cape != null ? obj(cape, "metadata") : null;
			if (meta != null && (meta.has(ANIM_STRIP) || meta.has(LEGACY_ANIM_STRIP))) {
				String keyStrip = meta.has(ANIM_STRIP) ? ANIM_STRIP : LEGACY_ANIM_STRIP;
				String keyFrames = meta.has(ANIM_FRAMES) ? ANIM_FRAMES : LEGACY_ANIM_FRAMES;
				String keyFps = meta.has(ANIM_FPS) ? ANIM_FPS : LEGACY_ANIM_FPS;
				strip = meta.get(keyStrip).getAsString();
				frames = Integer.parseInt(meta.get(keyFrames).getAsString());
				fps = Integer.parseInt(meta.get(keyFps).getAsString());
			}
		} catch (RuntimeException ignored) {
			strip = null;
		}
		if (strip == null || frames < 2 || fps < 1) {
			strip = null;
			frames = 0;
			fps = 0;
		}
		return new Parsed(url(skin), slim, url(cape), url(obj(textures, "ELYTRA")), strip, frames, fps);
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
