package xyz.nativelaunch.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import xyz.nativelaunch.core.Http;
import xyz.nativelaunch.core.NativeState;
import xyz.nativelaunch.ui.gfx.Image;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The same ad cards as the launcher's Home, for the title screen. The launcher writes
 * {@code <game dir>/.native/ads.json} at every launch, pointing at the banners it already downloaded (and at the
 * picture of the player's own skin it drew), so nothing is downloaded twice. Started without the launcher, the mod
 * reads the feed from the API itself and keeps the banners in {@code .native/ads/}.
 */
public final class Ads {
	public static final class Button {
		public final String label, action, value;

		Button(String label, String action, String value) {
			this.label = label;
			this.action = action;
			this.value = value;
		}

		public boolean server() {
			return "server".equals(action);
		}
	}

	public static final class Ad {
		public final String id, title, body, tag, url;
		public final List<Button> buttons;
		public final Image image;

		Ad(String id, String title, String body, String tag, String url, List<Button> buttons, Image image) {
			this.id = id;
			this.title = title;
			this.body = body;
			this.tag = tag;
			this.url = url;
			this.buttons = buttons;
			this.image = image;
		}

		/** The main button (clicking the banner does the same). */
		public Button primary() {
			return buttons.isEmpty() ? (url.isEmpty() ? null : new Button("", "url", url)) : buttons.get(0);
		}
	}

	private static final int MAX_IMAGE = 4 * 1024 * 1024;
	private static final Pattern SERVER = Pattern.compile("^[a-z0-9.-]+(:\\d{2,5})?$", Pattern.CASE_INSENSITIVE);
	/** Rounded top corners baked into the banner, as a share of its width (card radius / card width). */
	private static final float CORNER = 16f / 340f;

	private static volatile List<Ad> ads = Collections.emptyList();
	private static volatile boolean loading;
	private static volatile long retryAt;

	private Ads() {
	}

	/** Live ads (empty until loaded; loading starts on first call). */
	public static List<Ad> list() {
		if (!loading && retryAt >= 0 && System.currentTimeMillis() >= retryAt) {
			loading = true;
			Thread t = new Thread(Ads::load, "Native ads");
			t.setDaemon(true);
			t.start();
		}
		return ads;
	}

	private static void load() {
		try {
			Path gameDir = NativeState.get().gameDir();
			List<Ad> out = gameDir == null ? null : fromLauncher(gameDir.resolve(".native"));
			if (out == null || out.isEmpty()) {
				out = fromApi(gameDir == null ? null : gameDir.resolve(".native").resolve("ads"));
			}
			ads = Collections.unmodifiableList(out);
			retryAt = -1; // loaded: once per game session
		} catch (Throwable t) {
			retryAt = System.currentTimeMillis() + 120_000;
		} finally {
			loading = false;
		}
	}

	/** {@code ads.json} from the launcher; null when there is none. */
	private static List<Ad> fromLauncher(Path dir) throws Exception {
		Path file = dir.resolve("ads.json");
		if (!Files.isRegularFile(file) || Files.size(file) > 512 * 1024) {
			return null;
		}
		JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
		Image player = null;
		String playerFile = str(root, "player");
		if (!playerFile.isEmpty()) {
			player = read(Paths.get(playerFile));
		}
		List<Ad> out = new ArrayList<>();
		JsonArray list = root.has("ads") && root.get("ads").isJsonArray() ? root.getAsJsonArray("ads") : new JsonArray();
		for (JsonElement e : list) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject o = e.getAsJsonObject();
			String image = str(o, "file");
			Image banner = image.isEmpty() ? null : read(Paths.get(image));
			Ad ad = parse(o, banner, bool(o, "player") ? player : null);
			if (ad != null) {
				out.add(ad);
			}
		}
		return out;
	}

	private static List<Ad> fromApi(Path cache) throws Exception {
		String body = Http.getJson(Avatars.api() + "/v1/site/ads", null);
		JsonObject root = new JsonParser().parse(body).getAsJsonObject();
		List<Ad> out = new ArrayList<>();
		if (!root.has("ads") || !root.get("ads").isJsonArray()) {
			return out;
		}
		for (JsonElement e : root.getAsJsonArray("ads")) {
			if (!e.isJsonObject() || out.size() >= 20) {
				continue;
			}
			JsonObject o = e.getAsJsonObject();
			String url = str(o, "image");
			if (!url.startsWith("https://")) {
				continue;
			}
			Image banner = null;
			try {
				Path file = cache == null ? null : cache.resolve(sha1(url) + ".img");
				byte[] bytes;
				if (file != null && Files.isRegularFile(file)) {
					bytes = Files.readAllBytes(file);
				} else {
					bytes = Http.getBytes(url, MAX_IMAGE);
					if (file != null) {
						Files.createDirectories(cache);
						Files.write(file, bytes);
					}
				}
				banner = Image.decode(bytes);
			} catch (Exception ignored) {
				// skip this banner
			}
			Ad ad = parse(o, banner, null);
			if (ad != null) {
				out.add(ad);
			}
		}
		return out;
	}

	private static Ad parse(JsonObject o, Image banner, Image player) {
		if (banner == null) {
			return null;
		}
		String url = str(o, "url");
		List<Button> buttons = new ArrayList<>();
		if (o.has("buttons") && o.get("buttons").isJsonArray()) {
			for (JsonElement b : o.getAsJsonArray("buttons")) {
				if (!b.isJsonObject() || buttons.size() >= 2) {
					continue;
				}
				JsonObject bo = b.getAsJsonObject();
				String label = cut(str(bo, "label"), 20);
				String action = "server".equals(str(bo, "action")) ? "server" : "url";
				String value = str(bo, "value");
				boolean ok = action.equals("server") ? SERVER.matcher(value).matches() : value.startsWith("https://");
				if (!label.isEmpty() && ok) {
					buttons.add(new Button(label, action, value));
				}
			}
		} else if (!str(o, "cta").isEmpty() && url.startsWith("https://")) {
			buttons.add(new Button(cut(str(o, "cta"), 20), "url", url));
		}
		return new Ad(str(o, "id"), cut(str(o, "title"), 60), cut(str(o, "body"), 140), cut(str(o, "tag"), 20),
				url.startsWith("https://") ? url : "", buttons, compose(banner, player));
	}

	/**
	 * The banner with the player's skin drawn in like the launcher does (right 2.5%, bottom -2.8%, 112% tall) and
	 * its top corners rounded, so the card draws it as a single picture.
	 */
	static Image compose(Image banner, Image player) {
		int w = banner.width, h = banner.height;
		int[] px = banner.argb.clone();
		if (player != null && player.height > 0) {
			int ph = Math.round(h * 1.12f);
			int pw = Math.max(1, Math.round(ph * (float) player.width / player.height));
			Image p = player.scaled(pw, ph);
			int left = Math.round(w - w * 0.025f) - pw;
			int top = Math.round(h + h * 0.028f) - ph;
			for (int y = Math.max(0, top); y < Math.min(h, top + ph); y++) {
				for (int x = Math.max(0, left); x < Math.min(w, left + pw); x++) {
					int s = p.argb[(y - top) * pw + (x - left)];
					int sa = s >>> 24;
					if (sa == 0) {
						continue;
					}
					px[y * w + x] = over(s, px[y * w + x]);
				}
			}
		}
		if (w > 760) { // area-average down to about the size it is drawn at: crisp without mipmaps
			Image small = new Image(w, h, px).scaled(760, Math.round(760f * h / w));
			w = small.width;
			h = small.height;
			px = small.argb;
		}
		float r = Math.max(2, w * CORNER);
		int ri = (int) Math.ceil(r);
		for (int y = 0; y < Math.min(h, ri); y++) {
			for (int x = 0; x < ri; x++) {
				float dx = r - x - 0.5f, dy = r - y - 0.5f;
				float d = (float) Math.sqrt(dx * dx + dy * dy) - r;
				float cover = d <= -0.5f ? 1 : d >= 0.5f ? 0 : 0.5f - d;
				if (cover >= 1) {
					continue;
				}
				fade(px, y * w + x, cover);
				fade(px, y * w + (w - 1 - x), cover);
			}
		}
		Image out = new Image(w, h, px);
		out.linear = true;
		return out;
	}

	private static void fade(int[] px, int i, float k) {
		int a = Math.round((px[i] >>> 24) * k);
		px[i] = (a << 24) | (px[i] & 0xFFFFFF);
	}

	private static int over(int s, int d) {
		float sa = (s >>> 24) / 255f, da = (d >>> 24) / 255f;
		float oa = sa + da * (1 - sa);
		if (oa <= 0) {
			return 0;
		}
		int r = Math.round((((s >> 16) & 0xFF) * sa + ((d >> 16) & 0xFF) * da * (1 - sa)) / oa);
		int g = Math.round((((s >> 8) & 0xFF) * sa + ((d >> 8) & 0xFF) * da * (1 - sa)) / oa);
		int b = Math.round(((s & 0xFF) * sa + (d & 0xFF) * da * (1 - sa)) / oa);
		return (Math.round(oa * 255) << 24) | (r << 16) | (g << 8) | b;
	}

	/** Opens an ad link in the system browser (https only). */
	public static void open(String url) {
		if (url == null || !url.startsWith("https://")) {
			return;
		}
		Thread t = new Thread(() -> {
			try {
				String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
				ProcessBuilder pb = os.contains("win")
						? new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url)
						: os.contains("mac") ? new ProcessBuilder("open", url) : new ProcessBuilder("xdg-open", url);
				pb.start();
			} catch (Exception ignored) {
				// no browser
			}
		}, "Native ads link");
		t.setDaemon(true);
		t.start();
	}

	private static Image read(Path file) {
		try {
			if (!Files.isRegularFile(file) || Files.size(file) > MAX_IMAGE) {
				return null;
			}
			return Image.decode(Files.readAllBytes(file));
		} catch (Exception e) {
			return null;
		}
	}

	private static String str(JsonObject o, String key) {
		try {
			return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString().trim() : "";
		} catch (Exception e) {
			return "";
		}
	}

	private static boolean bool(JsonObject o, String key) {
		try {
			return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
		} catch (Exception e) {
			return false;
		}
	}

	private static String cut(String s, int n) {
		return s.length() > n ? s.substring(0, n) : s;
	}

	private static String sha1(String s) throws Exception {
		byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
		StringBuilder b = new StringBuilder();
		for (byte x : d) {
			b.append(String.format("%02x", x & 0xFF));
		}
		return b.toString();
	}
}
