package xyz.nativelaunch.ui.mod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The resource packs the player turned on (options.txt, folders and zips in resourcepacks/), so the HUD's item and
 * heart icons show the same textures as the game does. Highest priority first; reads are cheap and the list is
 * re-checked every couple of seconds, bumping {@link #generation()} when the selection or a pack file changed.
 */
final class ResourcePacks {
	private ResourcePacks() {
	}

	private static final class Pack {
		final File file;
		ZipFile zip;
		boolean broken;

		Pack(File file) {
			this.file = file;
		}

		byte[] read(String path) {
			try {
				if (file.isDirectory()) {
					File f = new File(file, path);
					return f.isFile() ? Files.readAllBytes(f.toPath()) : null;
				}
				if (zip == null && !broken) {
					zip = new ZipFile(file);
				}
				if (zip == null) {
					return null;
				}
				ZipEntry e = zip.getEntry(path);
				if (e == null || e.isDirectory()) {
					return null;
				}
				try (InputStream in = zip.getInputStream(e)) {
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					byte[] buf = new byte[8192];
					int n;
					while ((n = in.read(buf)) > 0) {
						out.write(buf, 0, n);
					}
					return out.toByteArray();
				}
			} catch (Throwable t) {
				broken = true;
				return null;
			}
		}

		void close() {
			try {
				if (zip != null) {
					zip.close();
				}
			} catch (Throwable ignored) {
				// nothing to release
			}
		}
	}

	private static List<Pack> packs = Collections.emptyList(); // highest priority first
	private static String signature = "";
	private static long checkedAt;
	private static int generation;

	/** Changes whenever the enabled packs (or their files) changed. */
	static synchronized int generation() {
		long now = System.currentTimeMillis();
		if (now - checkedAt > 2000) {
			checkedAt = now;
			refresh();
		}
		return generation;
	}

	private static void refresh() {
		try {
			java.nio.file.Path dir = xyz.nativelaunch.core.NativeState.get().gameDir();
			if (dir == null) {
				return;
			}
			List<String> names = new ArrayList<String>();
			File opt = dir.resolve("options.txt").toFile();
			if (opt.isFile()) {
				for (String line : Files.readAllLines(opt.toPath(), StandardCharsets.UTF_8)) {
					if (line.startsWith("resourcePacks:")) {
						JsonArray arr = new JsonParser().parse(line.substring("resourcePacks:".length())).getAsJsonArray();
						for (JsonElement e : arr) {
							names.add(e.getAsString());
						}
					}
				}
			}
			StringBuilder sig = new StringBuilder();
			List<File> files = new ArrayList<File>();
			for (int i = names.size() - 1; i >= 0; i--) { // options.txt lists the lowest priority first
				String n = names.get(i);
				if (!n.startsWith("file/")) {
					continue;
				}
				File f = dir.resolve("resourcepacks").resolve(n.substring(5)).toFile();
				if (f.exists()) {
					files.add(f);
					sig.append(f.getPath()).append('|').append(f.lastModified()).append('|').append(f.length()).append(';');
				}
			}
			if (sig.toString().equals(signature)) {
				return;
			}
			signature = sig.toString();
			for (Pack p : packs) {
				p.close();
			}
			List<Pack> next = new ArrayList<Pack>();
			for (File f : files) {
				next.add(new Pack(f));
			}
			packs = next;
			generation++;
		} catch (Throwable ignored) {
			// unreadable options: keep what we had
		}
	}

	/** A file from the highest-priority enabled pack that has it (e.g. "assets/minecraft/textures/item/apple.png"), else null. */
	static synchronized byte[] read(String path) {
		generation();
		for (Pack p : packs) {
			byte[] b = p.read(path);
			if (b != null) {
				return b;
			}
		}
		return null;
	}
}
