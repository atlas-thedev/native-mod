package xyz.nativelaunch.health;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityListTest {
	@Test
	void defaultsHideArmorStands(@TempDir Path dir) {
		EntityList l = EntityList.load(dir.resolve("none.json"));
		assertTrue(l.isBlacklist());
		assertFalse(l.allows("minecraft:armor_stand", null));
		assertTrue(l.allows("minecraft:zombie", null));
	}

	@Test
	void nullFileStillWorks() {
		EntityList l = EntityList.load(null);
		assertTrue(l.allows("minecraft:cow", null));
		l.toggle("minecraft:cow"); // nowhere to save; must not throw
		assertFalse(l.allows("minecraft:cow", null));
	}

	@Test
	void whitelistOnlyAllowsListedAndTreatsAnEmptyListAsEverything(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("h.json");
		Files.write(f, "{\"mode\":\"whitelist\",\"entities\":[\"minecraft:zombie\",\"Notch\"]}".getBytes(StandardCharsets.UTF_8));
		EntityList l = EntityList.load(f);
		assertFalse(l.isBlacklist());
		assertTrue(l.allows("minecraft:zombie", null));
		assertFalse(l.allows("minecraft:cow", null));
		assertTrue(l.allows("minecraft:player", "notch"), "player names match case-insensitively");
		assertFalse(l.allows("minecraft:player", "jeb_"));
		Files.write(f, "{\"mode\":\"whitelist\",\"entities\":[]}".getBytes(StandardCharsets.UTF_8));
		assertTrue(EntityList.load(f).allows("minecraft:cow", null));
	}

	@Test
	void toggleSavesAndReloads(@TempDir Path dir) {
		Path f = dir.resolve("config").resolve("h.json");
		EntityList l = EntityList.load(f);
		assertTrue(l.toggle("minecraft:Creeper"));
		assertFalse(l.allows("minecraft:creeper", null));
		EntityList again = EntityList.load(f);
		assertFalse(again.allows("minecraft:creeper", null));
		assertFalse(again.toggle("minecraft:creeper"));
		assertTrue(EntityList.load(f).allows("minecraft:creeper", null));
	}

	@Test
	void brokenFileFallsBackToDefaults(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("h.json");
		Files.write(f, "not json {".getBytes(StandardCharsets.UTF_8));
		EntityList l = EntityList.load(f);
		assertEquals(1, l.entries().size());
	}
}
