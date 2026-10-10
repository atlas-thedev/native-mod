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

	/** Largest JSON document accepted (after gzip). The skin directory is far below this. */
	static final long MAX_JSON_BYTES = 16L * 1024 * 1024;
	/** Largest reply accepted from a POST (upload acknowledgements are tiny). */
	static final int MAX_POST_REPLY_BYTES = 64 * 1024;

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
	public static String getJson(String url, String bearer) throws IOException {
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
				if (total > MAX_JSON_BYTES) {
					throw new IOException("Response too large");
				}
				out.write(buffer, 0, read);
			}
			return new String(out.toByteArray(), StandardCharsets.UTF_8);
		} finally {
			connection.disconnect();
		}
	}

	/** POST a binary body, read a JSON reply. Throws on any non-2xx status. */
	public static String postBytes(String url, String bearer, byte[] body, String contentType) throws IOException {
		HttpURLConnection connection = open(url, bearer, 8000, 15000, "application/json");
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type", contentType);
		connection.setFixedLengthStreamingMode(body.length);
		try {
			java.io.OutputStream os = connection.getOutputStream();
			try {
				os.write(body);
			} finally {
				os.close();
			}
			int status = connection.getResponseCode();
			if (status / 100 != 2) {
				throw new IOException("HTTP " + status + " from " + url);
			}
			InputStream in = connection.getInputStream();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int read;
			while ((read = in.read(buffer)) != -1) {
				// Check before writing, so the limit is the real limit (not limit + one buffer).
				if ((long) out.size() + read > MAX_POST_REPLY_BYTES) {
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
