package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Image;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The game's own icons for the status modules: item and block textures, potion effect icons and the heart / food /
 * armour pips. They are read from the vanilla assets on the class path (1.16 - 26.x all ship them at the same paths),
 * decoded once and cached; anything the game doesn't have simply comes back null and the module draws a plain tile.
 */
public final class Vanilla {
	private Vanilla() {
	}

	private static final Image NONE = new Image(1, 1, new int[1]);
	private static final Map<String, Image> cache = new HashMap<String, Image>();
	private static final Map<Image, Map<Long, Image>> sized = new IdentityHashMap<Image, Map<Long, Image>>();
	private static int sizedCount;

	private static int seenPacks = -1;

	/** Forgets everything decoded so far when the player switched resource packs. */
	private static void sync() {
		int g = ResourcePacks.generation();
		if (g != seenPacks) {
			seenPacks = g;
			cache.clear();
			items.clear();
			sized.clear();
			sizedCount = 0;
			models.clear();
		}
	}

	/** A texture by "name" (minecraft) or "namespace:name", from the enabled resource packs first. */
	public static synchronized Image texture(String name) {
		sync();
		Image img = cache.get(name);
		if (img == null) {
			int colon = name.indexOf(':');
			img = load("assets/" + (colon > 0 ? name.substring(0, colon) : "minecraft") + "/textures/" + (colon > 0 ? name.substring(colon + 1) : name) + ".png");
			if (img != null && img.height > img.width && img.width > 0 && img.height % img.width == 0) {
				int w = img.width;
				int[] px = new int[w * w];
				System.arraycopy(img.argb, 0, px, 0, px.length);
				img = new Image(w, w, px);
			}
			cache.put(name, img == null ? NONE : img);
		}
		return img == NONE || img == null ? null : img;
	}

	private static Image load(String path) {
		byte[] b = bytes(path);
		return b == null ? null : Image.decode(b);
	}

	/** A file from the enabled resource packs, else from the game's own assets on the class path. */
	private static byte[] bytes(String path) {
		byte[] packed = ResourcePacks.read(path);
		if (packed != null) {
			return packed;
		}
		ClassLoader[] loaders = {Vanilla.class.getClassLoader(), Thread.currentThread().getContextClassLoader(), ClassLoader.getSystemClassLoader()};
		for (ClassLoader cl : loaders) {
			if (cl == null) {
				continue;
			}
			try (InputStream in = cl.getResourceAsStream(path)) {
				if (in == null) {
					continue;
				}
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buf = new byte[8192];
				int n;
				while ((n = in.read(buf)) > 0) {
					out.write(buf, 0, n);
				}
				return out.toByteArray();
			} catch (Throwable ignored) {
				// try the next loader
			}
		}
		return null;
	}

	/** The default Steve skin from the running game (64 x 64, or 64 x 32 on old packs); null when it can't be read. */
	public static Image steve() {
		Image img = texture("entity/player/wide/steve"); // 1.19.3+
		return img != null ? img : texture("entity/steve");
	}

	// ── effects ──────────────────────────────────────────────────────────

	/** The game's icon for a potion effect ("speed", "instant_health", ...). */
	public static Image effect(String id) {
		return id == null || id.indexOf(':') >= 0 ? null : texture("mob_effect/" + id);
	}

	// ── items ────────────────────────────────────────────────────────────

	private static final Map<String, Image> items = new HashMap<String, Image>();

	/**
	 * The icon of an item id: its flat item texture, or for blocks a small isometric cube built from the block's own
	 * textures (the way the inventory shows them). Null when the game has neither.
	 */
	public static synchronized Image item(String id) {
		if (id == null) {
			return null;
		}
		sync();
		Image img = items.get(id);
		if (img == null) {
			try {
				img = fromModel(id, Float.NaN);
			} catch (Throwable ignored) {
				img = null; // an odd pack model: the plain lookups below still work
			}
			if (img == null) {
				img = texture("item/" + id);
			}
			if (img == null) {
				img = cube(id);
			}
			if (img == null && id.endsWith("_spawn_egg")) {
				img = texture("item/template_spawn_egg") == null ? null : texture("item/template_spawn_egg");
			}
			items.put(id, img == null ? NONE : img);
		}
		return img == NONE ? null : img;
	}

	/**
	 * The icon of an item in a slot, honouring server / pack custom items: the item_model component and custom model
	 * data (1.21.4+ item definitions and the older model "overrides"). Falls back to the plain item icon.
	 */
	public static synchronized Image item(Game.Slot sl) {
		if (sl == null || sl.id == null) {
			return null;
		}
		if (sl.model == null && Float.isNaN(sl.cmd)) {
			return item(sl.id);
		}
		sync();
		String key = sl.id + "|" + sl.model + "|" + sl.cmd;
		Image img = items.get(key);
		if (img == null) {
			try {
				img = fromModel(sl.model != null ? sl.model : sl.id, sl.cmd);
			} catch (Throwable ignored) {
				img = null;
			}
			if (img == null) {
				img = item(sl.id);
			}
			items.put(key, img == null ? NONE : img);
		}
		return img == NONE ? null : img;
	}

	// ── item models: what a resource pack's model files say the icon is ──────────────────

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	/** The model a 1.21.4+ item definition picks for this custom model data (NaN: the default branch). */
	private static String pick(JsonElement e, float cmd) {
		if (e == null || !e.isJsonObject()) {
			return null;
		}
		JsonObject o = e.getAsJsonObject();
		String type = str(o, "type");
		type = type == null ? "" : type.startsWith("minecraft:") ? type.substring(10) : type;
		if (type.equals("model")) {
			return str(o, "model");
		}
		if (type.equals("range_dispatch")) {
			String prop = str(o, "property");
			int index = o.has("index") ? o.get("index").getAsInt() : 0;
			if (prop != null && prop.endsWith("custom_model_data") && index == 0 && !Float.isNaN(cmd) && o.has("entries") && o.get("entries").isJsonArray()) {
				float v = cmd * (o.has("scale") ? o.get("scale").getAsFloat() : 1f), bestT = Float.NEGATIVE_INFINITY;
				JsonElement best = null;
				for (JsonElement en : o.getAsJsonArray("entries")) {
					if (!en.isJsonObject() || !en.getAsJsonObject().has("threshold")) {
						continue;
					}
					float t = en.getAsJsonObject().get("threshold").getAsFloat();
					if (t <= v && t >= bestT) {
						bestT = t;
						best = en.getAsJsonObject().get("model");
					}
				}
				String r = pick(best, cmd);
				if (r != null) {
					return r;
				}
			}
			String r = pick(o.get("fallback"), cmd);
			return r != null ? r : modelRef(o);
		}
		if (type.equals("condition")) {
			String r = pick(o.get("on_false"), cmd);
			return r != null ? r : pick(o.get("on_true"), cmd);
		}
		if (type.equals("select")) {
			String r = pick(o.get("fallback"), cmd);
			if (r == null && o.has("cases") && o.get("cases").isJsonArray() && o.getAsJsonArray("cases").size() > 0) {
				JsonElement c0 = o.getAsJsonArray("cases").get(0);
				r = c0.isJsonObject() ? pick(c0.getAsJsonObject().get("model"), cmd) : null;
			}
			return r;
		}
		if (type.equals("composite") && o.has("models") && o.get("models").isJsonArray() && o.getAsJsonArray("models").size() > 0) {
			return pick(o.getAsJsonArray("models").get(0), cmd);
		}
		if (type.equals("special")) {
			return str(o, "base");
		}
		return modelRef(o);
	}

	/** Pre-1.21.4 packs: the last model "overrides" entry whose custom_model_data is at most the item's. */
	private static String override(JsonObject m, float cmd) {
		if (m == null || Float.isNaN(cmd) || !m.has("overrides") || !m.get("overrides").isJsonArray()) {
			return null;
		}
		String found = null;
		for (JsonElement e : m.getAsJsonArray("overrides")) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject o = e.getAsJsonObject();
			JsonElement pred = o.get("predicate");
			if (pred == null || !pred.isJsonObject() || !pred.getAsJsonObject().has("custom_model_data")) {
				continue;
			}
			if (pred.getAsJsonObject().get("custom_model_data").getAsFloat() <= cmd && str(o, "model") != null) {
				found = str(o, "model");
			}
		}
		return found;
	}

	private static final Map<String, JsonObject> models = new HashMap<String, JsonObject>();
	private static final JsonObject NO_MODEL = new JsonObject();

	private static JsonObject model(String ref) {
		JsonObject o = models.get(ref);
		if (o == null) {
			int colon = ref.indexOf(':');
			String path = "assets/" + (colon > 0 ? ref.substring(0, colon) : "minecraft") + "/models/" + (colon > 0 ? ref.substring(colon + 1) : ref) + ".json";
			o = json(path);
			models.put(ref, o == null ? NO_MODEL : o);
		}
		return o == NO_MODEL ? null : o;
	}

	private static JsonObject json(String path) {
		try {
			byte[] b = bytes(path);
			if (b == null) {
				return null;
			}
			JsonElement e = new JsonParser().parse(new String(b, java.nio.charset.StandardCharsets.UTF_8));
			return e.isJsonObject() ? e.getAsJsonObject() : null;
		} catch (Throwable t) {
			return null;
		}
	}

	/** The first "model": "namespace:name" string anywhere in an item definition (1.21.4+ assets/minecraft/items/*.json). */
	private static String modelRef(JsonElement e) {
		if (e == null) {
			return null;
		}
		if (e.isJsonObject()) {
			for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
				JsonElement v = en.getValue();
				if (en.getKey().equals("model") && v.isJsonPrimitive()) {
					return v.getAsString();
				}
				String r = modelRef(v);
				if (r != null) {
					return r;
				}
			}
		} else if (e.isJsonArray()) {
			for (JsonElement v : e.getAsJsonArray()) {
				String r = modelRef(v);
				if (r != null) {
					return r;
				}
			}
		}
		return null;
	}

	private static String texOf(Map<String, String> tex, String... keys) {
		for (String k : keys) {
			String v = tex.get(k);
			for (int i = 0; v != null && v.startsWith("#") && i < 6; i++) {
				v = tex.get(v.substring(1));
			}
			if (v != null && !v.startsWith("#")) {
				return v;
			}
		}
		return null;
	}

	/** The icon an item's (pack or vanilla) model describes: its flat layer, or a cube of its block textures. Null when unclear. */
	private static Image fromModel(String fullId, float cmd) {
		int colon = fullId.indexOf(':');
		String ns = colon > 0 ? fullId.substring(0, colon) : "minecraft", id = colon > 0 ? fullId.substring(colon + 1) : fullId;
		String ref = null;
		JsonObject def = json("assets/" + ns + "/items/" + id + ".json");
		if (def != null) {
			ref = pick(def.get("model"), cmd);
			if (ref == null) {
				ref = modelRef(def);
			}
		}
		if (ref == null) {
			ref = ns + ":item/" + id;
		}
		String over = override(model(ref), cmd);
		if (over != null) {
			ref = over;
		}
		Map<String, String> tex = new HashMap<String, String>();
		boolean flat = false, entity = false, found = false;
		String cur = ref;
		for (int depth = 0; cur != null && depth < 10; depth++) {
			String bare = cur.startsWith("minecraft:") ? cur.substring(10) : cur;
			if (bare.equals("item/generated") || bare.equals("builtin/generated") || bare.equals("item/handheld") || bare.equals("item/handheld_rod")
					|| bare.equals("item/handheld_mace")) {
				flat = true;
			}
			if (bare.equals("builtin/entity")) {
				entity = true;
			}
			JsonObject o = model(cur);
			if (o == null) {
				break;
			}
			found = true;
			if (o.has("textures") && o.get("textures").isJsonObject()) {
				for (Map.Entry<String, JsonElement> en : o.getAsJsonObject("textures").entrySet()) {
					if (en.getValue().isJsonPrimitive() && !tex.containsKey(en.getKey())) {
						tex.put(en.getKey(), en.getValue().getAsString());
					}
				}
			}
			cur = o.has("parent") && o.get("parent").isJsonPrimitive() ? o.get("parent").getAsString() : null;
		}
		if (!found) {
			return null;
		}
		if (id.equals("shield")) {
			return shield();
		}
		String layer = texOf(tex, "layer0");
		if (layer != null && !entity) {
			return texture(layer);
		}
		if (flat) {
			return null;
		}
		String t = texOf(tex, "up", "top", "end", "all", "particle", "texture"), s = texOf(tex, "side", "north", "front", "all", "particle", "texture");
		Image top = t == null ? null : texture(t), side = s == null ? null : texture(s);
		if (top == null && side == null) {
			return null;
		}
		return cube(id, top, side);
	}

	/** The shield's front, cut out of its entity texture (the game draws it as a model, so it has no flat item texture). */
	private static Image shield() {
		Image sheet = texture("entity/shield_base_nopattern");
		if (sheet == null) {
			sheet = texture("entity/shield/shield_base_nopattern");
		}
		if (sheet == null) {
			return null;
		}
		float k = sheet.width / 64f;
		int cx = Math.round(k), cy = Math.round(k), cw = Math.round(12 * k), ch = Math.round(22 * k);
		if (cx + cw > sheet.width || cy + ch > sheet.height) {
			return null;
		}
		int side = ch; // square canvas so the icon keeps its proportions
		int[] out = new int[side * side];
		int ox = (side - cw) / 2;
		for (int y = 0; y < ch; y++) {
			System.arraycopy(sheet.argb, (cy + y) * sheet.width + cx, out, y * side + ox, cw);
		}
		Image img = new Image(side, side, out);
		img.linear = false;
		return img;
	}

	private static Image firstOf(String... names) {
		for (String n : names) {
			Image i = texture("block/" + n);
			if (i != null) {
				return i;
			}
		}
		return null;
	}

	private static int tintFor(String id, boolean top) {
		if (id.contains("leaves") && !id.contains("azalea") && !id.contains("cherry") && !id.contains("pale_oak")) {
			return 0xFF48B518;
		}
		if (id.equals("grass_block") && top || id.equals("short_grass") || id.equals("tall_grass") || id.equals("fern") || id.equals("large_fern")) {
			return 0xFF79C05A;
		}
		return 0;
	}

	/** An isometric cube of a block, 32 x 32, or null when the block has no textures. */
	private static Image cube(String id) {
		Image top = firstOf(id + "_top", id + "_top_open", id + "_end", id);
		Image side = firstOf(id + "_side", id + "_front", id + "_side1", id);
		if (side == null) {
			side = firstOf(id + "_side", id + "_top", id + "_end");
		}
		if (top == null && side == null) {
			Image flat = firstOf(id);
			return flat;
		}
		return cube(id, top, side);
	}

	/** An isometric cube from a top and a side texture (either may be null). */
	private static Image cube(String id, Image top, Image side) {
		if (top == null) {
			top = side;
		}
		if (side == null) {
			side = top;
		}
		if (top == null) {
			return null;
		}
		int tt = tintFor(id, true), st = tintFor(id, false);
		final int n = 32;
		int[] out = new int[n * n];
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				float px = x + 0.5f, py = y + 0.5f;
				// top face: A(16,1) B(30,8) C(16,15) D(2,8)
				float s = ((px - 16) / 14f + (py - 1) / 7f) / 2f, t = ((py - 1) / 7f - (px - 16) / 14f) / 2f;
				int c = 0;
				if (s >= 0 && s < 1 && t >= 0 && t < 1) {
					c = shade(sample(top, s, t), tt, 1f);
				} else {
					float u = (px - 2) / 14f, v = (py - 8 - 7 * u) / 15f;
					if (u >= 0 && u < 1 && v >= 0 && v < 1) {
						c = shade(sample(side, u, v), st, 0.82f);
					} else {
						u = (px - 16) / 14f;
						v = (py - 15 + 7 * u) / 15f;
						if (u >= 0 && u < 1 && v >= 0 && v < 1) {
							c = shade(sample(side, u, v), st, 0.62f);
						}
					}
				}
				out[y * n + x] = c;
			}
		}
		return new Image(n, n, out);
	}

	private static int sample(Image img, float u, float v) {
		int x = Math.min(img.width - 1, Math.max(0, (int) (u * img.width)));
		int y = Math.min(img.height - 1, Math.max(0, (int) (v * img.width))); // textures are square (strips were cropped)
		if (y >= img.height) {
			y = img.height - 1;
		}
		return img.argb[y * img.width + x];
	}

	private static int shade(int argb, int tint, float k) {
		int a = argb >>> 24;
		if (a == 0) {
			return 0;
		}
		int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		if (tint != 0) {
			r = r * ((tint >> 16) & 0xFF) / 255;
			g = g * ((tint >> 8) & 0xFF) / 255;
			b = b * (tint & 0xFF) / 255;
		}
		return (a << 24) | ((int) (r * k) << 16) | ((int) (g * k) << 8) | (int) (b * k);
	}

	// ── heart / food / armour pips ───────────────────────────────────────

	/** Names the pips can be asked for; each is {gui sprite (1.20.2+), x, y in gui/icons.png (older versions)}. */
	private static final Map<String, Object[]> HUD = new HashMap<String, Object[]>();

	static {
		pip("heart_container", "heart/container", 16, 0);
		pip("heart_full", "heart/full", 52, 0);
		pip("heart_half", "heart/half", 61, 0);
		pip("heart_absorb_full", "heart/absorbing_full", 160, 0);
		pip("heart_absorb_half", "heart/absorbing_half", 169, 0);
		pip("heart_poison_full", "heart/poisoned_full", 88, 0);
		pip("heart_poison_half", "heart/poisoned_half", 97, 0);
		pip("heart_wither_full", "heart/withered_full", 124, 0);
		pip("heart_wither_half", "heart/withered_half", 133, 0);
		pip("food_empty", "food_empty", 16, 27);
		pip("food_full", "food_full", 52, 27);
		pip("food_half", "food_half", 61, 27);
		pip("food_hunger_full", "food_full_hunger", 88, 27);
		pip("food_hunger_half", "food_half_hunger", 97, 27);
		pip("armor_empty", "armor_empty", 16, 9);
		pip("armor_half", "armor_half", 25, 9);
		pip("armor_full", "armor_full", 34, 9);
	}

	private static void pip(String name, String sprite, int x, int y) {
		HUD.put(name, new Object[] {sprite, x, y});
	}

	/** One of the 9 x 9 HUD pips ("heart_full", "food_half", "armor_full", ...), from the game's own assets. */
	public static synchronized Image pip(String name) {
		Image img = cache.get("pip:" + name);
		if (img == null) {
			Object[] d = HUD.get(name);
			if (d != null) {
				img = texture("gui/sprites/hud/" + d[0]);
				if (img == null) {
					Image sheet = texture("gui/icons");
					if (sheet != null) {
						img = crop(sheet, (Integer) d[1], (Integer) d[2], 9, 9);
					}
				}
			}
			cache.put("pip:" + name, img == null ? NONE : img);
		}
		return img == NONE ? null : img;
	}

	private static Image crop(Image src, int x, int y, int w, int h) {
		// gui/icons.png is 256 x 256; scale the coordinates when a pack ships a bigger sheet
		float k = src.width / 256f;
		int X = Math.round(x * k), Y = Math.round(y * k), W = Math.round(w * k), H = Math.round(h * k);
		if (X + W > src.width || Y + H > src.height) {
			return null;
		}
		int[] out = new int[W * H];
		for (int j = 0; j < H; j++) {
			System.arraycopy(src.argb, (Y + j) * src.width + X, out, j * W, W);
		}
		return new Image(W, H, out);
	}

	// ── drawing ──────────────────────────────────────────────────────────

	/**
	 * Draws a texture in a box at exactly its on-screen pixel size, nearest-neighbour when it grows (pixel art stays crisp)
	 * and area-averaged when it shrinks.
	 */
	public static void draw(Canvas c, Image img, float x, float y, float w, float h, int argb) {
		if (img == null) {
			return;
		}
		int pw = c.px(x + w) - c.px(x), ph = c.px(y + h) - c.px(y);
		if (pw <= 0 || ph <= 0) {
			return;
		}
		Image s = fit(img, pw, ph);
		c.stamp(s, s, x, y, w, h, false, argb);
	}

	private static Image fit(Image src, int pw, int ph) {
		if (src.width == pw && src.height == ph) {
			return src;
		}
		long key = ((long) pw << 20) | ph;
		Map<Long, Image> m = sized.get(src);
		if (m == null) {
			if (sizedCount > 1500) {
				sized.clear();
				sizedCount = 0;
			}
			m = new HashMap<Long, Image>();
			sized.put(src, m);
		}
		Image s = m.get(key);
		if (s == null) {
			if (pw < src.width || ph < src.height) {
				s = src.scaled(pw, ph);
			} else {
				int[] out = new int[pw * ph];
				for (int y = 0; y < ph; y++) {
					int sy = Math.min(src.height - 1, y * src.height / ph);
					for (int x = 0; x < pw; x++) {
						out[y * pw + x] = src.argb[sy * src.width + Math.min(src.width - 1, x * src.width / pw)];
					}
				}
				s = new Image(pw, ph, out);
			}
			m.put(key, s);
			sizedCount++;
		}
		return s;
	}
}
