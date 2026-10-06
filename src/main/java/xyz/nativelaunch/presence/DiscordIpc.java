package xyz.nativelaunch.presence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Talks to the Discord desktop app over its local IPC socket (a named pipe on Windows, a Unix
 * socket elsewhere): handshake, then SET_ACTIVITY. Not thread-safe; the presence thread owns it.
 */
final class DiscordIpc {
	private static final int HANDSHAKE = 0;
	private static final int FRAME = 1;
	private static final int CLOSE = 2;
	private static final int PING = 3;
	private static final int PONG = 4;

	interface Transport {
		void write(byte[] data) throws IOException;

		/** Non-blocking: copies what is waiting (maybe nothing) into buf. -1 when closed. */
		int read(byte[] buf) throws IOException;

		void close();
	}

	private final String clientId;
	private Transport transport;
	private boolean ready;
	private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
	private int nonce;

	DiscordIpc(String clientId) {
		this.clientId = clientId;
	}

	boolean connected() {
		return transport != null && ready;
	}

	/** Tries every Discord socket; true once Discord answered the handshake. */
	boolean connect() {
		close();
		for (int i = 0; i < 10; i++) {
			for (String path : candidates(i)) {
				Transport t = open(path);
				if (t == null) {
					continue;
				}
				transport = t;
				try {
					JsonObject hello = new JsonObject();
					hello.addProperty("v", 1);
					hello.addProperty("client_id", clientId);
					send(HANDSHAKE, hello);
					long until = System.currentTimeMillis() + 4000;
					while (System.currentTimeMillis() < until) {
						pump();
						if (ready) {
							return true;
						}
						if (transport == null) {
							break;
						}
						sleep(50);
					}
				} catch (IOException ignored) {
					// next socket
				}
				close();
			}
		}
		return false;
	}

	/** @param activity null clears the presence */
	void setActivity(JsonObject activity) throws IOException {
		if (!connected()) {
			throw new IOException("Discord is not connected");
		}
		JsonObject args = new JsonObject();
		args.addProperty("pid", pid());
		if (activity != null) {
			args.add("activity", activity);
		}
		JsonObject frame = new JsonObject();
		frame.addProperty("cmd", "SET_ACTIVITY");
		frame.add("args", args);
		frame.addProperty("nonce", "native-" + (++nonce));
		send(FRAME, frame);
	}

	/** Reads anything Discord sent: READY, PING, CLOSE, command replies. */
	void pump() throws IOException {
		if (transport == null) {
			return;
		}
		byte[] buf = new byte[8192];
		int n;
		while ((n = transport.read(buf)) > 0) {
			pending.write(buf, 0, n);
		}
		if (n < 0) {
			close();
			throw new IOException("Discord closed the connection");
		}
		byte[] data = pending.toByteArray();
		int offset = 0;
		while (data.length - offset >= 8) {
			ByteBuffer header = ByteBuffer.wrap(data, offset, 8).order(ByteOrder.LITTLE_ENDIAN);
			int op = header.getInt();
			int len = header.getInt();
			if (len < 0 || len > 1 << 20) {
				close();
				throw new IOException("Bad Discord frame");
			}
			if (data.length - offset - 8 < len) {
				break;
			}
			String body = new String(data, offset + 8, len, StandardCharsets.UTF_8);
			offset += 8 + len;
			handle(op, body);
			if (transport == null) {
				return;
			}
		}
		pending.reset();
		pending.write(data, offset, data.length - offset);
	}

	private void handle(int op, String body) throws IOException {
		if (op == PING) {
			JsonElement payload = new JsonParser().parse(body);
			sendRaw(PONG, payload.toString());
		} else if (op == CLOSE) {
			close();
		} else if (op == FRAME) {
			try {
				JsonObject o = new JsonParser().parse(body).getAsJsonObject();
				if (o.has("evt") && "READY".equals(o.get("evt").getAsString())) {
					ready = true;
				}
			} catch (RuntimeException ignored) {
				// not JSON we care about
			}
		}
	}

	void close() {
		ready = false;
		pending.reset();
		if (transport != null) {
			transport.close();
			transport = null;
		}
	}

	private void send(int op, JsonObject payload) throws IOException {
		sendRaw(op, payload.toString());
	}

	private void sendRaw(int op, String json) throws IOException {
		byte[] body = json.getBytes(StandardCharsets.UTF_8);
		ByteBuffer frame = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
		frame.putInt(op).putInt(body.length).put(body);
		try {
			transport.write(frame.array());
		} catch (IOException | RuntimeException e) {
			close();
			throw e instanceof IOException ? (IOException) e : new IOException(e);
		}
	}

	private static int pid() {
		try {
			String name = ManagementFactory.getRuntimeMXBean().getName();
			return Integer.parseInt(name.substring(0, name.indexOf('@')));
		} catch (RuntimeException e) {
			return 0;
		}
	}

	private static boolean windows() {
		return System.getProperty("os.name", "").toLowerCase().contains("win");
	}

	static List<String> candidates(int index) {
		List<String> out = new ArrayList<String>();
		String override = System.getProperty("native.discord.ipc");
		if (override != null) {
			if (index == 0) {
				out.add(override);
			}
			return out;
		}
		String name = "discord-ipc-" + index;
		if (windows()) {
			out.add("\\\\.\\pipe\\" + name);
			return out;
		}
		String[] bases = {System.getenv("XDG_RUNTIME_DIR"), System.getenv("TMPDIR"), System.getenv("TMP"), System.getenv("TEMP"), "/tmp"};
		String[] subs = {"", "app/com.discordapp.Discord", "snap.discord", ".flatpak/dev.vencord.Vesktop/xdg-run", "app/dev.vencord.Vesktop"};
		for (String base : bases) {
			if (base == null || base.isEmpty()) {
				continue;
			}
			for (String sub : subs) {
				File f = sub.isEmpty() ? new File(base, name) : new File(new File(base, sub), name);
				String p = f.getPath();
				if (!out.contains(p)) {
					out.add(p);
				}
			}
		}
		return out;
	}

	private static Transport open(String path) {
		if (windows() && path.startsWith("\\\\")) {
			try {
				final RandomAccessFile pipe = new RandomAccessFile(path, "rw");
				return new Transport() {
					@Override
					public void write(byte[] data) throws IOException {
						pipe.write(data);
					}

					@Override
					public int read(byte[] buf) throws IOException {
						// A blocking read would also block writes on the same pipe handle, so only
						// read what is already waiting (length() is the pipe's queued byte count).
						long waiting = pipe.length();
						if (waiting <= 0) {
							return 0;
						}
						return pipe.read(buf, 0, (int) Math.min(buf.length, waiting));
					}

					@Override
					public void close() {
						try {
							pipe.close();
						} catch (IOException ignored) {
							// already closed
						}
					}
				};
			} catch (IOException e) {
				return null;
			}
		}
		if (!new File(path).exists()) {
			return null;
		}
		try {
			// Unix sockets need Java 16+ (Minecraft 1.17+); reached reflectively to stay Java 8 bytecode.
			Class<?> addressType = Class.forName("java.net.UnixDomainSocketAddress");
			Object address = addressType.getMethod("of", String.class).invoke(null, path);
			@SuppressWarnings({"unchecked", "rawtypes"})
			Object unix = Enum.valueOf((Class) Class.forName("java.net.StandardProtocolFamily"), "UNIX");
			final SocketChannel channel = (SocketChannel) SocketChannel.class.getMethod("open", ProtocolFamily.class).invoke(null, unix);
			channel.connect((SocketAddress) address);
			channel.configureBlocking(false);
			return new Transport() {
				@Override
				public void write(byte[] data) throws IOException {
					ByteBuffer b = ByteBuffer.wrap(data);
					long until = System.currentTimeMillis() + 3000;
					while (b.hasRemaining()) {
						if (channel.write(b) == 0) {
							if (System.currentTimeMillis() > until) {
								throw new IOException("Discord is not reading");
							}
							sleep(5);
						}
					}
				}

				@Override
				public int read(byte[] buf) throws IOException {
					return channel.read(ByteBuffer.wrap(buf));
				}

				@Override
				public void close() {
					try {
						channel.close();
					} catch (IOException ignored) {
						// already closed
					}
				}
			};
		} catch (Throwable t) {
			return null;
		}
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
