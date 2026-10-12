package xyz.nativelaunch.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthLayoutTest {
	@Test
	void fullHealthIsTenFullHearts() {
		HealthLayout l = new HealthLayout().set(20, 20, 0, false, 20);
		assertEquals(10, l.red);
		assertEquals(10, l.normal);
		assertEquals(0, l.yellow);
		assertFalse(l.redHalf);
		assertEquals(HealthLayout.RED_FULL, l.typeAt(9));
	}

	@Test
	void oddHealthEndsInAHalfHeartThenEmpties() {
		HealthLayout l = new HealthLayout().set(7, 20, 0, false, 20);
		assertEquals(4, l.red);
		assertTrue(l.redHalf);
		assertEquals(HealthLayout.RED_FULL, l.typeAt(2));
		assertEquals(HealthLayout.RED_HALF, l.typeAt(3));
		assertEquals(HealthLayout.EMPTY, l.typeAt(4));
		assertEquals(HealthLayout.EMPTY, l.typeAt(9));
	}

	@Test
	void fractionalHealthRoundsUp() {
		HealthLayout l = new HealthLayout().set(0.5f, 20, 0, false, 20);
		assertEquals(1, l.red);
		assertTrue(l.redHalf);
	}

	@Test
	void absorptionAddsYellowHeartsAfterTheContainers() {
		HealthLayout l = new HealthLayout().set(20, 20, 5, false, 20);
		assertEquals(3, l.yellow);
		assertEquals(13, l.total);
		assertEquals(HealthLayout.YELLOW_FULL, l.typeAt(10));
		assertEquals(HealthLayout.YELLOW_HALF, l.typeAt(12));
	}

	@Test
	void percentModeScalesToTheChosenHeartCount() {
		HealthLayout l = new HealthLayout().set(50, 100, 0, true, 20);
		assertEquals(10, l.normal);
		assertEquals(5, l.red);
		assertFalse(l.redHalf);
	}

	@Test
	void absurdMaxHealthOverflowsToTheNumber() {
		HealthLayout l = new HealthLayout().set(1_000_000, 1_000_000, 0, false, 20);
		assertTrue(l.overflow);
		assertFalse(new HealthLayout().set(40, 40, 0, false, 20).overflow);
	}

	@Test
	void rowsAndPositions() {
		assertEquals(0, HealthLayout.rows(0, 10));
		assertEquals(1, HealthLayout.rows(10, 10));
		assertEquals(2, HealthLayout.rows(11, 10));
		assertEquals(5, HealthLayout.rows(5, 0)); // a bad width is treated as 1 per row
		// a full row of ten is 81 px wide, centred
		assertEquals(40.5f, HealthLayout.iconX(0, 10, 10), 1e-4);
		assertEquals(40.5f - 72f, HealthLayout.iconX(9, 10, 10), 1e-4);
		// the second row starts over at the left
		assertEquals(HealthLayout.iconX(0, 12, 10), HealthLayout.iconX(10, 12, 10), 1e-4);
		assertEquals(1, HealthLayout.rowOf(10, 10));
	}

	@Test
	void armourGrowsPastTheVanillaBarButIsCapped() {
		assertEquals(10, HealthLayout.armorSlots(0));
		assertEquals(10, HealthLayout.armorSlots(20));
		assertEquals(15, HealthLayout.armorSlots(30));
		assertEquals(HealthLayout.MAX_ARMOR_SLOTS, HealthLayout.armorSlots(10_000));
		assertEquals(1, HealthLayout.armorType(0, 5));
		assertEquals(1, HealthLayout.armorType(1, 5));
		assertEquals(2, HealthLayout.armorType(2, 5));
		assertEquals(0, HealthLayout.armorType(3, 5));
	}

	@Test
	void numberText() {
		assertEquals("13.5 / 20.0", HealthLayout.text(13.5f, 20, 0, false, 1));
		assertEquals("15 / 20", HealthLayout.text(13, 20, 2, false, 0));
		assertEquals("50.0 %", HealthLayout.text(10, 20, 0, true, 1));
		assertEquals("0.0 %", HealthLayout.text(10, 0, 0, true, 1));
	}

	@Test
	void depthOffsetGrowsWithDistanceAndShrinksWithSize() {
		float near = HealthLayout.depthOffsetPx(4, 0.025f);
		float far = HealthLayout.depthOffsetPx(4096, 0.025f);
		assertTrue(far > near && near > 0);
		assertTrue(HealthLayout.depthOffsetPx(4, 0.05f) < near);
	}

	@Test
	void regenHopWrapsAroundTheBar() {
		assertEquals(0, HealthLayout.regenIndex(0, 20));
		assertEquals(24, HealthLayout.regenIndex(24, 20));
		assertEquals(0, HealthLayout.regenIndex(25, 20));
		assertEquals(3, HealthLayout.regenIndex(-22, 20));
	}
}
