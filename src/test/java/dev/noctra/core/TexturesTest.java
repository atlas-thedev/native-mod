package dev.noctra.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TexturesTest {
	private static final UUID ID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

	private static String mojang(String json) {
		return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void packedTexturesRoundTrip() {
		String packed = Textures.pack(null, new SkinOverride("https://x/skin", "https://x/cape", true), ID, "Alice");
		Textures.Parsed parsed = Textures.parse(packed);
		assertNotNull(parsed);
		assertEquals("https://x/skin", parsed.skinUrl);
		assertEquals("https://x/cape", parsed.capeUrl);
		assertTrue(parsed.slim);
		assertNull(parsed.elytraUrl);
	}

	@Test
	void mojangTexturesAreKeptWhereNoctraHasNone() {
		String vanilla = mojang("{\"profileName\":\"Alice\",\"textures\":{"
				+ "\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/abc\"},"
				+ "\"CAPE\":{\"url\":\"http://textures.minecraft.net/texture/def\"}}}");
		// Noctra only has a skin: Mojang's cape stays
		Textures.Parsed parsed = Textures.parse(Textures.pack(vanilla, new SkinOverride("https://x/skin", null, false), ID, "Alice"));
		assertEquals("https://x/skin", parsed.skinUrl);
		assertEquals("http://textures.minecraft.net/texture/def", parsed.capeUrl);
		assertFalse(parsed.slim);
	}

	@Test
	void onlyOurOwnPropertiesAreRecognised() {
		assertNull(Textures.parse(null));
		assertNull(Textures.parse("not base64 at all!!"));
		assertNull(Textures.parse(mojang("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/abc\"}}}")));
		assertNull(Textures.parse(mojang("[1,2,3]")));
	}

	@Test
	void corruptExistingPropertyIsIgnored() {
		Textures.Parsed parsed = Textures.parse(Textures.pack("%%%", new SkinOverride("https://x/skin", null, false), ID, "Alice"));
		assertEquals("https://x/skin", parsed.skinUrl);
	}

	@Test
	void handoffRequiresATicketAndReadsTheAccount(@TempDir Path gameDir) throws Exception {
		assertNull(Handoff.read(gameDir), "no file means guest mode");
		Path folder = Files.createDirectories(gameDir.resolve(".noctra"));
		Files.write(folder.resolve("session.json"), "{\"v\":1,\"ticket\":\"nmt1.abc.def\",\"api\":\"https://api.example\",\"account\":{\"name\":\"Alice\"}}".getBytes(StandardCharsets.UTF_8));
		Handoff handoff = Handoff.read(gameDir);
		assertNotNull(handoff);
		assertEquals("nmt1.abc.def", handoff.ticket);
		assertEquals("https://api.example", handoff.api);
		assertEquals("Alice", handoff.accountName);

		Files.write(folder.resolve("session.json"), "{\"ticket\":\"some-real-session-token\"}".getBytes(StandardCharsets.UTF_8));
		assertNull(Handoff.read(gameDir), "a full session token is never accepted");
		Files.write(folder.resolve("session.json"), "garbage".getBytes(StandardCharsets.UTF_8));
		assertNull(Handoff.read(gameDir));
	}

	@Test
	void profileAccessReadsBothClassAndRecordShapes() {
		class OldProfile {
			public String getName() {
				return "Old";
			}

			public UUID getId() {
				return ID;
			}
		}
		record NewProfile(String name, UUID id) {
		}
		assertEquals("Old", ProfileAccess.name(new OldProfile()));
		assertEquals(ID, ProfileAccess.id(new OldProfile()));
		assertEquals("New", ProfileAccess.name(new NewProfile("New", ID)));
		assertEquals(ID, ProfileAccess.id(new NewProfile("New", ID)));
		assertNull(ProfileAccess.name(null));
	}
}
