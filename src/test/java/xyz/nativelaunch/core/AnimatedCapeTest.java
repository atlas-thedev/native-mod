package xyz.nativelaunch.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnimatedCapeTest {
	private static final String BASE = "https://api.example/csl/textures/";
	private static final String SKIN = "a".repeat(64);
	private static final String STILL = "b".repeat(64);
	private static final String STRIP = "c".repeat(64);

	private static JsonObject json(String s) {
		return new JsonParser().parse(s).getAsJsonObject();
	}

	@Test
	void directoryEntryReadsTheAnimation() {
		SkinEntry e = SkinSync.entry(json("{\"n\":\"Alice\",\"m\":\"default\",\"s\":\"" + SKIN + "\",\"c\":\"" + STILL
				+ "\",\"a\":{\"h\":\"" + STRIP + "\",\"f\":26,\"p\":12},\"u\":null,\"t\":1,\"r\":4}"));
		assertNotNull(e);
		assertTrue(e.hasAnimatedCape());
		assertEquals(STILL, e.capeHash, "the still first frame stays the normal cape");
		assertEquals(STRIP, e.capeStripHash);
		assertEquals(26, e.capeFrames);
		assertEquals(12, e.capeFps);
		assertEquals(4, e.revision);
	}

	@Test
	void missingOrBrokenAnimationFallsBackToAStillCape() {
		String head = "{\"n\":\"Bob\",\"s\":\"" + SKIN + "\",\"c\":\"" + STILL + "\",\"r\":1";
		for (String tail : new String[] {"}", ",\"a\":null}", ",\"a\":\"oops\"}", ",\"a\":{\"h\":\"" + STRIP + "\",\"f\":\"x\",\"p\":12}}",
				",\"a\":{\"h\":\"" + STRIP + "\",\"f\":1,\"p\":12}}", ",\"a\":{\"f\":26,\"p\":12}}"}) {
			SkinEntry e = SkinSync.entry(json(head + tail));
			assertNotNull(e, tail);
			assertFalse(e.hasAnimatedCape(), tail);
			assertEquals(STILL, e.capeHash, tail);
			assertEquals(SKIN, e.skinHash, tail);
		}
	}

	@Test
	void animationWithoutAStillCapeIsDropped() {
		SkinEntry e = SkinSync.entry(json("{\"n\":\"Cat\",\"s\":\"" + SKIN + "\",\"c\":null,\"a\":{\"h\":\"" + STRIP + "\",\"f\":26,\"p\":12},\"r\":1}"));
		assertFalse(e.hasAnimatedCape());
	}

	@Test
	void overrideAndPackedTexturesCarryTheStrip() {
		SkinDirectory d = new SkinDirectory();
		d.replaceAll(1, 1, BASE, Collections.singletonList(new SkinEntry("Alice", false, SKIN, STILL, null, 1, STRIP, 26, 12)));
		UUID offline = UUID.nameUUIDFromBytes("OfflinePlayer:Alice".getBytes(StandardCharsets.UTF_8));
		SkinOverride o = d.find("Alice", offline);
		assertNotNull(o);
		assertEquals(BASE + STILL, o.capeUrl);
		assertEquals(BASE + STRIP, o.capeStripUrl);

		Textures.Parsed p = Textures.parse(Textures.pack(null, o, offline, "Alice"));
		assertEquals(BASE + STILL, p.capeUrl);
		assertEquals(BASE + STRIP, p.capeStripUrl);
		assertEquals(26, p.capeFrames);
		assertEquals(12, p.capeFps);
	}

	@Test
	void stillCapesHaveNoAnimationMetadata() {
		Textures.Parsed p = Textures.parse(Textures.pack(null, new SkinOverride(BASE + SKIN, BASE + STILL, false), null, "Alice"));
		assertEquals(BASE + STILL, p.capeUrl);
		assertNull(p.capeStripUrl);
		assertEquals(0, p.capeFrames);
	}
}
