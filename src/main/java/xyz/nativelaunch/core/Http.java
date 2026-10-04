package xyz.nativelaunch.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/** Minimal HTTP helper (Java 8 compatible; Minecraft 1.16 still runs on Java 8). */
public final class Http {
	static final String USER_AGENT = "NativeMod/" + Version.MOD + " (Minecraft)";

	private Http() {
	}

	static HttpURLConnection open(String url, String bearer, int connectTimeoutMs, int readTimeoutMs, String accept) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		connection.setConnectTimeout(connectTimeoutMs);
		connection.setReadTimeout(readTimeoutMs);
		connection.setRequestProperty("User-Agent", USER_AGENT);
		connection.setRequestProperty("Accept", accept);
		if (bearer != null && !bearer.isEmpty()) {
			connection.setRequestProperty("Authorization", "Bearer " + bearer);
		}
		return connection;
	}

	/** GET a JSON document. Throws on any non-2xx status. */
	static String getJson(String url, String bearer) throws IOException {
		HttpURLConnection connection = open(url, bearer, 8000, 15000, "application/json");
		connection.setRequestProperty("Accept-Encoding", "gzip");
		try {
			int status = connection.getResponseCode();
			if (status / 100 != 2) {
				throw new IOException("HTTP " + status + " from " + url);
			}
			InputStream in = connection.getInputStream();
			if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
				in = new GZIPInputStream(in);
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int read;
			long total = 0;
			while ((read = in.read(buffer)) != -1) {
				total += read;
				if (total > 64L * 1024 * 1024) {
					throw new IOException("Response too large");
				}
				out.write(buffer, 0, read);
			}
			return new String(out.toByteArray(), StandardCharsets.UTF_8);
		} finally {
			connection.disconnect();
		}
	}

	/** GET raw bytes (a texture). Throws on any non-2xx status or when the body exceeds maxBytes. */
	public static byte[] getBytes(String url, int maxBytes) throws IOException {
		HttpURLConnection connection = open(url, null, 8000, 20000, "image/png,*/*");
		try {
			int status = connection.getResponseCode();
			if (status / 100 != 2) {
				throw new IOException("HTTP " + status + " from " + url);
			}
			InputStream in = connection.getInputStream();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int read;
			long total = 0;
			while ((read = in.read(buffer)) != -1) {
				total += read;
				if (total > maxBytes) {
					throw new IOException("Texture too large");
				}
				out.write(buffer, 0, read);
			}
			return out.toByteArray();
		} finally {
			connection.disconnect();
		}
	}
}
