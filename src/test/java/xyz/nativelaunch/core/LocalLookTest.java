package xyz.nativelaunch.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Offline accounts: the launcher's local look shows for that offline name only, on this PC. */
class LocalLookTest {
	private static final String SKIN = new String(new char[64]).replace('\0', 'a');

	@Test
	void appliesToTheOfflineNameOnly(@TempDir Path game) throws Exception {
		Files.createDirectories(game.resolve(".native"));
		String json = "{\"v\":1,\"local\":{\"name\":\"Steve_1\",\"skin\":\"" + SKIN + "\",\"cape\":null,\"slim\":true}}";
		Files.write(game.resolve(".native").resolve("launcher.json"), json.getBytes(StandardCharsets.UTF_8));

		SkinDirectory directory = new SkinDirectory();
		directory.setLocal(TextureCache.localLook(game));
		UUID offline = UUID.nameUUIDFromBytes("OfflinePlayer:Steve_1".getBytes(StandardCharsets.UTF_8));

		SkinOverride mine = directory.find("steve_1", offline);
		assertNotNull(mine);
		assertTrue(mine.skinUrl.endsWith("/csl/textures/" + SKIN));
		assertNull(mine.capeUrl);
		assertTrue(mine.slim);

		assertNull(directory.find("Alex", UUID.nameUUIDFromBytes("OfflinePlayer:Alex".getBytes(StandardCharsets.UTF_8))));
		// A real premium player with the same name never gets it.
		assertNull(directory.find("Steve_1", UUID.randomUUID(), true));
	}

	@Test
	void ignoresBadEntries(@TempDir Path game) throws Exception {
		Files.createDirectories(game.resolve(".native"));
		Files.write(game.resolve(".native").resolve("launcher.json"),
				"{\"local\":{\"name\":\"../x\",\"skin\":\"zz\"}}".getBytes(StandardCharsets.UTF_8));
		assertNull(TextureCache.localLook(game));
		assertEquals(null, TextureCache.localLook(game.resolve("missing")));
	}
}
