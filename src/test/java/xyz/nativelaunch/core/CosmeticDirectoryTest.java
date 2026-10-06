package xyz.nativelaunch.core;

import org.junit.jupiter.api.Test;
import xyz.nativelaunch.cosmetic.CosmeticRef;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CosmeticDirectoryTest {
	static final String M = "aa".repeat(32);
	static final String X = "bb".repeat(32);
	static final String PREMIUM = "0f6e3c1a2b3c4d5e8f9a0b1c2d3e4f50";

	static String snapshot(String k) {
		return "{\"epoch\":1,\"rev\":2,\"full\":true,\"textureBase\":\"https://x/t/\",\"entries\":["
				+ "{\"n\":\"Alice\",\"m\":\"default\",\"s\":null,\"c\":null,\"u\":null,\"r\":1,\"k\":" + k + "},"
				+ "{\"n\":\"Prem\",\"m\":\"default\",\"s\":null,\"c\":null,\"u\":\"" + PREMIUM + "\",\"r\":2,\"k\":" + k + "}]}";
	}

	static UUID offline(String name) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void cosmeticsAloneKeepAPlayerInTheDirectory() {
		SkinDirectory d = new SkinDirectory();
		SkinSync.applySnapshot(d, snapshot("[{\"i\":\"propeller_cap\",\"m\":\"" + M + "\",\"x\":\"" + X + "\"}]"));
		assertEquals(2, d.size());
		List<CosmeticRef> worn = d.cosmetics("alice", offline("Alice"));
		assertEquals(1, worn.size());
		assertEquals("propeller_cap", worn.get(0).id);
		assertEquals(M, worn.get(0).modelHash);
		assertEquals(X, worn.get(0).textureHash);
	}

	@Test
	void premiumUuidsMustBeTheLinkedAccount() {
		SkinDirectory d = new SkinDirectory();
		SkinSync.applySnapshot(d, snapshot("[{\"i\":\"crown\",\"m\":\"" + M + "\",\"x\":\"" + X + "\"}]"));
		UUID linked = UUID.fromString(PREMIUM.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
		assertEquals(1, d.cosmetics("Prem", linked).size());
		assertTrue(d.cosmetics("Prem", UUID.randomUUID()).isEmpty(), "another premium player with that name gets nothing");
		assertTrue(d.cosmetics("Alice", UUID.randomUUID()).isEmpty(), "unlinked entries never apply to premium UUIDs");
	}

	@Test
	void junkCosmeticEntriesAreDropped() {
		SkinDirectory d = new SkinDirectory();
		SkinSync.applySnapshot(d, snapshot("[{\"i\":\"x\",\"m\":\"nothex\",\"x\":\"" + X + "\"},7,{\"m\":\"" + M + "\",\"x\":\"" + X + "\"}]"));
		List<CosmeticRef> worn = d.cosmetics("Alice", offline("Alice"));
		assertEquals(1, worn.size());
		assertEquals(M.substring(0, 12), worn.get(0).id);
		SkinDirectory empty = new SkinDirectory();
		SkinSync.applySnapshot(empty, snapshot("null"));
		assertEquals(0, empty.size(), "no skin, no cape and no cosmetics: not in the directory");
	}

	static final String C = "cc".repeat(32);

	static String withCape(String k) {
		return "{\"epoch\":1,\"rev\":2,\"full\":true,\"textureBase\":\"https://x/t/\",\"entries\":["
				+ "{\"n\":\"Alice\",\"m\":\"default\",\"s\":null,\"c\":\"" + C + "\",\"u\":null,\"r\":1,\"k\":" + k + "},"
				+ "{\"n\":\"Prem\",\"m\":\"default\",\"s\":null,\"c\":null,\"u\":\"" + PREMIUM + "\",\"r\":2,\"hp\":true,\"k\":" + k + "}]}";
	}

	@Test
	void backItemsHideTheCape() {
		SkinDirectory d = new SkinDirectory();
		SkinSync.applySnapshot(d, withCape("[{\"i\":\"angel-wings\",\"s\":\"back\",\"m\":\"" + M + "\",\"x\":\"" + X + "\"}]"));
		assertTrue(d.cosmetics("Alice", offline("Alice")).get(0).isBackItem());
		SkinOverride o = d.find("Alice", offline("Alice"));
		assertNotNull(o);
		assertTrue(o.hideCape);
		assertNull(o.capeUrl, "the Native cloak is not shown under wings");
		// Textures.pack drops a CAPE the profile already had (e.g. a Mojang cape)
		String existing = java.util.Base64.getEncoder().encodeToString("{\"textures\":{\"CAPE\":{\"url\":\"https://textures.minecraft.net/texture/abc\"}}}".getBytes(StandardCharsets.UTF_8));
		String packed = new String(java.util.Base64.getDecoder().decode(Textures.pack(existing, o, offline("Alice"), "Alice")), StandardCharsets.UTF_8);
		assertFalse(packed.contains("CAPE"), packed);
	}

	@Test
	void otherSlotsKeepTheCape() {
		SkinDirectory d = new SkinDirectory();
		SkinSync.applySnapshot(d, withCape("[{\"i\":\"top-hat\",\"s\":\"hats\",\"m\":\"" + M + "\",\"x\":\"" + X + "\"}]"));
		SkinOverride o = d.find("Alice", offline("Alice"));
		assertFalse(o.hideCape);
		assertEquals("https://x/t/" + C, o.capeUrl);
	}
}
