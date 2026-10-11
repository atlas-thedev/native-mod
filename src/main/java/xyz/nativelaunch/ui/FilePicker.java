package xyz.nativelaunch.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Picks a picture from disk for the Relay composer (the launcher's attach button, in game).
 *
 * Uses LWJGL's tinyfd file dialog when the game ships it (Minecraft 1.16 - 26.2), reached by reflection so a
 * version without it (26.3, SDL) never links against it; otherwise asks the OS directly (PowerShell on Windows,
 * osascript on macOS, zenity / kdialog on Linux). Blocking: call from a worker thread.
 */
public final class FilePicker {
	/** Largest picture accepted, same as a pasted one. */
	public static final int MAX_BYTES = 8 * 1024 * 1024;

	/** What happened: the bytes of the picked picture, or a note for the player (never both). */
	public static final class Result {
		public final byte[] bytes;
		public final String note;

		Result(byte[] bytes, String note) {
			this.bytes = bytes;
			this.note = note;
		}
	}

	private FilePicker() {
	}

	/** null when the player closed the dialog without picking anything. */
	public static Result pickImage() {
		String path;
		try {
			path = tinyfd();
		} catch (Throwable t) {
			try {
				path = system();
			} catch (Throwable t2) {
				return new Result(null, "Couldn't open a file picker on this PC. Copy the picture and press Ctrl+V instead.");
			}
		}
		if (path == null || path.trim().isEmpty()) {
			return null;
		}
		File file = new File(path.trim());
		String lower = file.getName().toLowerCase(Locale.ROOT);
		if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp"))) {
			return new Result(null, "Pick a PNG, JPEG, GIF or WebP picture.");
		}
		try {
			if (!file.isFile()) {
				return new Result(null, "That file can't be read.");
			}
			if (file.length() > MAX_BYTES) {
				return new Result(null, "Picture is too large (max 8 MB).");
			}
			return new Result(Files.readAllBytes(file.toPath()), null);
		} catch (Throwable t) {
			return new Result(null, "That file can't be read.");
		}
	}

	/** LWJGL tinyfd (shipped with Minecraft up to 26.2). Throws when it is not there. */
	private static String tinyfd() throws Exception {
		Class<?> fd = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
		Class<?> pointerBuffer = Class.forName("org.lwjgl.PointerBuffer");
		java.lang.reflect.Method open = fd.getMethod("tinyfd_openFileDialog", CharSequence.class, CharSequence.class, pointerBuffer,
				CharSequence.class, boolean.class);
		String home = System.getProperty("user.home", "");
		// no filter patterns (they need native memory): the extension is checked after picking
		Object picked = open.invoke(null, "Send a picture", home.isEmpty() ? "" : home + File.separator, null, "Pictures", Boolean.FALSE);
		return picked instanceof String ? (String) picked : null;
	}

	private static String system() throws Exception {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("win")) {
			String ps = "Add-Type -AssemblyName System.Windows.Forms;"
					+ "$d=New-Object Windows.Forms.OpenFileDialog;$d.Title='Send a picture';"
					+ "$d.Filter='Pictures|*.png;*.jpg;*.jpeg;*.gif;*.webp';"
					+ "$f=New-Object Windows.Forms.Form;$f.TopMost=$true;"
					+ "if($d.ShowDialog($f) -eq 'OK'){[Console]::Out.Write($d.FileName)}";
			return run("powershell", "-NoProfile", "-STA", "-NonInteractive", "-Command", ps);
		}
		if (os.contains("mac")) {
			return run("osascript", "-e", "POSIX path of (choose file of type {\"public.image\"} with prompt \"Send a picture\")");
		}
		try {
			return run("zenity", "--file-selection", "--title=Send a picture", "--file-filter=Pictures | *.png *.jpg *.jpeg *.gif *.webp");
		} catch (java.io.IOException noZenity) {
			return run("kdialog", "--title", "Send a picture", "--getopenfilename", System.getProperty("user.home", "."),
					"image/png image/jpeg image/gif image/webp");
		}
	}

	/** Runs a dialog program and returns what it printed (the chosen path), or null when cancelled. */
	private static String run(String... command) throws Exception {
		Process pr = new ProcessBuilder(command).redirectErrorStream(false).start();
		StringBuilder out = new StringBuilder();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				if (out.length() < 4096) {
					out.append(line);
				}
			}
		}
		if (!pr.waitFor(10, TimeUnit.MINUTES)) {
			pr.destroyForcibly();
			return null;
		}
		return pr.exitValue() == 0 ? out.toString() : null;
	}
}
