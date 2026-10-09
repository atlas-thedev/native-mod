package xyz.nativelaunch.ui.mod;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import xyz.nativelaunch.core.Log;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Every client module, plus config/native-modules.json ({id: {enabled, settings{...}, x, y}}). */
public final class Modules {
	private static final List<Module> all = new ArrayList<Module>();
	private static final List<HudModule> hud = new ArrayList<HudModule>();
	private static Path file;
	private static volatile boolean dirty;
	private static long dirtyAt;
	private static boolean loaded;

	private Modules() {
	}

	public static synchronized void init(Path gameDir) {
		if (loaded) {
			return;
		}
		loaded = true;
		HudModules.register();
		file = gameDir == null ? null : gameDir.resolve("config").resolve("native-modules.json");
		load();
		for (Module m : all) {
			if (m.enabled) {
				try {
					m.onEnable();
				} catch (Throwable t) {
					Log.warn("Module {} failed to start: {}", m.id, t.toString());
				}
			}
		}
	}

	static void register(Module m) {
		all.add(m);
		if (m instanceof HudModule) {
			hud.add((HudModule) m);
		}
	}

	public static List<Module> all() {
		return Collections.unmodifiableList(all);
	}

	public static List<HudModule> hud() {
		return hud;
	}

	public static Module get(String id) {
		for (Module m : all) {
			if (m.id.equals(id)) {
				return m;
			}
		}
		return null;
	}

	/** Something changed: written to disk shortly after (throttled, see {@link #tick()}). */
	public static void changed() {
		if (!dirty) {
			dirtyAt = System.currentTimeMillis();
		}
		dirty = true;
	}

	/** Once per frame: saves pending changes after a short quiet period. */
	public static void tick() {
		if (dirty && System.currentTimeMillis() - dirtyAt > 800) {
			save();
		}
	}

	public static void resetPositions() {
		for (HudModule m : hud) {
			m.resetPosition();
		}
		changed();
	}

	private static void load() {
		if (file == null || !Files.isRegularFile(file)) {
			return;
		}
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = new JsonParser().parse(r).getAsJsonObject();
			for (Module m : all) {
				JsonElement e = root.get(m.id);
				if (e == null || !e.isJsonObject()) {
					continue;
				}
				JsonObject o = e.getAsJsonObject();
				try {
					if (o.has("enabled")) {
						m.enabled = o.get("enabled").getAsBoolean();
					}
					if (o.has("favorite")) {
						m.favorite = o.get("favorite").getAsBoolean();
					}
					if (m instanceof HudModule) {
						HudModule h = (HudModule) m;
						if (o.has("x")) {
							h.fx = clamp01(o.get("x").getAsFloat());
						}
						if (o.has("y")) {
							h.fy = clamp01(o.get("y").getAsFloat());
						}
					}
					JsonObject s = o.has("settings") && o.get("settings").isJsonObject() ? o.getAsJsonObject("settings") : null;
					if (s != null) {
						for (Setting st : m.allSettings()) {
							JsonElement v = s.get(st.id);
							if (v != null) {
								try {
									st.load(v);
								} catch (Throwable ignored) {
									st.reset();
								}
							}
						}
					}
				} catch (Throwable t) {
					Log.warn("Bad settings for module {}: {}", m.id, t.toString());
				}
			}
		} catch (Throwable t) {
			Log.warn("Could not read {} ({}), using defaults.", file, t.toString());
		}
	}

	public static synchronized void save() {
		dirty = false;
		if (file == null) {
			return;
		}
		JsonObject root = new JsonObject();
		for (Module m : all) {
			JsonObject o = new JsonObject();
			o.addProperty("enabled", m.enabled);
			if (m.favorite) {
				o.addProperty("favorite", true);
			}
			if (m instanceof HudModule) {
				o.addProperty("x", ((HudModule) m).fx);
				o.addProperty("y", ((HudModule) m).fy);
			}
			JsonObject s = new JsonObject();
			for (Setting st : m.allSettings()) {
				s.add(st.id, st.save());
			}
			o.add("settings", s);
			root.add(m.id, o);
		}
		try {
			Files.createDirectories(file.getParent());
			try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
			}
		} catch (Throwable t) {
			Log.warn("Could not save {} ({}).", file, t.toString());
		}
	}

	static float clamp01(float v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}
}
