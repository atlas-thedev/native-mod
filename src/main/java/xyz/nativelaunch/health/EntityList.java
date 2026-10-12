package xyz.nativelaunch.health;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The entity blacklist / whitelist, kept in {@code config/native-health.json}:
 * {@code {"mode": "blacklist", "entities": ["minecraft:armor_stand"]}}. Entries are entity ids ("minecraft:zombie",
 * "minecraft:player" for every player) or a player's name. Edit the file, or toggle the mob under the crosshair with
 * the "Hide / show this mob type" key.
 */
public final class EntityList {
	private boolean blacklist = true;
	private final Set<String> entries = new LinkedHashSet<String>();
	private Path file;

	public EntityList() {
		entries.add("minecraft:armor_stand");
	}

	public static EntityList load(Path file) {
		EntityList list = new EntityList();
		list.file = file;
		if (file == null || !Files.isRegularFile(file)) {
			return list;
		}
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject o = new JsonParser().parse(r).getAsJsonObject();
			list.entries.clear();
			if (o.has("mode")) {
				list.blacklist = !"whitelist".equalsIgnoreCase(o.get("mode").getAsString());
			}
			if (o.has("entities") && o.get("entities").isJsonArray()) {
				for (JsonElement e : o.getAsJsonArray("entities")) {
					list.entries.add(norm(e.getAsString()));
				}
			}
		} catch (Exception ignored) {
			// unreadable file: keep the defaults
		}
		return list;
	}

	public void save() {
		if (file == null) {
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("mode", blacklist ? "blacklist" : "whitelist");
		JsonArray a = new JsonArray();
		for (String s : entries) {
			a.add(new com.google.gson.JsonPrimitive(s));
		}
		o.add("entities", a);
		try {
			Files.createDirectories(file.getParent());
			try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				new GsonBuilder().setPrettyPrinting().create().toJson(o, w);
			}
		} catch (IOException ignored) {
			// read-only config dir: the list still works for this session
		}
	}

	private static String norm(String s) {
		return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
	}

	public boolean isBlacklist() {
		return blacklist;
	}

	public void setBlacklist(boolean on) {
		blacklist = on;
	}

	public Set<String> entries() {
		return entries;
	}

	/** True when the entity may get an indicator. {@code typeId} like "minecraft:zombie"; {@code playerName} null for non-players. */
	public boolean allows(String typeId, String playerName) {
		boolean match = matches(typeId, playerName);
		if (blacklist) {
			return !match;
		}
		return entries.isEmpty() || match;
	}

	private boolean matches(String typeId, String playerName) {
		if (typeId != null && entries.contains(norm(typeId))) {
			return true;
		}
		return playerName != null && entries.contains(norm(playerName));
	}

	/** Adds the entry, or removes it when it is already there. Returns true when it is in the list afterwards. */
	public boolean toggle(String entry) {
		String e = norm(entry);
		if (e.isEmpty()) {
			return false;
		}
		boolean now = !entries.remove(e);
		if (now) {
			entries.add(e);
		}
		save();
		return now;
	}
}
