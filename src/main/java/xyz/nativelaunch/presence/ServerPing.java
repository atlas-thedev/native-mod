package xyz.nativelaunch.presence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Hashtable;

/**
 * Minecraft server-list ping (the same request the multiplayer screen makes): player count,
 * slots and the server icon. Resolves _minecraft._tcp SRV records like the game does.
 */
final class ServerPing {
	static final class Result {
		int online = -1;
		int max = -1;
		byte[] icon;
	}

	private ServerPing() {
	}

	/** host[:port] as typed in the server list ("[::1]:25565" style IPv6 too). */
	static String[] split(String address) {
		String a = address == null ? "" : address.trim();
		String host = a;
		String port = null;
		if (a.startsWith("[")) {
			int end = a.indexOf(']');
			if (end > 0) {
				host = a.substring(1, end);
				if (a.length() > end + 2 && a.charAt(end + 1) == ':') {
					port = a.substring(end + 2);
				}
			}
		} else if (a.indexOf(':') == a.lastIndexOf(':') && a.indexOf(':') > 0) {
			host = a.substring(0, a.indexOf(':'));
			port = a.substring(a.indexOf(':') + 1);
		}
		return new String[] {host, port};
	}

	static Result ping(String address, int timeoutMs) throws IOException {
		String[] parts = split(address);
		String host = parts[0];
		int port = 25565;
		if (parts[1] != null) {
			try {
				port = Integer.parseInt(parts[1].trim());
			} catch (NumberFormatException ignored) {
				// default port
			}
		} else {
			String[] srv = srv(host);
			if (srv != null) {
				host = srv[0];
				port = Integer.parseInt(srv[1]);
			}
		}
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(host, port), timeoutMs);
			socket.setSoTimeout(timeoutMs);
			OutputStream out = socket.getOutputStream();
			ByteArrayOutputStream handshake = new ByteArrayOutputStream();
			varInt(handshake, 0x00);
			varInt(handshake, -1); // any protocol: status works on every version
			string(handshake, parts[0]);
			handshake.write((port >> 8) & 0xFF);
			handshake.write(port & 0xFF);
			varInt(handshake, 1);
			packet(out, handshake.toByteArray());
			packet(out, new byte[] {0x00});
			out.flush();

			DataInputStream in = new DataInputStream(socket.getInputStream());
			int length = readVarInt(in);
			if (length <= 0 || length > 2 * 1024 * 1024) {
				throw new IOException("Bad status length");
			}
			if (readVarInt(in) != 0x00) {
				throw new IOException("Unexpected status packet");
			}
			int jsonLength = readVarInt(in);
			if (jsonLength <= 0 || jsonLength > length) {
				throw new IOException("Bad status body");
			}
			byte[] json = new byte[jsonLength];
			in.readFully(json);
			return parse(new String(json, StandardCharsets.UTF_8));
		}
	}

	static Result parse(String json) {
		Result r = new Result();
		JsonElement root = new JsonParser().parse(json);
		if (!root.isJsonObject()) {
			return r;
		}
		JsonObject o = root.getAsJsonObject();
		if (o.has("players") && o.get("players").isJsonObject()) {
			JsonObject players = o.getAsJsonObject("players");
			r.online = number(players, "online");
			r.max = number(players, "max");
		}
		if (o.has("favicon") && o.get("favicon").isJsonPrimitive()) {
			String favicon = o.get("favicon").getAsString();
			int comma = favicon.indexOf(',');
			if (favicon.startsWith("data:image/png;base64,") && comma > 0) {
				try {
					r.icon = Base64.getMimeDecoder().decode(favicon.substring(comma + 1));
				} catch (IllegalArgumentException ignored) {
					// no icon
				}
			}
		}
		return r;
	}

	private static int number(JsonObject o, String key) {
		try {
			return o.has(key) ? o.get(key).getAsInt() : -1;
		} catch (RuntimeException e) {
			return -1;
		}
	}

	private static String[] srv(String host) {
		if (host.matches("[0-9.]+") || host.contains(":") || "localhost".equalsIgnoreCase(host)) {
			return null;
		}
		try {
			Hashtable<String, String> env = new Hashtable<String, String>();
			env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
			env.put("java.naming.provider.url", "dns:");
			env.put("com.sun.jndi.dns.timeout.retries", "1");
			javax.naming.directory.DirContext ctx = new javax.naming.directory.InitialDirContext(env);
			try {
				javax.naming.directory.Attribute attr = ctx.getAttributes("_minecraft._tcp." + host, new String[] {"SRV"}).get("srv");
				if (attr == null || attr.size() == 0) {
					return null;
				}
				String[] f = attr.get(0).toString().split(" ", 4);
				if (f.length < 4) {
					return null;
				}
				String target = f[3].endsWith(".") ? f[3].substring(0, f[3].length() - 1) : f[3];
				return new String[] {target, f[2]};
			} finally {
				ctx.close();
			}
		} catch (Throwable t) {
			return null;
		}
	}

	private static void packet(OutputStream out, byte[] body) throws IOException {
		ByteArrayOutputStream frame = new ByteArrayOutputStream();
		varInt(frame, body.length);
		frame.write(body);
		out.write(frame.toByteArray());
	}

	private static void string(ByteArrayOutputStream out, String value) throws IOException {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		varInt(out, bytes.length);
		out.write(bytes);
	}

	static void varInt(ByteArrayOutputStream out, int value) {
		int v = value;
		while (true) {
			if ((v & ~0x7F) == 0) {
				out.write(v);
				return;
			}
			out.write((v & 0x7F) | 0x80);
			v >>>= 7;
		}
	}

	static int readVarInt(DataInputStream in) throws IOException {
		int value = 0;
		for (int i = 0; i < 5; i++) {
			int b = in.readUnsignedByte();
			value |= (b & 0x7F) << (7 * i);
			if ((b & 0x80) == 0) {
				return value;
			}
		}
		throw new IOException("VarInt too long");
	}
}
