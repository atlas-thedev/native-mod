package xyz.nativelaunch.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Native+ members are marked in the skin directory ("x": 1) and get the Native+ mark on their name tag. */
class PlusFlagTest {
	private static final String BASE = "https://api.example/csl/textures/";
	private static final String SKIN = "a".repeat(64);

	private static UUID offlineUuid(String name) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
	}

	private static SkinEntry row(String json) {
		return SkinSync.entry(JsonParser.parseString(json).getAsJsonObject());
	}

	@Test
	void theDirectoryRowCarriesTheFlag() {
		assertTrue(row("{\"n\":\"Alice\",\"s\":\"" + SKIN + "\",\"x\":1}").plus);
		assertTrue(row("{\"n\":\"Alice\",\"s\":\"" + SKIN + "\",\"x\":true}").plus);
		assertFalse(row("{\"n\":\"Alice\",\"s\":\"" + SKIN + "\"}").plus);
		assertFalse(row("{\"n\":\"Alice\",\"s\":\"" + SKIN + "\",\"x\":0}").plus);
		assertFalse(row("{\"n\":\"Alice\",\"s\":\"" + SKIN + "\",\"x\":\"nope\"}").plus);
	}

	@Test
	void offlinePlayersMatchByName() {
		SkinDirectory directory = new SkinDirectory();
		directory.replaceAll(1, 1, BASE, Arrays.asList(
				row("{\"n\":\"Alice\",\"s\":\"" + SKIN + "\",\"x\":1}"),
				row("{\"n\":\"Bob\",\"s\":\"" + SKIN + "\"}")));
		assertTrue(directory.plus("aLiCe", offlineUuid("aLiCe")));
		assertFalse(directory.plus("Bob", offlineUuid("Bob")));
		assertFalse(directory.plus("Nobody", offlineUuid("Nobody")));
		assertFalse(directory.plus(null, null));
	}

	@Test
	void aPremiumUuidThatIsNotLinkedNeverBorrowsTheMark() {
		String linked = "11112222333344445555666677778888";
		SkinDirectory directory = new SkinDirectory();
		directory.replaceAll(1, 1, BASE, Arrays.asList(
				row("{\"n\":\"Alice\",\"s\":\"" + SKIN + "\",\"x\":1,\"u\":\"" + linked + "\"}")));
		UUID mine = UUID.fromString("11112222-3333-4444-5555-666677778888");
		UUID someoneElse = UUID.fromString("99999999-3333-4444-5555-666677778888");
		assertTrue(directory.plus("Alice", mine));
		assertFalse(directory.plus("Alice", someoneElse));
	}
}
