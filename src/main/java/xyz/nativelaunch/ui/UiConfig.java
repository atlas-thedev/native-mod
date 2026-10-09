package xyz.nativelaunch.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import xyz.nativelaunch.core.Log;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/native-ui.json */
public final class UiConfig {
	public boolean customTitle = true;
	public boolean notifications = true;
	/** GLFW key that opens the chat in game (default Y). */
	public int relayKey = 89;
	public float scale = 1f;

	private transient Path file;

	public static UiConfig load(Path gameDir) {
		Path file = gameDir.resolve("config").resolve("native-ui.json");
		UiConfig cfg = null;
		try {
			if (Files.isRegularFile(file)) {
				try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					cfg = new Gson().fromJson(r, UiConfig.class);
				}
			}
		} catch (Throwable t) {
			Log.warn("Could not read {} ({}), using defaults.", file, t.toString());
		}
		if (cfg == null) {
			cfg = new UiConfig();
		}
		if (!(cfg.scale >= 0.5f && cfg.scale <= 2.5f)) {
			cfg.scale = 1f;
		}
		cfg.file = file;
		return cfg;
	}

	public void save() {
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				new GsonBuilder().setPrettyPrinting().create().toJson(this, w);
			}
		} catch (Throwable t) {
			Log.warn("Could not save {} ({}).", file, t.toString());
		}
	}
}
