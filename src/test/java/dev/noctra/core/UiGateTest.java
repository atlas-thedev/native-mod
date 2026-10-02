package dev.noctra.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UiGateTest {
	@Test
	void onlyMinecraft263GetsTheTitleScreen() {
		assertTrue(UiGate.supportsTitleScreen("26.3"));
		assertTrue(UiGate.supportsTitleScreen("26.3.1"));
		assertFalse(UiGate.supportsTitleScreen("26.2"));
		assertFalse(UiGate.supportsTitleScreen("26.30"));
		assertFalse(UiGate.supportsTitleScreen("26.4-snapshot-1"));
		assertFalse(UiGate.supportsTitleScreen("1.21.4"));
		assertFalse(UiGate.supportsTitleScreen(null));
	}
}
