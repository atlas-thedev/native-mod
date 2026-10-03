package dev.noctra.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimGateTest {
	@Test
	void animatesOnlyOnMinecraft26AndLater() {
		assertTrue(AnimGate.supported("26.3"));
		assertTrue(AnimGate.supported("26.1.2"));
		assertTrue(AnimGate.supported("27.1"));
		assertFalse(AnimGate.supported("1.21.11"));
		assertFalse(AnimGate.supported("1.16.5"));
		assertFalse(AnimGate.supported(""));
		assertFalse(AnimGate.supported(null));
		assertFalse(AnimGate.supported("snapshot"));
	}

	@Test
	void directoryListsAnimatedWearers() {
		SkinDirectory directory = new SkinDirectory();
		java.util.List<SkinEntry> all = new java.util.ArrayList<>();
		all.add(new SkinEntry("Still", false, null, "c1", null, 1));
		all.add(new SkinEntry("Moving", false, null, "c2", null, 2, "strip", 24, 12));
		directory.replaceAll(1, 2, "https://example.test/t/", all);
		java.util.List<SkinEntry> animated = directory.animated();
		org.junit.jupiter.api.Assertions.assertEquals(1, animated.size());
		org.junit.jupiter.api.Assertions.assertEquals("Moving", animated.get(0).name);
		org.junit.jupiter.api.Assertions.assertEquals("https://example.test/t/", directory.textureBase());
	}
}
