package xyz.nativelaunch.ui.mod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import xyz.nativelaunch.core.Http;
import xyz.nativelaunch.core.Log;
import xyz.nativelaunch.core.NativeState;
import xyz.nativelaunch.relay.RelayClient;
import xyz.nativelaunch.ui.gfx.Image;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The player's Native locker inside the game: the store catalogue, what they own and wear, and equipping.
 * Every request runs on a background thread; the menu only reads the volatile snapshot.
 */
public final class Wardrobe {
	public static final String[] SLOTS = {"capes", "hats", "glasses", "back", "shoes", "hand"};
	public static final String[] SLOT_NAMES = {"Capes", "Hats", "Glasses", "Wings & Backpacks", "Shoes", "Hand Items"};

	public static final class Item {
		public final String id, name, kind, slot, stillUrl, modelUrl, textureUrl, dyeUrl;

		Item(String id, String name, String kind, String slot, String stillUrl, String modelUrl, String textureUrl, String dyeUrl) {
			this.id = id;
			this.name = name;
			this.kind = kind;
			this.slot = slot;
			this.stillUrl = stillUrl;
			this.modelUrl = modelUrl;
			this.textureUrl = textureUrl;
			this.dyeUrl = dyeUrl;
		}

		public boolean isCape() {
			return !"cosmetic".equals(kind);
		}

		/** Slot key: "capes" for capes. */
		public String group() {
			return isCape() ? "capes" : slot == null ? "hats" : slot;
		}

		public String modelHash() {
			return hashOf(modelUrl);
		}

		public String textureHash() {
			return hashOf(textureUrl);
		}
	}

	public enum State { IDLE, LOADING, READY, ERROR, NO_ACCOUNT }

	public static volatile State state = State.IDLE;
	public static volatile String error;
	public static volatile List<Item> catalog = Collections.emptyList();
	public static volatile Set<String> owned = Collections.emptySet();
	/** Store cape worn (null = none). */
	public static volatile String equipped;
	public static volatile Map<String, String> wearing = Collections.emptyMap();
	public static volatile Map<String, Integer> sides = Collections.emptyMap();
	public static volatile Map<String, String> dyes = Collections.emptyMap();
	/** Item (or slot) id with a request in flight. */
	public static volatile String busy;
	private static volatile long loadedAt;
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Native-Wardrobe");
		t.setDaemon(true);
		return t;
	});

	private Wardrobe() {
	}

	static String hashOf(String url) {
		if (url == null) {
			return null;
		}
		int q = url.indexOf('?');
		String u = q >= 0 ? url.substring(0, q) : url;
		String h = u.substring(u.lastIndexOf('/') + 1);
		return h.matches("[a-f0-9]{64}") ? h : null;
	}

	public static String api() {
		RelayClient r = RelayClient.get();
		if (r != null && r.api() != null) {
			return r.api();
		}
		return NativeState.get().api();
	}

	static String ticket() {
		RelayClient r = RelayClient.get();
		return r == null ? null : r.ticket();
	}

	/** Loads (or reloads when older than 30 s) the catalogue and the locker. */
	public static void refresh(boolean force) {
		if (state == State.LOADING || !force && state == State.READY && System.currentTimeMillis() - loadedAt < 30_000) {
			return;
		}
		if (state != State.READY) {
			state = State.LOADING;
		}
		IO.execute(() -> {
			try {
				String api = api();
				JsonObject cat = parse(Http.getJson(api + "/v1/store/catalog", null));
				List<Item> items = new ArrayList<Item>();
				for (JsonElement e : cat.getAsJsonArray("items")) {
					JsonObject o = e.getAsJsonObject();
					items.add(new Item(str(o, "id"), str(o, "name"), str(o, "kind"), str(o, "slot"), str(o, "stillUrl"), str(o, "modelUrl"),
							str(o, "textureUrl"), str(o, "dyeUrl")));
				}
				catalog = Collections.unmodifiableList(items);
				String ticket = ticket();
				if (ticket == null) {
					state = State.NO_ACCOUNT;
					return;
				}
				apply(parse(Http.getJson(api + "/v1/store/me", ticket)));
				loadedAt = System.currentTimeMillis();
				error = null;
				state = State.READY;
			} catch (Throwable t) {
				Log.warn("Wardrobe unavailable: {}", t.toString());
				error = "Could not load your locker.";
				if (state != State.READY) {
					state = State.ERROR;
				}
			}
		});
	}

	private static void apply(JsonObject me) {
		if (me.has("owned") && me.get("owned").isJsonArray()) {
			Set<String> set = new HashSet<String>();
			for (JsonElement e : me.getAsJsonArray("owned")) {
				if (e.isJsonObject() && e.getAsJsonObject().has("id")) {
					set.add(e.getAsJsonObject().get("id").getAsString());
				} else if (e.isJsonPrimitive()) {
					set.add(e.getAsString());
				}
			}
			owned = set;
		}
		equipped = str(me, "equipped");
		wearing = map(me, "wearing");
		Map<String, Integer> s = new HashMap<String, Integer>();
		if (me.has("sides") && me.get("sides").isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : me.getAsJsonObject("sides").entrySet()) {
				try {
					s.put(e.getKey(), e.getValue().getAsInt());
				} catch (Throwable ignored) {
					// "left" / "right"
					String v = e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : "";
					s.put(e.getKey(), v.startsWith("l") ? 1 : v.startsWith("r") ? 2 : 0);
				}
			}
		}
		sides = s;
		dyes = map(me, "dyes");
	}

	private static Map<String, String> map(JsonObject o, String key) {
		Map<String, String> m = new HashMap<String, String>();
		if (o.has(key) && o.get(key).isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(key).entrySet()) {
				if (e.getValue().isJsonPrimitive()) {
					m.put(e.getKey(), e.getValue().getAsString());
				}
			}
		}
		return m;
	}

	public static Item item(String id) {
		if (id == null) {
			return null;
		}
		for (Item i : catalog) {
			if (i.id.equals(id)) {
				return i;
			}
		}
		return null;
	}

	/** Owned items in one slot group, in catalogue order. */
	public static List<Item> ownedIn(String group) {
		List<Item> out = new ArrayList<Item>();
		Set<String> mine = owned;
		for (Item i : catalog) {
			if (mine.contains(i.id) && i.group().equals(group)) {
				out.add(i);
			}
		}
		return out;
	}

	public static boolean isWorn(Item i) {
		return i.isCape() ? i.id.equals(equipped) : i.id.equals(wearing.get(i.group()));
	}

	/** Puts the item on, or takes it off when it is worn. */
	public static void toggle(final Item i) {
		if (busy != null) {
			return;
		}
		final boolean off = isWorn(i);
		JsonObject body = new JsonObject();
		if (off) {
			if (i.isCape()) {
				body.add("itemId", com.google.gson.JsonNull.INSTANCE);
			} else {
				body.addProperty("slot", i.group());
				body.add("itemId", com.google.gson.JsonNull.INSTANCE);
			}
		} else {
			body.addProperty("itemId", i.id);
		}
		// optimistic
		if (i.isCape()) {
			equipped = off ? null : i.id;
		} else {
			Map<String, String> w = new HashMap<String, String>(wearing);
			if (off) {
				w.remove(i.group());
			} else {
				w.put(i.group(), i.id);
			}
			wearing = w;
		}
		post(i.id, body);
	}

	private static void post(final String what, final JsonObject body) {
		busy = what;
		IO.execute(() -> {
			try {
				String res = Http.postBytes(api() + "/v1/store/equip", ticket(), body.toString().getBytes(StandardCharsets.UTF_8), "application/json");
				apply(parse(res));
				error = null;
			} catch (Throwable t) {
				if (t.getMessage() != null && t.getMessage().contains("too large")) {
					loadedAt = 0; // it worked, the reply was just big: read the locker again
					error = null;
					busy = null;
					refresh(true);
					return;
				}
				error = t.getMessage() != null && t.getMessage().contains("403") ? "Add it to your locker in the launcher first."
						: "Could not change your look. Try again.";
				Log.warn("Equip failed: {}", t.toString());
				loadedAt = 0;
				refresh(true);
			} finally {
				busy = null;
			}
		});
	}

	private static JsonObject parse(String s) {
		return new JsonParser().parse(s).getAsJsonObject();
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? null : e.getAsString();
	}

	// ── images (thumbnails, skins, cosmetic textures) ────────────────────

	public static final Image NONE = new Image(1, 1, new int[1]);
	private static final Map<String, Image> images = new ConcurrentHashMap<String, Image>();
	private static final Map<String, Boolean> requested = new ConcurrentHashMap<String, Boolean>();
	private static final ExecutorService IMG = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "Native-Wardrobe-Img");
		t.setDaemon(true);
		return t;
	});

	public interface Source {
		byte[] load() throws Exception;
	}

	/** An image by key: null while loading, {@link #NONE} when it failed. maxSide shrinks big pictures. */
	public static Image image(final String key, final Source source, final boolean pixel, final int maxSide) {
		if (key == null) {
			return NONE;
		}
		Image img = images.get(key);
		if (img != null) {
			return img;
		}
		if (requested.putIfAbsent(key, Boolean.TRUE) == null) {
			IMG.execute(() -> {
				try {
					Image d = Image.decode(source.load());
					if (d == null) {
						images.put(key, NONE);
						return;
					}
					if (maxSide > 0 && Math.max(d.width, d.height) > maxSide && !pixel) {
						float k = (float) maxSide / Math.max(d.width, d.height);
						d = d.scaled(Math.max(1, Math.round(d.width * k)), Math.max(1, Math.round(d.height * k)));
					}
					d.linear = !pixel;
					images.put(key, d);
				} catch (Throwable t) {
					images.put(key, NONE);
				}
			});
		}
		return null;
	}

	public static Image url(final String url, boolean pixel, int maxSide) {
		return url == null ? NONE : image(url, () -> Http.getBytes(url, 4 * 1024 * 1024), pixel, maxSide);
	}

	/** A texture by store hash (shared launcher cache first). */
	public static Image hash(final String base, final String hash) {
		if (hash == null) {
			return NONE;
		}
		return image("h:" + hash, () -> xyz.nativelaunch.core.TextureCache.getOrDownload(base, hash, 4 * 1024 * 1024), true, 0);
	}

	/** Texture of a worn cosmetic, dyed when the player dyed it. */
	public static Image cosmeticTexture(Item i) {
		String dye = dyes.get(i.id);
		if (dye != null && i.dyeUrl != null && dye.matches("#?[0-9a-fA-F]{6}")) {
			return url(i.dyeUrl + dye.replace("#", "").toLowerCase(java.util.Locale.ROOT), true, 0);
		}
		String h = i.textureHash();
		return h != null ? hash(base(), h) : url(i.textureUrl, true, 0);
	}

	public static String base() {
		return api() + "/csl/textures/";
	}
}
