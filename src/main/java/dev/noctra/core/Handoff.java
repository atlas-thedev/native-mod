package dev.noctra.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What the Noctra launcher leaves for the game: a short-lived, game-only ticket
 * and the API address. Lives in {@code <game dir>/.noctra/session.json}. The
 * mod works fine without it (guest mode: skins and capes still load).
 */
public final class Handoff {
	public final String ticket;
	public final String api;
	public final String accountName;

	Handoff(String ticket, String api, String accountName) {
		this.ticket = ticket;
		this.api = api;
		this.accountName = accountName;
	}

	/** @return the parsed hand-off, or null when there is none / it is unreadable. */
	public static Handoff read(Path gameDir) {
		if (gameDir == null) {
			return null;
		}
		Path file = gameDir.resolve(".noctra").resolve("session.json");
		try {
			if (!Files.isRegularFile(file) || Files.size(file) > 16 * 1024) {
				return null;
			}
			return parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	static Handoff parse(String json) {
		JsonElement element = new JsonParser().parse(json);
		if (!element.isJsonObject()) {
			return null;
		}
		JsonObject root = element.getAsJsonObject();
		String ticket = text(root, "ticket");
		if (ticket == null || !ticket.startsWith("nmt1.")) {
			return null;
		}
		String name = null;
		if (root.has("account") && root.get("account").isJsonObject()) {
			name = text(root.getAsJsonObject("account"), "name");
		}
		return new Handoff(ticket, text(root, "api"), name);
	}

	private static String text(JsonObject object, String key) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
	}
}
