package xyz.nativelaunch.presence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PresenceTest {
	@Test
	void splitsAddresses() {
		assertArrayEquals(new String[] {"mc.hypixel.net", null}, ServerPing.split("mc.hypixel.net"));
		assertArrayEquals(new String[] {"play.example.com", "25570"}, ServerPing.split("play.example.com:25570"));
		assertArrayEquals(new String[] {"::1", "25565"}, ServerPing.split("[::1]:25565"));
		assertEquals("mc.hypixel.net", PresenceService.normalizeAddress("MC.Hypixel.net:25565"));
		assertEquals("play.example.com:25570", PresenceService.normalizeAddress("play.example.com:25570"));
	}

	@Test
	void hidesPrivateAddresses() {
		assertTrue(PresenceService.isPublicAddress("mc.hypixel.net"));
		assertTrue(PresenceService.isPublicAddress("51.75.10.20:25565"));
		assertFalse(PresenceService.isPublicAddress("localhost"));
		assertFalse(PresenceService.isPublicAddress("127.0.0.1:25565"));
		assertFalse(PresenceService.isPublicAddress("192.168.1.20"));
		assertFalse(PresenceService.isPublicAddress("10.0.0.5:25566"));
		assertFalse(PresenceService.isPublicAddress("myserver"));
		assertFalse(PresenceService.isPublicAddress(null));
	}

	@Test
	void labelsServers() {
		GameProbe.Snapshot s = new GameProbe.Snapshot();
		s.address = "mc.hypixel.net";
		s.serverName = "Minecraft Server";
		assertEquals("Hypixel", PresenceService.serverLabel(s));
		s.address = "play.cosmicmc.org";
		assertEquals("Cosmicmc", PresenceService.serverLabel(s));
		s.serverName = "Cosmic SMP";
		assertEquals("Cosmic SMP", PresenceService.serverLabel(s));
	}

	@Test
	void buildsMultiplayerActivity(@TempDir Path game) {
		PresenceService service = new PresenceService(game, "https://api.nativelaunch.xyz", null, true);
		GameProbe.Snapshot s = new GameProbe.Snapshot();
		s.kind = GameProbe.Kind.MULTIPLAYER;
		s.address = "mc.hypixel.net";
		s.tabPlayers = 12;
		JsonObject a = service.activity(s);
		assertEquals("Playing on Hypixel", a.get("details").getAsString());
		assertEquals("mc.hypixel.net", a.get("state").getAsString());
		assertEquals("https://api.mcsrvstat.us/icon/mc.hypixel.net", a.getAsJsonObject("assets").get("large_image").getAsString());
		assertFalse(a.has("party"), "no max players known yet: no party size");

		s.address = "192.168.1.4";
		JsonObject lan = service.activity(s);
		assertEquals("Playing on a private server", lan.get("details").getAsString());
		assertEquals("logo", lan.getAsJsonObject("assets").get("large_image").getAsString());
	}

	@Test
	void buildsSingleplayerActivity(@TempDir Path game) {
		PresenceService service = new PresenceService(game, "https://api.nativelaunch.xyz", null, true);
		GameProbe.Snapshot s = new GameProbe.Snapshot();
		s.kind = GameProbe.Kind.SINGLEPLAYER;
		s.worldName = "Survival";
		s.published = true;
		JsonObject a = service.activity(s);
		assertEquals("Playing Singleplayer", a.get("details").getAsString());
		assertEquals("Survival · Open to LAN", a.get("state").getAsString());
	}

	@Test
	void readsTheDiscordSetting(@TempDir Path game) throws Exception {
		assertTrue(PresenceService.discordSetting(game));
		Files.createDirectories(game.resolve(".native"));
		Files.write(game.resolve(".native/launcher.json"), "{\"v\":1,\"presence\":{\"discord\":false}}".getBytes(StandardCharsets.UTF_8));
		assertFalse(PresenceService.discordSetting(game));
	}

	@Test
	void pingsAServer() throws Exception {
		byte[] icon = new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
		String json = "{\"version\":{\"name\":\"1.21\",\"protocol\":767},\"players\":{\"max\":100,\"online\":42},\"favicon\":\"data:image/png;base64," + Base64.getEncoder().encodeToString(icon) + "\"}";
		try (ServerSocket server = new ServerSocket(0)) {
			Thread t = new Thread(() -> {
				try (Socket c = server.accept()) {
					DataInputStream in = new DataInputStream(c.getInputStream());
					int len = ServerPing.readVarInt(in);
					in.readFully(new byte[len]);
					len = ServerPing.readVarInt(in);
					in.readFully(new byte[len]);
					ByteArrayOutputStream body = new ByteArrayOutputStream();
					ServerPing.varInt(body, 0);
					byte[] j = json.getBytes(StandardCharsets.UTF_8);
					ServerPing.varInt(body, j.length);
					body.write(j);
					ByteArrayOutputStream frame = new ByteArrayOutputStream();
					ServerPing.varInt(frame, body.size());
					frame.write(body.toByteArray());
					OutputStream out = c.getOutputStream();
					out.write(frame.toByteArray());
					out.flush();
				} catch (Exception ignored) {
				}
			});
			t.start();
			ServerPing.Result r = ServerPing.ping("127.0.0.1:" + server.getLocalPort(), 3000);
			t.join(3000);
			assertEquals(42, r.online);
			assertEquals(100, r.max);
			assertArrayEquals(icon, r.icon);
		}
	}

	@Test
	void talksToDiscord(@TempDir Path dir) throws Exception {
		Path sock = dir.resolve("discord-ipc-0");
		AtomicReference<String> activity = new AtomicReference<>();
		try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
			server.bind(UnixDomainSocketAddress.of(sock));
			Thread t = new Thread(() -> {
				try (SocketChannel c = server.accept()) {
					String hello = readFrame(c);
					assertTrue(hello.contains("\"client_id\":\"" + PresenceService.CLIENT_ID + "\""));
					writeFrame(c, 1, "{\"cmd\":\"DISPATCH\",\"evt\":\"READY\",\"data\":{}}");
					activity.set(readFrame(c));
				} catch (Exception ignored) {
				}
			});
			t.start();
			System.setProperty("native.discord.ipc", sock.toString());
			try {
				DiscordIpc ipc = new DiscordIpc(PresenceService.CLIENT_ID);
				assertTrue(ipc.connect());
				JsonObject a = new JsonObject();
				a.addProperty("details", "Playing on Hypixel");
				ipc.setActivity(a);
				t.join(3000);
				ipc.close();
			} finally {
				System.clearProperty("native.discord.ipc");
			}
		}
		JsonObject sent = new JsonParser().parse(activity.get()).getAsJsonObject();
		assertEquals("SET_ACTIVITY", sent.get("cmd").getAsString());
		assertEquals("Playing on Hypixel", sent.getAsJsonObject("args").getAsJsonObject("activity").get("details").getAsString());
	}

	private static String readFrame(SocketChannel c) throws Exception {
		ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
		while (header.hasRemaining()) c.read(header);
		header.flip();
		header.getInt();
		ByteBuffer body = ByteBuffer.allocate(header.getInt());
		while (body.hasRemaining()) c.read(body);
		return new String(body.array(), StandardCharsets.UTF_8);
	}

	private static void writeFrame(SocketChannel c, int op, String json) throws Exception {
		byte[] b = json.getBytes(StandardCharsets.UTF_8);
		ByteBuffer f = ByteBuffer.allocate(8 + b.length).order(ByteOrder.LITTLE_ENDIAN);
		f.putInt(op).putInt(b.length).put(b);
		f.flip();
		while (f.hasRemaining()) c.write(f);
	}
}
