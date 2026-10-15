package xyz.nativelaunch.core;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionTest {
	private static HttpServer server;

	@AfterAll
	static void stop() {
		if (server != null) {
			server.stop(0);
		}
	}

	private static void write(Path dir, String ticket, long mtime) throws Exception {
		Path file = dir.resolve(".native").resolve("session.json");
		Files.createDirectories(file.getParent());
		Files.write(file, ("{\"v\":1,\"ticket\":\"" + ticket + "\",\"account\":{\"name\":\"Steve\"}}").getBytes(StandardCharsets.UTF_8));
		Files.setLastModifiedTime(file, FileTime.fromMillis(mtime));
	}

	private static void await(Session.Status want) throws InterruptedException {
		for (int i = 0; i < 100 && Session.status() != want; i++) {
			Thread.sleep(50);
		}
		assertEquals(want, Session.status());
	}

	@Test
	void expiresOnRejectionAndPicksUpTheLaunchersFreshTicket() throws Exception {
		AtomicReference<String> good = new AtomicReference<String>("nmt1.second.sig");
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/mod/me", exchange -> {
			String auth = exchange.getRequestHeaders().getFirst("Authorization");
			boolean ok = ("Bearer " + good.get()).equals(auth);
			byte[] body = (ok ? "{\"ok\":true,\"account\":{\"id\":\"u1\",\"name\":\"Steve\",\"uuid\":\"x\",\"model\":\"classic\"}}" : "{\"ok\":false}")
					.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(ok ? 200 : 401, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		String api = "http://127.0.0.1:" + server.getAddress().getPort();
		Path dir = Files.createTempDirectory("native-session");
		write(dir, "nmt1.first.sig", 1_000_000L);

		Session.start(dir, api, Handoff.read(dir));
		await(Session.Status.EXPIRED);
		assertNull(Session.ticket(), "a rejected ticket is never sent again");

		write(dir, "nmt1.second.sig", 2_000_000L);
		Session.retry();
		await(Session.Status.SIGNED_IN);
		assertEquals("nmt1.second.sig", Session.ticket());
		assertEquals("Steve", Session.account().name);
	}
}
