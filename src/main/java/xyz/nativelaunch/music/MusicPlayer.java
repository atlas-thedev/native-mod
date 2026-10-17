package xyz.nativelaunch.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import xyz.nativelaunch.core.Log;
import xyz.nativelaunch.ui.gfx.Image;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Native Music: plays the songs in one music folder shared by every instance (the launcher's minecraft/music), with
 * playlists (its sub-folders), shuffle, repeat, seeking and the sound bars. Audio runs on its own thread straight to
 * the speakers, so it keeps playing in menus, between worlds and while the game is paused. UI calls are main-thread
 * friendly: they only read snapshots or queue a command.
 */
public final class MusicPlayer {
	public enum Repeat { OFF, ALL, ONE }

	public static final String[] FORMATS = {"mp3", "ogg", "oga", "wav"};

	/** One song file. Tags are filled in by the library thread. */
	public static final class Track {
		public final Path path;
		/** Path inside the music folder, with '/' (what is remembered between sessions). */
		public final String rel;
		/** Playlist (first sub-folder), or null for songs at the top. */
		public final String playlist;
		final long size, modified;
		public volatile String title, artist, album;
		public volatile long durationMs = -1;
		public volatile String error;
		volatile boolean read;
		volatile byte[] coverBytes;
		volatile boolean hasCover;

		Track(Path path, String rel, String playlist, long size, long modified) {
			this.path = path;
			this.rel = rel;
			this.playlist = playlist;
			this.size = size;
			this.modified = modified;
			TrackInfo t = new TrackInfo();
			TrackInfo.fromName(t, path.getFileName().toString());
			title = t.title;
			artist = t.artist;
		}

		public String subtitle() {
			String a = artist == null || artist.isEmpty() ? null : artist;
			return a != null ? a : album != null ? album : "Unknown artist";
		}
	}

	private MusicPlayer() {
	}

	// ── shared state (read by the UI) ────────────────────────────────────

	private static volatile Path folder;
	private static volatile List<Track> library = Collections.emptyList();
	private static volatile List<String> playlists = Collections.emptyList();
	private static volatile String playlist; // null = all songs
	private static volatile Track current;
	private static volatile boolean playing;
	private static volatile long positionMs, seekingTo = -1;
	private static volatile String error;
	private static volatile long errorAt;
	private static volatile boolean scanning, scannedOnce;
	private static final Spectrum spectrum = new Spectrum();

	// settings, written by the Music Player module every frame
	public static volatile float volume = 0.7f;
	public static volatile boolean shuffle;
	public static volatile Repeat repeat = Repeat.ALL;
	/** 1 = full volume, lower while in a voice call (set by the module). */
	public static volatile float duck = 1f;

	private static final ConcurrentLinkedQueue<Runnable> commands = new ConcurrentLinkedQueue<Runnable>();
	private static Thread audioThread, libraryThread;
	private static volatile boolean running;
	private static boolean hooked;
	private static final Object wake = new Object();

	// ── folder ───────────────────────────────────────────────────────────

	/**
	 * The shared music folder. Launched by Native the instance lives in {@code <root>/instances/<id>}: songs go in
	 * {@code <root>/music} next to the shared assets. Other launchers use the Native launcher's own folder
	 * ({@code <appdata>/Native/minecraft/music}), so every instance on the PC still sees the same songs.
	 */
	static Path resolveFolder(Path gameDir, Map<String, String> env, String os, String home) {
		Path dir = gameDir == null ? null : gameDir.toAbsolutePath().normalize();
		Path parent = dir == null ? null : dir.getParent();
		if (parent != null && parent.getFileName() != null && "instances".equals(parent.getFileName().toString()) && parent.getParent() != null) {
			Path root = parent.getParent();
			if (Files.isDirectory(root.resolve("versions")) || Files.isDirectory(root.resolve("assets")) || Files.isDirectory(root.resolve("music"))) {
				return root.resolve("music");
			}
		}
		String o = os.toLowerCase(Locale.ROOT);
		Path appData;
		if (o.contains("win")) {
			String v = env.get("APPDATA");
			appData = v != null && !v.isEmpty() ? Paths.get(v) : Paths.get(home, "AppData", "Roaming");
		} else if (o.contains("mac")) {
			appData = Paths.get(home, "Library", "Application Support");
		} else {
			String v = env.get("XDG_CONFIG_HOME");
			appData = v != null && !v.isEmpty() ? Paths.get(v) : Paths.get(home, ".config");
		}
		for (String name : new String[] {"Native", "Native Client", "native-client"}) {
			if (Files.isDirectory(appData.resolve(name).resolve("minecraft"))) {
				return appData.resolve(name).resolve("minecraft").resolve("music");
			}
		}
		return appData.resolve("Native").resolve("minecraft").resolve("music");
	}

	public static synchronized void init(Path gameDir) {
		if (folder == null) {
			folder = resolveFolder(gameDir, System.getenv(), System.getProperty("os.name", ""), System.getProperty("user.home", "."));
		}
	}

	public static Path folder() {
		return folder;
	}

	/** Opens the music folder in the file manager (creating it first). */
	public static void openFolder() {
		final Path dir = folder;
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
				Log.debug("Could not open the music folder: {}", e.toString());
			}
			rescanSoon();
		}, "Native-Music-OpenFolder");
		t.setDaemon(true);
		t.start();
	}

	// ── lifecycle ────────────────────────────────────────────────────────

	/** Starts the audio and library threads (module switched on). Idempotent. */
	public static synchronized void start() {
		if (running) {
			return;
		}
		Thread old = audioThread;
		if (old != null && old.isAlive()) {
			try {
				old.join(1000); // switched off and on again: the old thread lets go of the speakers first
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
		}
		running = true;
		if (!hooked) {
			hooked = true;
			// closing the game: remember the song and where it was, so the next session picks up there
			Runtime.getRuntime().addShutdownHook(new Thread(MusicPlayer::saveState, "Native-Music-Save"));
		}
		audioThread = new Thread(MusicPlayer::audioLoop, "Native-Music");
		audioThread.setDaemon(true);
		audioThread.setPriority(Thread.NORM_PRIORITY + 1);
		audioThread.start();
		libraryThread = new Thread(MusicPlayer::libraryLoop, "Native-Music-Library");
		libraryThread.setDaemon(true);
		libraryThread.setPriority(Thread.MIN_PRIORITY);
		libraryThread.start();
	}

	/** Stops playing and lets the speakers go (module switched off / game closing). */
	public static synchronized void stop() {
		if (!running) {
			return;
		}
		saveState();
		running = false;
		playing = false;
		synchronized (wake) {
			wake.notifyAll();
		}
	}

	public static boolean running() {
		return running;
	}

	// ── what the UI reads ────────────────────────────────────────────────

	public static List<Track> library() {
		return library;
	}

	public static List<String> playlists() {
		return playlists;
	}

	public static String playlist() {
		return playlist;
	}

	public static void setPlaylist(String name) {
		playlist = name;
	}

	/** Songs of the chosen playlist (all songs when none is chosen). */
	public static List<Track> visible() {
		String p = playlist;
		List<Track> all = library;
		if (p == null) {
			return all;
		}
		List<Track> out = new ArrayList<Track>();
		for (Track t : all) {
			if (p.equals(t.playlist)) {
				out.add(t);
			}
		}
		return out;
	}

	public static Track current() {
		return current;
	}

	public static boolean playing() {
		return playing;
	}

	public static long position() {
		long s = seekingTo;
		return s >= 0 ? s : positionMs;
	}

	public static long duration() {
		Track t = current;
		return t == null ? -1 : t.durationMs;
	}

	public static boolean scanning() {
		return scanning || !scannedOnce;
	}

	/** A problem worth showing for a few seconds ("Couldn't play ..."), or null. */
	public static String error() {
		return error != null && System.currentTimeMillis() - errorAt < 8000 ? error : null;
	}

	private static void fail(String message) {
		error = message;
		errorAt = System.currentTimeMillis();
	}

	public static void bars(float[] out) {
		spectrum.read(out);
	}

	// ── commands ─────────────────────────────────────────────────────────

	private static void send(Runnable r) {
		commands.add(r);
		synchronized (wake) {
			wake.notifyAll();
		}
	}

	public static void play(final Track t) {
		if (t == null) {
			return;
		}
		send(() -> open(t, 0, true));
	}

	public static void toggle() {
		if (current == null) {
			List<Track> v = order();
			if (!v.isEmpty()) {
				play(v.get(0));
			}
			return;
		}
		send(() -> {
			if (source == null && current != null) {
				open(current, positionMs, true);
			} else {
				playing = !playing;
			}
			saveSoon = true;
		});
	}

	public static void next() {
		send(() -> advance(1, false));
	}

	/** Back to the start of the song, or the previous song in the first three seconds. */
	public static void previous() {
		send(() -> {
			if (current != null && positionMs > 3000) {
				open(current, 0, playing);
			} else {
				advance(-1, false);
			}
		});
	}

	public static void seek(final long ms) {
		final Track t = current;
		if (t == null) {
			return;
		}
		seekingTo = Math.max(0, ms);
		send(() -> {
			open(t, Math.max(0, ms), playing);
			seekingTo = -1;
		});
	}

	// ── play order ───────────────────────────────────────────────────────

	private static List<Track> shuffled = Collections.emptyList();
	private static List<Track> shuffledFrom = Collections.emptyList();
	private static final Random random = new Random();

	/** The order songs play in: the playlist, or a shuffled copy that stays put until the playlist changes. */
	static synchronized List<Track> order() {
		List<Track> v = visible();
		if (!shuffle) {
			return v;
		}
		if (shuffledFrom != v && !shuffledFrom.equals(v)) {
			List<Track> s = new ArrayList<Track>(v);
			Collections.shuffle(s, random);
			Track c = current;
			if (c != null && s.remove(c)) {
				s.add(0, c); // the song playing now stays first, so "next" never repeats it
			}
			shuffled = s;
			shuffledFrom = v;
		}
		return shuffled;
	}

	/** The song {@code step} away from the current one, or null at the end (repeat off). */
	static Track step(List<Track> order, Track cur, int step, Repeat rep) {
		if (order.isEmpty()) {
			return null;
		}
		int i = cur == null ? -1 : order.indexOf(cur);
		int n = i + step;
		if (n < 0 || n >= order.size()) {
			if (rep == Repeat.OFF && cur != null) {
				return null;
			}
			n = ((n % order.size()) + order.size()) % order.size();
		}
		return order.get(n);
	}

	private static void advance(int dir, boolean ended) {
		Track cur = current;
		if (ended && repeat == Repeat.ONE && cur != null) {
			open(cur, 0, true);
			return;
		}
		List<Track> order = order();
		// skip songs that can't be played, at most once around the list
		for (int tries = 0; tries < Math.max(1, order.size()); tries++) {
			Track next = step(order, cur, dir, ended ? repeat : Repeat.ALL);
			if (next == null) {
				// the end of the playlist with repeat off: stop on the last song, back at its start
				playing = false;
				closeSource();
				positionMs = 0;
				return;
			}
			if (next.error == null || !ended) {
				open(next, 0, true);
				if (current == next && source != null) {
					return;
				}
			}
			cur = next;
		}
		playing = false;
	}

	// ── audio thread ─────────────────────────────────────────────────────

	private static PcmSource source;
	private static SourceDataLine line;
	private static int lineRate;
	private static long openedAtMs, framesOut;
	private static float gain;
	private static volatile boolean saveSoon;
	private static long idleSince;

	/** Runs on the audio thread: switches to {@code t} at {@code ms}. */
	private static void open(Track t, long ms, boolean play) {
		closeSource();
		if (line != null) {
			line.flush(); // drop what was queued for the old song / position
		}
		try {
			source = PcmSource.open(t.path, ms);
			t.error = null;
		} catch (Throwable e) {
			source = null;
			t.error = e.getMessage() == null ? "Can't play this file." : e.getMessage();
			fail("Couldn't play " + t.title + ": " + t.error);
			Log.debug("Music: {} - {}", t.rel, e.toString());
			current = t;
			playing = false;
			positionMs = ms;
			return;
		}
		current = t;
		openedAtMs = ms;
		framesOut = 0;
		positionMs = ms;
		gain = 0; // fade in: no click when jumping into the middle of a waveform
		playing = play;
		saveSoon = true;
		if (!t.read) {
			readInfo(t);
		}
	}

	private static void closeSource() {
		if (source != null) {
			source.close();
			source = null;
		}
	}

	private static boolean ensureLine(int rate) {
		if (line != null && lineRate == rate) {
			return true;
		}
		closeLine();
		try {
			AudioFormat fmt = new AudioFormat(rate, 16, 2, true, false);
			SourceDataLine l = AudioSystem.getSourceDataLine(fmt);
			l.open(fmt, rate / 8 * 4); // ~125 ms: short enough that pause and the bars feel instant
			l.start();
			line = l;
			lineRate = rate;
			return true;
		} catch (Throwable e) {
			fail("No sound device for music: " + e.getMessage());
			playing = false;
			return false;
		}
	}

	private static void closeLine() {
		if (line != null) {
			try {
				line.stop();
				line.close();
			} catch (Throwable ignored) {
				// already gone
			}
			line = null;
		}
	}

	private static void audioLoop() {
		short[] pcm = new short[2048 * 2];
		short[] stereo = new short[2048 * 2];
		byte[] out = new byte[2048 * 4];
		long lastSave = System.currentTimeMillis();
		loadState();
		try {
			while (running) {
				Runnable c;
				while ((c = commands.poll()) != null) {
					try {
						c.run();
					} catch (Throwable t) {
						Log.debug("Music command failed: {}", t.toString());
					}
				}
				long now = System.currentTimeMillis();
				if (saveSoon && now - lastSave > 1500 || now - lastSave > 15000) {
					saveSoon = false;
					lastSave = now;
					saveState();
				}
				float target = playing ? volume * duck : 0f;
				if (source == null || !playing && gain <= 0.0005f) {
					// silent: let go of the speakers after a while, the bars sink
					if (line != null && line.isActive()) {
						line.drain();
						line.stop();
						if (source != null) {
							positionMs = openedAtMs + framesOut * 1000L / Math.max(1, lineRate);
						}
						idleSince = now;
					} else if (line != null && now - idleSince > 30_000) {
						closeLine(); // paused for a while: other apps get the sound device back
					}
					spectrum.decay(0.05f);
					synchronized (wake) {
						if (commands.isEmpty() && running) {
							wake.wait(50);
						}
					}
					continue;
				}
				int rate = source.rate(), ch = source.channels();
				if (!ensureLine(rate)) {
					continue;
				}
				if (!line.isActive()) {
					line.start();
				}
				int n = source.read(pcm, 1024);
				if (n < 0) {
					advance(1, true);
					continue;
				}
				// mono -> stereo, volume with a short ramp (fades on pause, seek and song change)
				float step = 1f / (rate * 0.03f);
				for (int f = 0; f < n; f++) {
					if (gain < target) {
						gain = Math.min(target, gain + step);
					} else if (gain > target) {
						gain = Math.max(target, gain - step);
					}
					int l = pcm[f * ch], r = ch > 1 ? pcm[f * ch + 1] : l;
					stereo[f * 2] = (short) Math.max(-32768, Math.min(32767, Math.round(l * gain)));
					stereo[f * 2 + 1] = (short) Math.max(-32768, Math.min(32767, Math.round(r * gain)));
				}
				for (int i = 0; i < n * 2; i++) {
					out[i * 2] = (byte) stereo[i];
					out[i * 2 + 1] = (byte) (stereo[i] >> 8);
				}
				line.write(out, 0, n * 4); // blocks: paces the loop at real time
				framesOut += n;
				spectrum.feed(stereo, n, 2, rate);
				long queued = Math.max(0, (line.getBufferSize() - line.available()) / 4);
				positionMs = openedAtMs + Math.max(0, framesOut - queued) * 1000L / rate;
			}
		} catch (Throwable t) {
			Log.warn("Music stopped: {}", t.toString());
		} finally {
			closeSource();
			closeLine();
			playing = false;
		}
	}

	// ── library ──────────────────────────────────────────────────────────

	private static volatile boolean rescan = true;

	public static void rescanSoon() {
		rescan = true;
	}

	private static void libraryLoop() {
		String signature = null;
		while (running) {
			try {
				Path dir = folder;
				if (dir != null) {
					String sig = signature(dir);
					if (rescan || !sig.equals(signature)) {
						rescan = false;
						signature = sig;
						scanning = true;
						scan(dir);
						scanning = false;
						scannedOnce = true;
					}
					for (Track t : library) {
						if (!running) {
							break;
						}
						if (!t.read) {
							readInfo(t);
						}
					}
				}
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				return;
			} catch (Throwable t) {
				scanning = false;
				scannedOnce = true;
				Log.debug("Music library: {}", t.toString());
			}
		}
	}

	static boolean playable(Path p) {
		String ext = TrackInfo.extension(p);
		for (String f : FORMATS) {
			if (f.equals(ext)) {
				return true;
			}
		}
		return false;
	}

	/** Changes whenever a song is added, removed, renamed or replaced (cheap: listing only). */
	private static String signature(Path dir) throws IOException {
		StringBuilder sb = new StringBuilder();
		if (!Files.isDirectory(dir)) {
			return "";
		}
		List<Path> dirs = new ArrayList<Path>();
		dirs.add(dir);
		try (DirectoryStream<Path> top = Files.newDirectoryStream(dir)) {
			for (Path p : top) {
				if (Files.isDirectory(p)) {
					dirs.add(p);
				}
			}
		}
		for (Path d : dirs) {
			sb.append(d.getFileName()).append(':').append(Files.getLastModifiedTime(d).toMillis()).append(';');
		}
		return sb.toString();
	}

	static void scan(Path dir) throws IOException {
		Files.createDirectories(dir);
		Map<String, Track> old = new HashMap<String, Track>();
		for (Track t : library) {
			old.put(t.rel, t);
		}
		List<Track> found = new ArrayList<Track>();
		java.util.Set<String> lists = new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		try (DirectoryStream<Path> top = Files.newDirectoryStream(dir)) {
			for (Path p : top) {
				if (Files.isDirectory(p)) {
					String name = p.getFileName().toString();
					if (name.startsWith(".")) {
						continue;
					}
					try (DirectoryStream<Path> sub = Files.newDirectoryStream(p)) {
						for (Path q : sub) {
							if (Files.isRegularFile(q) && playable(q)) {
								found.add(track(old, dir, q, name));
								lists.add(name);
							}
						}
					}
				} else if (Files.isRegularFile(p) && playable(p)) {
					found.add(track(old, dir, p, null));
				}
			}
		}
		Collections.sort(found, (a, b) -> {
			int c = String.CASE_INSENSITIVE_ORDER.compare(a.playlist == null ? "" : a.playlist, b.playlist == null ? "" : b.playlist);
			return c != 0 ? c : String.CASE_INSENSITIVE_ORDER.compare(a.rel, b.rel);
		});
		library = Collections.unmodifiableList(found);
		playlists = Collections.unmodifiableList(new ArrayList<String>(lists));
		if (playlist != null && !lists.contains(playlist)) {
			playlist = null;
		}
		if (pendingRestore != null) {
			restore();
		}
	}

	private static Track track(Map<String, Track> old, Path root, Path file, String list) throws IOException {
		String rel = root.relativize(file).toString().replace('\\', '/');
		long size = Files.size(file), modified = Files.getLastModifiedTime(file).toMillis();
		Track prev = old.get(rel);
		if (prev != null && prev.size == size && prev.modified == modified) {
			return prev;
		}
		return new Track(file, rel, list, size, modified);
	}

	private static void readInfo(Track t) {
		TrackInfo info = TrackInfo.read(t.path);
		t.title = info.title;
		t.artist = info.artist;
		t.album = info.album;
		if (info.durationMs > 0) {
			t.durationMs = info.durationMs;
		}
		t.hasCover = info.cover != null;
		t.coverBytes = info.cover != null && info.cover.length <= 8 * 1024 * 1024 ? info.cover : null;
		t.read = true;
	}

	// ── covers ───────────────────────────────────────────────────────────

	private static final Map<String, Image> covers = Collections.synchronizedMap(new LinkedHashMap<String, Image>(32, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Image> e) {
			return size() > 48;
		}
	});
	private static final Map<String, Integer> accents = Collections.synchronizedMap(new HashMap<String, Integer>());
	private static final java.util.Set<String> decoding = Collections.synchronizedSet(new java.util.HashSet<String>());
	private static final Image NONE = new Image(1, 1, new int[1]);
	private static final java.util.concurrent.ExecutorService coverPool = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Native-Music-Cover");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});

	/** The cover picture (scaled to at most 256 px), or null while loading / when the song has none. */
	public static Image cover(final Track t) {
		if (t == null || !t.read || !t.hasCover) {
			return null;
		}
		String key = t.rel + "@" + t.modified;
		Image img = covers.get(key);
		if (img != null) {
			return img == NONE ? null : img;
		}
		if (decoding.add(key)) {
			coverPool.execute(() -> {
				Image done = NONE;
				try {
					byte[] b = t.coverBytes;
					if (b == null) {
						b = TrackInfo.read(t.path).cover;
					}
					Image full = b == null ? null : Image.decode(b);
					if (full != null) {
						Image small = scaled(full, 256);
						accents.put(key, accent(small));
						done = rounded(small, 0.09f);
					}
				} catch (Throwable e) {
					Log.debug("Music cover: {}", e.toString());
				} finally {
					covers.put(key, done);
					decoding.remove(key);
				}
			});
		}
		return null;
	}

	/** A colour from the cover for the player's glow (Spotify style), or {@code fallback}. */
	public static int accent(Track t, int fallback) {
		if (t == null) {
			return fallback;
		}
		Integer a = accents.get(t.rel + "@" + t.modified);
		return a == null ? fallback : a;
	}

	static Image scaled(Image src, int max) {
		int w = src.width, h = src.height;
		if (w <= max && h <= max) {
			return src;
		}
		float k = Math.max(w, h) / (float) max;
		int nw = Math.max(1, Math.round(w / k)), nh = Math.max(1, Math.round(h / k));
		int[] px = new int[nw * nh];
		// box filter: average every source pixel under each target pixel
		for (int y = 0; y < nh; y++) {
			int y0 = (int) (y * k), y1 = Math.max(y0 + 1, Math.min(h, (int) ((y + 1) * k)));
			for (int x = 0; x < nw; x++) {
				int x0 = (int) (x * k), x1 = Math.max(x0 + 1, Math.min(w, (int) ((x + 1) * k)));
				long a = 0, r = 0, g = 0, b = 0;
				int n = 0;
				for (int yy = y0; yy < y1; yy++) {
					for (int xx = x0; xx < x1; xx++) {
						int c = src.argb[yy * w + xx];
						a += c >>> 24;
						r += (c >> 16) & 0xFF;
						g += (c >> 8) & 0xFF;
						b += c & 0xFF;
						n++;
					}
				}
				px[y * nw + x] = (int) (a / n) << 24 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
			}
		}
		return new Image(nw, nh, px);
	}

	/** Rounded corners baked into the pixels (radius as a share of the size), anti-aliased. */
	static Image rounded(Image src, float frac) {
		int w = src.width, h = src.height;
		float r = Math.min(w, h) * frac;
		int[] px = src.argb.clone();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				float dx = Math.max(0, Math.max(r - x - 0.5f, x + 0.5f - (w - r))), dy = Math.max(0, Math.max(r - y - 0.5f, y + 0.5f - (h - r)));
				if (dx <= 0 || dy <= 0) {
					continue;
				}
				float cover = Math.max(0, Math.min(1, r - (float) Math.sqrt(dx * dx + dy * dy) + 0.5f));
				int c = px[y * w + x];
				px[y * w + x] = Math.round((c >>> 24) * cover) << 24 | (c & 0xFFFFFF);
			}
		}
		return new Image(w, h, px);
	}

	/** The cover's most colourful average, brightened enough to glow on a dark card. */
	static int accent(Image img) {
		double r = 0, g = 0, b = 0, wsum = 0;
		for (int c : img.argb) {
			int cr = (c >> 16) & 0xFF, cg = (c >> 8) & 0xFF, cb = c & 0xFF;
			int max = Math.max(cr, Math.max(cg, cb)), min = Math.min(cr, Math.min(cg, cb));
			double w = 0.05 + (max - min) / 255.0 * (max / 255.0); // colourful, bright pixels count most
			r += cr * w;
			g += cg * w;
			b += cb * w;
			wsum += w;
		}
		float[] hsv = hsv((float) (r / wsum / 255), (float) (g / wsum / 255), (float) (b / wsum / 255));
		return rgb(hsv[0], Math.min(1f, hsv[1] * 1.25f + 0.08f), Math.max(0.55f, Math.min(0.85f, hsv[2] * 1.2f)));
	}

	// own colour maths: java.awt.Color loads AWT's native library, which can hang an LWJGL game on macOS
	static float[] hsv(float r, float g, float b) {
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
		float h = 0;
		if (d > 0) {
			h = max == r ? ((g - b) / d) % 6 : max == g ? (b - r) / d + 2 : (r - g) / d + 4;
			h /= 6;
			if (h < 0) {
				h += 1;
			}
		}
		return new float[] {h, max == 0 ? 0 : d / max, max};
	}

	static int rgb(float h, float s, float v) {
		float c = v * s, x = c * (1 - Math.abs((h * 6) % 2 - 1)), m = v - c;
		float r, g, b;
		int i = (int) (h * 6) % 6;
		if (i == 0) { r = c; g = x; b = 0; } else if (i == 1) { r = x; g = c; b = 0; } else if (i == 2) { r = 0; g = c; b = x; }
		else if (i == 3) { r = 0; g = x; b = c; } else if (i == 4) { r = x; g = 0; b = c; } else { r = c; g = 0; b = x; }
		return 0xFF000000 | Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
	}

	// ── remembered between sessions (in the shared folder) ───────────────

	private static volatile String pendingRestore;
	private static volatile long pendingPosition;

	private static Path stateFile() {
		Path dir = folder;
		return dir == null ? null : dir.resolve(".native-player.json");
	}

	private static void loadState() {
		try {
			Path f = stateFile();
			if (f == null || !Files.isRegularFile(f)) {
				return;
			}
			JsonObject o = new JsonParser().parse(new String(Files.readAllBytes(f), StandardCharsets.UTF_8)).getAsJsonObject();
			pendingRestore = o.has("track") ? o.get("track").getAsString() : null;
			pendingPosition = o.has("position") ? o.get("position").getAsLong() : 0;
			if (o.has("playlist") && !o.get("playlist").isJsonNull()) {
				playlist = o.get("playlist").getAsString();
			}
		} catch (Throwable t) {
			Log.debug("Music state: {}", t.toString());
		}
	}

	/** After the first scan: the last song is back, paused where it was. */
	private static void restore() {
		String rel = pendingRestore;
		pendingRestore = null;
		for (Track t : library) {
			if (t.rel.equals(rel)) {
				final long at = pendingPosition;
				send(() -> {
					if (current == null) {
						current = t;
						positionMs = at;
						if (!t.read) {
							readInfo(t);
						}
					}
				});
				return;
			}
		}
	}

	private static void saveState() {
		try {
			Path f = stateFile();
			Track t = current;
			if (f == null || t == null) {
				return;
			}
			JsonObject o = new JsonObject();
			o.addProperty("track", t.rel);
			o.addProperty("position", positionMs);
			o.addProperty("playlist", playlist);
			Files.createDirectories(f.getParent());
			Path tmp = f.resolveSibling(".native-player.json.tmp");
			Files.write(tmp, o.toString().getBytes(StandardCharsets.UTF_8));
			Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (Throwable e) {
			Log.debug("Music state not saved: {}", e.toString());
		}
	}

	/** "3:07", "1:02:45". */
	public static String time(long ms) {
		if (ms < 0) {
			return "--:--";
		}
		long s = ms / 1000;
		return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60) : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
	}
}
