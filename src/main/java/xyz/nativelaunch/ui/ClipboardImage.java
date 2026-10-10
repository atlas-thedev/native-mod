package xyz.nativelaunch.ui;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/** Reads a picture from the system clipboard (GLFW only exposes text). Blocking: call from a worker thread. */
public final class ClipboardImage {
	private ClipboardImage() {
	}

	/** PNG/JPEG/GIF bytes of the clipboard picture (or of a copied image file), null when there is none. */
	public static byte[] read() {
		try {
			String os = System.getProperty("os.name", "").toLowerCase();
			if (os.contains("win")) {
				return windows();
			}
			if (os.contains("mac")) {
				return mac();
			}
			return linux();
		} catch (Throwable t) {
			return null;
		}
	}

	public static String mime(byte[] b) {
		if (b == null || b.length < 4) {
			return null;
		}
		if ((b[0] & 0xFF) == 0x89 && b[1] == 'P') {
			return "image/png";
		}
		if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) {
			return "image/jpeg";
		}
		if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F') {
			return "image/gif";
		}
		if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F') {
			return "image/webp";
		}
		return null;
	}

	private static byte[] windows() throws Exception {
		File out = File.createTempFile("native-clip", ".png");
		try {
			String ps = "Add-Type -AssemblyName System.Windows.Forms,System.Drawing;"
					+ "$p='" + out.getAbsolutePath().replace("'", "''") + "';"
					+ "if([Windows.Forms.Clipboard]::ContainsImage()){[Windows.Forms.Clipboard]::GetImage().Save($p,[Drawing.Imaging.ImageFormat]::Png);exit 0}"
					+ "$f=[Windows.Forms.Clipboard]::GetFileDropList();"
					+ "if($f.Count -gt 0 -and $f[0] -match '\\.(png|jpe?g|gif|webp)$'){Copy-Item -LiteralPath $f[0] -Destination $p -Force;exit 0}"
					+ "exit 1";
			Process pr = new ProcessBuilder("powershell", "-NoProfile", "-STA", "-NonInteractive", "-Command", ps).redirectErrorStream(true).start();
			drain(pr);
			if (!pr.waitFor(8, TimeUnit.SECONDS)) {
				pr.destroyForcibly();
				return null;
			}
			return pr.exitValue() == 0 && out.length() > 0 ? Files.readAllBytes(out.toPath()) : null;
		} finally {
			out.delete();
		}
	}

	private static byte[] mac() throws Exception {
		File out = File.createTempFile("native-clip", ".png");
		try {
			String script = "try\nset d to the clipboard as \u00abclass PNGf\u00bb\nset f to open for access POSIX file \"" + out.getAbsolutePath()
					+ "\" with write permission\nset eof f to 0\nwrite d to f\nclose access f\non error\nerror number 1\nend try";
			Process pr = new ProcessBuilder("osascript", "-e", script).redirectErrorStream(true).start();
			drain(pr);
			if (!pr.waitFor(8, TimeUnit.SECONDS)) {
				pr.destroyForcibly();
				return null;
			}
			return pr.exitValue() == 0 && out.length() > 0 ? Files.readAllBytes(out.toPath()) : null;
		} finally {
			out.delete();
		}
	}

	private static byte[] linux() throws Exception {
		String[][] attempts = {
				{"wl-paste", "--no-newline", "--type", "image/png"},
				{"xclip", "-selection", "clipboard", "-t", "image/png", "-o"}};
		for (String[] cmd : attempts) {
			try {
				Process pr = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.to(new File("/dev/null"))).start();
				ByteArrayOutputStream bo = new ByteArrayOutputStream();
				try (InputStream in = pr.getInputStream()) {
					byte[] buf = new byte[65536];
					int n;
					while ((n = in.read(buf)) > 0 && bo.size() < 9_000_000) {
						bo.write(buf, 0, n);
					}
				}
				if (!pr.waitFor(6, TimeUnit.SECONDS)) {
					pr.destroyForcibly();
					continue;
				}
				byte[] b = bo.toByteArray();
				if (pr.exitValue() == 0 && mime(b) != null) {
					return b;
				}
			} catch (Exception ignored) {
				// tool not installed
			}
		}
		return null;
	}

	private static void drain(Process pr) {
		new Thread(() -> {
			try (InputStream in = pr.getInputStream()) {
				byte[] buf = new byte[4096];
				while (in.read(buf) > 0) {
					// discard
				}
			} catch (Exception ignored) {
				// done
			}
		}).start();
	}
}
