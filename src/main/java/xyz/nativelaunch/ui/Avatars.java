package xyz.nativelaunch.ui;

import xyz.nativelaunch.core.Http;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.ui.gfx.Image;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Player faces (skin face + hat layer, 8x8) fetched from the Native texture store, with a lettered fallback. */
public final class Avatars {
	private static final Map<String, Image> faces = new ConcurrentHashMap<String, Image>();
	private static final Map<String, Boolean> requested = new ConcurrentHashMap<String, Boolean>();
	private static final Image NONE = new Image(1, 1, new int[1]);

	private Avatars() {
	}

	/** Draws a rounded avatar for a player. skin = texture hash (may be null). */
	public static void draw(Canvas c, String api, String name, String skin, float x, float y, float size, float radius) {
		Image face = skin == null ? null : face(api, skin);
		if (face != null && face != NONE) {
			c.round(x, y, size, size, radius, 0xFF1A1B20);
			// faces are pixel art: inset slightly so the rounded mask reads as rounded
			c.stamp("face:" + skin, face, x, y, size, size, false, 0xFFFFFFFF);
			c.outline(x, y, size, size, radius, 1, 0x1FFFFFFF);
			return;
		}
		int color = Theme.nameColor(name);
		c.round(x, y, size, size, radius, Theme.mix(color, 0xFF000000, 0.35f));
		String letter = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
		float fs = size * 0.46f;
		float w = c.textWidth(Fonts.SEMIBOLD, fs, letter);
		c.text(Fonts.SEMIBOLD, fs, letter, x + (size - w) / 2, y + (size - c.lineHeight(Fonts.SEMIBOLD, fs)) / 2, 0xFFFFFFFF);
	}

	private static Image face(final String api, final String skin) {
		Image f = faces.get(skin);
		if (f != null) {
			return f;
		}
		boolean mojang = skin.startsWith("mj:") && skin.substring(3).matches("[a-f0-9]{32}");
		if (requested.putIfAbsent(skin, Boolean.TRUE) == null && (mojang || skin.matches("[a-f0-9]{64}"))) {
			Thread t = new Thread(() -> {
				try {
					String url = mojang ? mojangSkinUrl(skin.substring(3)) : api + "/csl/textures/" + skin;
					if (url == null) {
						faces.put(skin, NONE);
						return;
					}
					byte[] png = Http.getBytes(url, 2 * 1024 * 1024);
					Image img = Image.decode(png);
					faces.put(skin, img == null || img.width < 64 ? NONE : crop(img));
				} catch (Throwable e) {
					faces.put(skin, NONE);
				}
			}, "Native-Avatar");
			t.setDaemon(true);
			t.start();
		}
		return null;
	}

	/** Skin URL of a Mojang account (session server profile, textures property). */
	public static String mojangSkinUrl(String uuid) throws java.io.IOException {
		byte[] body = Http.getBytes("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid, 64 * 1024);
		com.google.gson.JsonObject o = new com.google.gson.JsonParser().parse(new String(body, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
		for (com.google.gson.JsonElement e : o.getAsJsonArray("properties")) {
			com.google.gson.JsonObject p = e.getAsJsonObject();
			if (!"textures".equals(p.get("name").getAsString())) {
				continue;
			}
			String json = new String(java.util.Base64.getDecoder().decode(p.get("value").getAsString()), java.nio.charset.StandardCharsets.UTF_8);
			com.google.gson.JsonObject t = new com.google.gson.JsonParser().parse(json).getAsJsonObject().getAsJsonObject("textures");
			if (t != null && t.has("SKIN")) {
				String url = t.getAsJsonObject("SKIN").get("url").getAsString();
				return url.startsWith("http://textures.minecraft.net/") ? "https://" + url.substring(7) : url;
			}
		}
		return null;
	}

	private static Image crop(Image skin) {
		int s = skin.width / 64; // HD skins
		int[] out = new int[64];
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 8; x++) {
				int base = skin.argb[(8 * s + y * s) * skin.width + (8 * s + x * s)] | 0xFF000000;
				int hat = skin.argb[(8 * s + y * s) * skin.width + (40 * s + x * s)];
				int a = hat >>> 24;
				out[y * 8 + x] = a > 128 ? (hat | 0xFF000000) : base;
			}
		}
		Image img = new Image(8, 8, out);
		img.linear = false;
		return img;
	}
}
