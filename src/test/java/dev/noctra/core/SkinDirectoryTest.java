package dev.noctra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SkinDirectoryTest {
	private static final String BASE = "https://api.example/csl/textures/";
	private static final String SKIN = "a".repeat(64);
	private static final String CAPE = "b".repeat(64);

	private static UUID offlineUuid(String name) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
	}

	private SkinDirectory directory(SkinEntry... entries) {
		SkinDirectory directory = new SkinDirectory();
		directory.replaceAll(1, 1, BASE, Arrays.asList(entries));
		return directory;
	}

	@Test
	void offlinePlayersMatchByNameCaseInsensitively() {
		SkinDirectory directory = directory(new SkinEntry("Alice", true, SKIN, CAPE, null, 1));
		SkinOverride found = directory.find("aLiCe", offlineUuid("aLiCe"));
		assertNotNull(found);
		assertEquals(BASE + SKIN, found.skinUrl);
		assertEquals(BASE + CAPE, found.capeUrl);
		assertTrue(found.slim);
	}

	@Test
	void premiumPlayersOnlyMatchTheirLinkedUuid() {
		UUID premium = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
		SkinDirectory unlinked = directory(new SkinEntry("Notch", false, SKIN, null, null, 1));
		assertNull(unlinked.find("Notch", premium), "an unlinked name must not hijack a real player");

		SkinDirectory linked = directory(new SkinEntry("Notch", false, SKIN, null, "069a79f444e94726a5befca90e38aaf5", 1));
		assertNotNull(linked.find("Notch", premium));
		assertNull(linked.find("Notch", UUID.fromString("11111111-2222-4333-8444-555555555555")));
		// the linked account still shows when playing offline
		assertNotNull(linked.find("Notch", offlineUuid("Notch")));
	}

	@Test
	void unknownPlayersAndEmptyDirectoriesGiveNothing() {
		assertNull(directory().find("Nobody", offlineUuid("Nobody")));
		assertNull(new SkinDirectory().find("Alice", offlineUuid("Alice")), "no texture base yet");
		assertNull(directory(new SkinEntry("Alice", false, SKIN, null, null, 1)).find(null, null));
	}

	@Test
	void deltasAddReplaceAndRemove() {
		SkinDirectory directory = directory(new SkinEntry("Alice", false, SKIN, null, null, 1));
		directory.apply(new SkinEntry("Alice", false, null, CAPE, null, 2), 2);
		SkinOverride changed = directory.find("Alice", offlineUuid("Alice"));
		assertNull(changed.skinUrl);
		assertEquals(BASE + CAPE, changed.capeUrl);
		assertEquals(2, directory.revision());

		directory.apply(new SkinEntry("Alice", false, null, null, null, 3), 3);
		assertNull(directory.find("Alice", offlineUuid("Alice")));
		assertEquals(0, directory.size());
	}

	@Test
	void listenersHearAboutChangesAndCannotBreakTheSync() {
		SkinDirectory directory = directory();
		final String[] seen = new String[1];
		directory.onChange(e -> {
			throw new IllegalStateException("bad listener");
		});
		directory.onChange(e -> seen[0] = e.name);
		directory.apply(new SkinEntry("Bob", false, SKIN, null, null, 5), 5);
		assertEquals("Bob", seen[0]);
	}

	@Test
	void snapshotsParseFullAndDelta() {
		SkinDirectory directory = new SkinDirectory();
		SkinSync.applySnapshot(directory, "{\"ok\":true,\"epoch\":42,\"rev\":3,\"full\":true,\"textureBase\":\"" + BASE
				+ "\",\"entries\":[{\"n\":\"Alice\",\"m\":\"slim\",\"s\":\"" + SKIN + "\",\"c\":null,\"u\":null,\"t\":1,\"r\":0}]}");
		assertEquals(42, directory.epoch());
		assertEquals(3, directory.revision());
		assertTrue(directory.find("alice", offlineUuid("alice")).slim);

		SkinSync.applySnapshot(directory, "{\"ok\":true,\"epoch\":42,\"rev\":4,\"full\":false,\"textureBase\":\"" + BASE
				+ "\",\"entries\":[{\"n\":\"Alice\",\"m\":\"default\",\"s\":null,\"c\":null,\"u\":null,\"t\":2,\"r\":4}]}");
		assertEquals(4, directory.revision());
		assertNull(directory.find("alice", offlineUuid("alice")));
	}

	@Test
	void apiAddressMustBeHttpsOrLoopback() {
		assertEquals(NoctraState.DEFAULT_API, NoctraState.chooseApi(null, null));
		assertEquals("https://example.org", NoctraState.chooseApi(null, "https://example.org/"));
		assertEquals("http://127.0.0.1:3418", NoctraState.chooseApi("http://127.0.0.1:3418", "https://example.org"));
		assertEquals(NoctraState.DEFAULT_API, NoctraState.chooseApi("http://evil.example", "ftp://x"));
		assertFalse(NoctraState.chooseApi("http://evil.example", null).contains("evil"));
	}
}
