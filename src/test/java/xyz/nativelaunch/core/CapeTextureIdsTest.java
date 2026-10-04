package xyz.nativelaunch.core;

import com.google.common.hash.Hashing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapeTextureIdsTest {
	@Test
	@SuppressWarnings("deprecation")
	void hashesLikeTheGamesSkinManager() {
		for (String value : new String[] {"", "abc", "4f1d0c7e9b2a", "ünïcödé-ţexture", "a".repeat(200)}) {
			assertEquals(Hashing.sha1().hashUnencodedChars(value).toString(), CapeTextureIds.sha1OfChars(value));
		}
	}

	@Test
	void textureHashIsTheFileNameWithoutExtension() {
		assertEquals("deadbeef", CapeTextureIds.textureHash("https://api.example/t/deadbeef"));
		assertEquals("deadbeef", CapeTextureIds.textureHash("https://api.example/t/deadbeef.png"));
		assertEquals("deadbeef", CapeTextureIds.textureHash("deadbeef"));
	}

	@Test
	void matchesEveryPrefixTheGameUses() {
		String sha = CapeTextureIds.sha1OfChars("deadbeef");
		assertTrue(CapeTextureIds.matches("minecraft:capes/" + sha, sha));
		assertTrue(CapeTextureIds.matches("minecraft:skins/" + sha, sha));
		assertTrue(CapeTextureIds.matches("minecraft:elytra/" + sha, sha));
		assertFalse(CapeTextureIds.matches("minecraft:textures/" + sha, sha));
		assertFalse(CapeTextureIds.matches("minecraft:capes/" + sha + "x", sha));
		assertFalse(CapeTextureIds.matches(null, sha));
	}
}
