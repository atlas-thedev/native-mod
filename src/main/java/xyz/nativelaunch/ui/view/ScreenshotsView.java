package xyz.nativelaunch.ui.view;

import xyz.nativelaunch.core.Log;
import xyz.nativelaunch.core.NativeState;
import xyz.nativelaunch.ui.Scroll;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.ui.gfx.Image;
import xyz.nativelaunch.ui.gfx.NavIcons;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Screenshots page of the Native menu: every picture in the game's screenshots folder as a grid of thumbnails
 * (newest first), a viewer for one picture, and open-folder / copy-path / delete. Pictures are decoded off the render
 * thread, only for the rows on screen.
 */
public final class ScreenshotsView {
	/** One screenshot file. */
	static final class Shot {
		final Path path;
		final String name, when, size;
		final long modified;

		Shot(Path path, long modified, long bytes) {
			this.path = path;
			this.name = path.getFileName().toString();
			this.modified = modified;
			this.when = new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ENGLISH).format(new java.util.Date(modified));
			this.size = bytes >= 1048576 ? String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0) : Math.max(1, bytes / 1024) + " KB";
		}
	}

	private static final int MAX_SHOTS = 600;
	private final Scroll scroll = new Scroll();
	private volatile List<Shot> shots = Collections.emptyList();
	private volatile boolean loaded;
	private long scannedAt;
	private final Map<String, Image> thumbs = new HashMap<String, Image>();
	private final Set<String> pending = new HashSet<String>();
	private final java.util.concurrent.ConcurrentHashMap<String, Image> ready = new java.util.concurrent.ConcurrentHashMap<String, Image>();
	private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "Native-Screenshots");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});
	private Shot open;
	private volatile Image big;
	private volatile boolean bigLoading;
	private String confirm;
	private String note;
	private long noteAt;

	/** Files of the screenshots folder, newest first (pure, so it can be tested). */
	static List<Shot> list(Path dir) {
		List<Shot> out = new ArrayList<Shot>();
		if (dir == null || !Files.isDirectory(dir)) {
			return out;
		}
		File[] files = dir.toFile().listFiles();
		if (files == null) {
			return out;
		}
		for (File f : files) {
			String n = f.getName().toLowerCase(Locale.ROOT);
			if (f.isFile() && (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg"))) {
				out.add(new Shot(f.toPath(), f.lastModified(), f.length()));
			}
		}
		Collections.sort(out, new Comparator<Shot>() {
			@Override
			public int compare(Shot a, Shot b) {
				int d = Long.compare(b.modified, a.modified);
				return d != 0 ? d : a.name.compareTo(b.name);
			}
		});
		return out.size() > MAX_SHOTS ? new ArrayList<Shot>(out.subList(0, MAX_SHOTS)) : out;
	}

	private static Path folder() {
		Path dir = NativeState.get().gameDir();
		return dir == null ? null : dir.resolve("screenshots");
	}

	/** The page was opened (or the menu came back): rescan the folder. */
	public void refresh() {
		scannedAt = System.currentTimeMillis();
		pool.execute(() -> {
			shots = list(folder());
			loaded = true;
		});
	}

	/** Esc: closes the viewer first. True when it was consumed. */
	public boolean escape() {
		if (confirm != null) {
			confirm = null;
			return true;
		}
		if (open != null) {
			close();
			return true;
		}
		return false;
	}

	private void close() {
		open = null;
		big = null;
		bigLoading = false;
		confirm = null;
	}

	public void draw(Ui ui, float x, float y, float w, float h) {
		Canvas c = ui.c;
		long now = System.currentTimeMillis();
		if (!loaded || now - scannedAt > 6000 && open == null) {
			if (!loaded && scannedAt == 0 || now - scannedAt > 6000) {
				refresh();
			}
		}
		for (Map.Entry<String, Image> e : ready.entrySet()) { // thumbnails the workers finished since the last frame
			thumbs.put(e.getKey(), e.getValue());
			pending.remove(e.getKey());
		}
		ready.clear();
		if (open != null) {
			viewer(ui, x, y, w, h);
			return;
		}
		List<Shot> list = shots;
		// toolbar: count on the left, folder / refresh on the right
		float tb = 36;
		String count = !loaded ? "Looking for screenshots..." : list.isEmpty() ? "No screenshots yet" : list.size() + (list.size() == 1 ? " screenshot" : " screenshots");
		c.text(Fonts.MEDIUM, 12, count, x + 2, y + (tb - c.lineHeight(Fonts.MEDIUM, 12)) / 2, Theme.TEXT_SECONDARY);
		float bw = 130, bx = x + w - bw;
		if (ui.button("shots:folder", bx, y, bw, 32, "Open folder", Theme.I_LAYERS, Ui.BTN_GHOST)) {
			openFolder();
		}
		if (ui.iconButton("shots:refresh", bx - 38, y, 32, Theme.I_REFRESH, "Rescan the folder")) {
			refresh();
		}
		if (note != null && now - noteAt < 3500) {
			float nw = c.textWidth(Fonts.MEDIUM, 11.5f, note);
			c.text(Fonts.MEDIUM, 11.5f, note, bx - 52 - nw, y + (tb - c.lineHeight(Fonts.MEDIUM, 11.5f)) / 2, Theme.ONLINE);
		}
		float gy = y + tb + 8, gh = h - tb - 8;
		if (loaded && list.isEmpty()) {
			MenuView.empty(c, x, gy, w, gh, NavIcons.CAMERA, "No screenshots yet", "Press F2 in game to take one: it will show up here.");
			return;
		}
		float gap = 14;
		int cols = Math.max(1, (int) ((w - 6 + gap) / (210 + gap)));
		float cw = (w - 6 - gap * (cols - 1)) / cols, ch = cw * 9f / 16f + 34;
		float off = scroll.begin(ui, x, gy, w + 6, gh);
		int rows = (list.size() + cols - 1) / cols;
		for (int i = 0; i < list.size(); i++) {
			float cx = x + (i % cols) * (cw + gap), cy = gy + (i / cols) * (ch + gap) - off;
			if (cy + ch < gy - 2 || cy > gy + gh + 2) {
				continue;
			}
			card(ui, list.get(i), cx, cy, cw, ch, i);
		}
		scroll.end(ui, rows * (ch + gap));
	}

	private void card(Ui ui, Shot s, float x, float y, float w, float h, int index) {
		Canvas c = ui.c;
		boolean over = ui.hover(x, y, w, h) && ui.c.inClip(ui.mx, ui.my);
		float hv = ui.anim("shots:card:" + index + "#h", over, 14f);
		c.round(x, y, w, h, 12, Theme.mix(0xB808090C, 0xD80E1014, hv));
		c.outline(x, y, w, h, 12, 1, Theme.mix(Theme.HAIRLINE, Theme.HAIRLINE_STRONG, hv));
		float ix = x + 5, iy = y + 5, iw = w - 10, ih = w * 9f / 16f - 4;
		Image t = thumbs.get(s.name + "@" + s.modified);
		if (t == null) {
			load(s);
			c.round(ix, iy, iw, ih, 8, 0x12FFFFFF);
			c.icon(NavIcons.CAMERA, 18, ix + iw / 2, iy + ih / 2, Theme.TEXT_MUTED);
		} else {
			c.pushClip(ix, iy, iw, ih);
			c.imageCover(t, ix, iy, iw, ih, 1f, 0.5f, 0.5f, 0xFFFFFFFF);
			c.popClip();
		}
		float ty = iy + ih + 6;
		c.text(Fonts.MEDIUM, 11, c.ellipsize(Fonts.MEDIUM, 11, s.name, w - 18), x + 10, ty, Theme.TEXT);
		c.text(Fonts.REGULAR, 10, s.when + "  ·  " + s.size, x + 10, ty + 15, Theme.TEXT_MUTED);
		if (over) {
			ui.cursorHand = true;
		}
		if (ui.clicked("shots:card:" + index, x, y, w, h)) {
			open = s;
			big = null;
			bigLoading = false;
			confirm = null;
			loadBig(s);
		}
	}

	private void load(final Shot s) {
		final String key = s.name + "@" + s.modified;
		if (pending.contains(key) || pending.size() >= 6) {
			return;
		}
		pending.add(key);
		pool.execute(() -> {
			Image thumb = null;
			try {
				Image full = Image.decode(Files.readAllBytes(s.path));
				if (full != null) {
					int tw = 360, th = Math.max(1, full.height * tw / Math.max(1, full.width));
					thumb = full.width <= tw ? full : full.scaled(tw, th);
				}
			} catch (Throwable t) {
				Log.debug("Screenshot thumbnail {}: {}", s.name, t.toString());
			}
			ready.put(key, thumb == null ? new Image(1, 1, new int[] {0x22FFFFFF}) : thumb);
		});
	}

	private void loadBig(final Shot s) {
		bigLoading = true;
		pool.execute(() -> {
			Image img = null;
			try {
				Image full = Image.decode(Files.readAllBytes(s.path));
				if (full != null) {
					int max = 1920;
					img = full.width <= max ? full : full.scaled(max, Math.max(1, full.height * max / full.width));
				}
			} catch (Throwable t) {
				Log.debug("Screenshot {}: {}", s.name, t.toString());
			}
			if (open == s) {
				big = img;
			}
			bigLoading = false;
		});
	}

	private void viewer(Ui ui, float x, float y, float w, float h) {
		Canvas c = ui.c;
		Shot s = open;
		if (ui.button("shots:back", x, y, 92, 32, "Back", Theme.I_ARROW_LEFT, Ui.BTN_GHOST)) {
			close();
			return;
		}
		c.text(Fonts.SEMIBOLD, 13, c.ellipsize(Fonts.SEMIBOLD, 13, s.name, w - 560), x + 106, y + 2, Theme.TEXT_STRONG);
		c.text(Fonts.REGULAR, 10.5f, s.when + "  ·  " + s.size, x + 106, y + 19, Theme.TEXT_MUTED);
		float bx = x + w;
		bx -= 112;
		if (confirm != null) {
			if (ui.button("shots:yes", bx, y, 112, 32, "Delete it", Theme.I_TRASH, Ui.BTN_DANGER)) {
				delete(s);
				return;
			}
			bx -= 98;
			if (ui.button("shots:no", bx, y, 90, 32, "Keep", Theme.I_X, Ui.BTN_GHOST)) {
				confirm = null;
			}
			String ask = "Delete this screenshot for good?";
			c.text(Fonts.MEDIUM, 11.5f, ask, bx - 14 - c.textWidth(Fonts.MEDIUM, 11.5f, ask), y + 8, Theme.DANGER);
		} else {
			if (ui.button("shots:del", bx, y, 112, 32, "Delete", Theme.I_TRASH, Ui.BTN_GHOST)) {
				confirm = s.name;
			}
			bx -= 126;
			if (ui.button("shots:path", bx, y, 118, 32, "Copy path", Theme.I_HASH, Ui.BTN_GHOST)) {
				if (ui.clipboard != null) {
					ui.clipboard.set(s.path.toAbsolutePath().toString());
					note("Path copied");
				}
			}
			bx -= 126;
			if (ui.button("shots:folder2", bx, y, 118, 32, "Show in folder", Theme.I_LAYERS, Ui.BTN_GHOST)) {
				openFolder();
			}
		}
		float py = y + 46, ph = h - 46;
		c.round(x, py, w, ph, 12, 0x66000000);
		c.outline(x, py, w, ph, 12, 1, Theme.HAIRLINE);
		Image img = big;
		if (img == null) {
			String t = bigLoading ? "Loading..." : "This picture can't be shown";
			c.text(Fonts.MEDIUM, 12, t, x + (w - c.textWidth(Fonts.MEDIUM, 12, t)) / 2, py + ph / 2 - 8, Theme.TEXT_MUTED);
			return;
		}
		float pad = 10, aw = w - pad * 2, ah = ph - pad * 2;
		float k = Math.min(aw / img.width, ah / img.height);
		float iw = img.width * k, ih = img.height * k;
		c.image(img, x + (w - iw) / 2, py + (ph - ih) / 2, iw, ih, 0, 0, 1, 1, 0xFFFFFFFF);
	}

	private void note(String text) {
		note = text;
		noteAt = System.currentTimeMillis();
	}

	private void delete(Shot s) {
		confirm = null;
		try {
			Files.deleteIfExists(s.path);
			note("Deleted");
		} catch (Throwable t) {
			note("Could not delete it");
		}
		close();
		refresh();
	}

	private void openFolder() {
		final Path dir = folder();
		if (dir == null) {
			return;
		}
		Thread t = new Thread(() -> {
			try {
				Files.createDirectories(dir);
				String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
				String cmd = os.contains("win") ? "explorer" : os.contains("mac") ? "open" : "xdg-open";
				new ProcessBuilder(cmd, dir.toAbsolutePath().toString()).redirectErrorStream(true).start();
			} catch (Throwable e) {
				Log.debug("Could not open the screenshots folder: {}", e.toString());
			}
		}, "Native-OpenFolder");
		t.setDaemon(true);
		t.start();
	}
}
