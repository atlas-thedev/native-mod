package dev.noctra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SkinSyncTest {
	private static UUID offline(String name) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void snapshotThenLiveStreamUpdatesTheDirectory() throws Exception {
		final String hashA = "a".repeat(64);
		final String hashB = "b".repeat(64);
		final CountDownLatch streamOpened = new CountDownLatch(1);
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/skins/directory", exchange -> {
			String port = "http://127.0.0.1:" + server.getAddress().getPort();
			byte[] body = ("{\"ok\":true,\"epoch\":7,\"rev\":1,\"full\":true,\"textureBase\":\"" + port + "/csl/textures/\","
					+ "\"entries\":[{\"n\":\"Alice\",\"m\":\"default\",\"s\":\"" + hashA + "\",\"c\":null,\"u\":null,\"t\":1,\"r\":1}]}")
					.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		});
		server.createContext("/v1/skins/stream", exchange -> {
			exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
			exchange.sendResponseHeaders(200, 0);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write("event: hello\ndata: {\"epoch\":7,\"rev\":1,\"resync\":false}\n\n".getBytes(StandardCharsets.UTF_8));
				out.flush();
				streamOpened.countDown();
				Thread.sleep(300);
				out.write((": ping\n\nevent: skin\nid: 2\ndata: {\"n\":\"Bob\",\"m\":\"slim\",\"s\":\"" + hashB + "\",\"c\":null,\"u\":null,\"t\":2,\"r\":2}\n\n")
						.getBytes(StandardCharsets.UTF_8));
				out.flush();
				Thread.sleep(1500);
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
		});
		server.start();
		SkinDirectory directory = new SkinDirectory();
		SkinSync sync = new SkinSync(directory, "http://127.0.0.1:" + server.getAddress().getPort());
		try {
			sync.start();
			sync.awaitFirstAttempt(5000);
			SkinOverride alice = directory.find("Alice", offline("Alice"));
			assertNotNull(alice, "the snapshot is loaded before the first lookup");
			assertTrue(alice.skinUrl.endsWith("/csl/textures/" + hashA));

			assertTrue(streamOpened.await(5, TimeUnit.SECONDS));
			long end = System.currentTimeMillis() + 5000;
			while (directory.find("Bob", offline("Bob")) == null && System.currentTimeMillis() < end) {
				Thread.sleep(50);
			}
			SkinOverride bob = directory.find("Bob", offline("Bob"));
			assertNotNull(bob, "a live skin event reaches the directory");
			assertTrue(bob.slim);
			assertEquals(2, directory.revision());
			assertNull(directory.find("Carol", offline("Carol")));
		} finally {
			sync.stop();
			server.stop(0);
		}
	}

	@Test
	void anUnreachableServerNeverBlocksOrThrows() {
		SkinDirectory directory = new SkinDirectory();
		SkinSync sync = new SkinSync(directory, "http://127.0.0.1:1");
		sync.start();
		long started = System.currentTimeMillis();
		sync.awaitFirstAttempt(5000);
		assertTrue(System.currentTimeMillis() - started < 5000, "first attempt completes (as a failure) quickly");
		assertNull(directory.find("Alice", offline("Alice")));
		sync.stop();
	}
}
