package xyz.nativelaunch.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimGateTest {
	@Test
	void animatesOnEveryReleaseFrom116() {
		assertEquals(AnimGate.Mode.MODERN, AnimGate.mode("26.3"));
		assertEquals(AnimGate.Mode.MODERN, AnimGate.mode("26.1.2"));
		assertEquals(AnimGate.Mode.MODERN, AnimGate.mode("27.1"));
		assertEquals(AnimGate.Mode.MODERN, AnimGate.mode("26.4-pre.1"));
		assertEquals(AnimGate.Mode.GL, AnimGate.mode("1.21.11"));
		assertEquals(AnimGate.Mode.GL, AnimGate.mode("1.21.5"));
		assertEquals(AnimGate.Mode.GL, AnimGate.mode("1.20.1"));
		assertEquals(AnimGate.Mode.GL, AnimGate.mode("1.16.5"));
		assertEquals(AnimGate.Mode.GL, AnimGate.mode("1.16"));
		assertEquals(AnimGate.Mode.GL, AnimGate.mode("1.20.5-rc.1"));
		assertEquals(AnimGate.Mode.NONE, AnimGate.mode("1.15.2"));
		assertEquals(AnimGate.Mode.NONE, AnimGate.mode("1.8.9"));
		assertEquals(AnimGate.Mode.NONE, AnimGate.mode("25w14a"));
		assertEquals(AnimGate.Mode.NONE, AnimGate.mode(""));
		assertEquals(AnimGate.Mode.NONE, AnimGate.mode(null));
		assertEquals(AnimGate.Mode.NONE, AnimGate.mode("snapshot"));
		assertTrue(AnimGate.supported("1.16.5"));
		assertTrue(AnimGate.supported("26.3"));
		assertFalse(AnimGate.supported("1.12.2"));
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
