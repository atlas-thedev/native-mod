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
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
		String prefix = "";
		File prefixDir;

		Pack(File file) {
			this.file = file;
			if (file.isDirectory()) {
				if (!new File(file, "assets").isDirectory() && !new File(file, "pack.mcmeta").isFile()) {
					File[] subs = file.listFiles();
					if (subs != null) {
						for (File sub : subs) {
							if (sub.isDirectory() && (new File(sub, "assets").isDirectory() || new File(sub, "pack.mcmeta").isFile())) {
								prefixDir = sub;
								break;
							}
						}
					}
				}
			}
		}

		private byte[] readRaw(String path) {
			try {
				if (file.isDirectory()) {
					File base = prefixDir != null ? prefixDir : file;
					File f = new File(base, path);
					if (f.isFile()) {
						return Files.readAllBytes(f.toPath());
					}
					if (path.indexOf('/') >= 0) {
						File f2 = new File(base, path.replace('/', File.separatorChar));
						if (f2.isFile()) {
							return Files.readAllBytes(f2.toPath());
						}
					}
					return null;
				}
				if (zip == null && !broken) {
					zip = new ZipFile(file);
					if (zip.getEntry("assets") == null && zip.getEntry("pack.mcmeta") == null) {
						Enumeration<? extends ZipEntry> en = zip.entries();
						while (en.hasMoreElements()) {
							ZipEntry ze = en.nextElement();
							String name = ze.getName();
							int idx = name.indexOf("/assets/");
							if (idx >= 0) {
								prefix = name.substring(0, idx + 1);
								break;
							}
							if (name.endsWith("/pack.mcmeta")) {
								prefix = name.substring(0, name.length() - "pack.mcmeta".length());
								break;
							}
						}
					}
				}
				if (zip == null) {
					return null;
				}
				ZipEntry e = zip.getEntry(path);
				if (e == null && !prefix.isEmpty()) {
					e = zip.getEntry(prefix + path);
				}
				if (e == null && path.indexOf('/') >= 0) {
					e = zip.getEntry(path.replace('/', '\\'));
					if (e == null && !prefix.isEmpty()) {
						e = zip.getEntry((prefix + path).replace('/', '\\'));
					}
				}
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

		byte[] read(String path) {
			byte[] b = readRaw(path);
			if (b != null) {
				return b;
			}
			for (String v : variantsOf(path)) {
				b = readRaw(v);
				if (b != null) {
					return b;
				}
			}
			return null;
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

	private static List<String> variantsOf(String path) {
		Set<String> set = new LinkedHashSet<String>();
		// item <-> items (1.8-1.12 vs 1.13+)
		if (path.contains("/textures/item/")) {
			set.add(path.replace("/textures/item/", "/textures/items/"));
		} else if (path.contains("/textures/items/")) {
			set.add(path.replace("/textures/items/", "/textures/item/"));
		}
		// block <-> blocks
		if (path.contains("/textures/block/")) {
			set.add(path.replace("/textures/block/", "/textures/blocks/"));
		} else if (path.contains("/textures/blocks/")) {
			set.add(path.replace("/textures/blocks/", "/textures/block/"));
		}

		// golden_ <-> gold_
		if (path.contains("golden_")) {
			String alt = path.replace("golden_", "gold_");
			set.add(alt);
			if (alt.contains("/textures/item/")) {
				set.add(alt.replace("/textures/item/", "/textures/items/"));
			}
		} else if (path.contains("gold_")) {
			String alt = path.replace("gold_", "golden_");
			set.add(alt);
			if (alt.contains("/textures/items/")) {
				set.add(alt.replace("/textures/items/", "/textures/item/"));
			}
		}

		// wooden_ <-> wood_
		if (path.contains("wooden_")) {
			String alt = path.replace("wooden_", "wood_");
			set.add(alt);
			if (alt.contains("/textures/item/")) {
				set.add(alt.replace("/textures/item/", "/textures/items/"));
			}
		} else if (path.contains("wood_")) {
			String alt = path.replace("wood_", "wooden_");
			set.add(alt);
			if (alt.contains("/textures/items/")) {
				set.add(alt.replace("/textures/items/", "/textures/item/"));
			}
		}

		// OptiFine / MCPatcher CIT textures
		if (path.contains("/textures/item/") || path.contains("/textures/items/")) {
			int lastSlash = path.lastIndexOf('/');
			if (lastSlash >= 0) {
				String fn = path.substring(lastSlash + 1);
				set.add("assets/minecraft/optifine/cit/" + fn);
				set.add("assets/minecraft/optifine/cit/items/" + fn);
				set.add("assets/minecraft/optifine/cit/item/" + fn);
				set.add("assets/minecraft/mcpatcher/cit/" + fn);
				set.add("assets/minecraft/mcpatcher/cit/items/" + fn);
			}
		}

		return new ArrayList<String>(set);
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
			StringBuilder sig = new StringBuilder();
			if (opt.isFile()) {
				for (String line : Files.readAllLines(opt.toPath(), StandardCharsets.UTF_8)) {
					if (line.startsWith("resourcePacks:")) {
						sig.append(line).append(';');
						String val = line.substring("resourcePacks:".length()).trim();
						if (val.startsWith("[")) {
							try {
								JsonArray arr = new JsonParser().parse(val).getAsJsonArray();
								for (JsonElement e : arr) {
									names.add(e.getAsString());
								}
							} catch (Throwable t) {
								String inner = val.substring(1, val.endsWith("]") ? val.length() - 1 : val.length());
								for (String part : inner.split(",")) {
									String p = part.trim();
									if (p.startsWith("\"") && p.endsWith("\"") && p.length() >= 2) {
										p = p.substring(1, p.length() - 1);
									}
									if (!p.isEmpty()) {
										names.add(p);
									}
								}
							}
						}
					}
				}
			}
			List<File> files = new ArrayList<File>();
			File rpDir = dir.resolve("resourcepacks").toFile();
			for (int i = names.size() - 1; i >= 0; i--) { // options.txt lists the lowest priority first
				String n = names.get(i).trim();
				if (n.startsWith("\"") && n.endsWith("\"") && n.length() >= 2) {
					n = n.substring(1, n.length() - 1);
				}
				String packName = n;
				if (packName.startsWith("file/")) {
					packName = packName.substring(5);
				} else if (packName.startsWith("file:")) {
					packName = packName.substring(5);
				}
				if (packName.equalsIgnoreCase("vanilla") || packName.equalsIgnoreCase("fabric")
						|| packName.equalsIgnoreCase("mod_resources") || packName.startsWith("mod:") || packName.isEmpty()) {
					continue;
				}
				File f = new File(rpDir, packName);
				if (!f.exists() && !packName.toLowerCase().endsWith(".zip")) {
					File fZip = new File(rpDir, packName + ".zip");
					if (fZip.exists()) {
						f = fZip;
					}
				}
				if (!f.exists() && packName.toLowerCase().endsWith(".zip")) {
					File fDir = new File(rpDir, packName.substring(0, packName.length() - 4));
					if (fDir.exists()) {
						f = fDir;
					}
				}
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
