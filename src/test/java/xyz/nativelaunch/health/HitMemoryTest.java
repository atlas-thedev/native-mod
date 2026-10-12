package xyz.nativelaunch.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HitMemoryTest {
	@Test
	void remembersUntilTheDurationIsUp() {
		HitMemory m = new HitMemory();
		m.hit(7, 100, 60);
		assertTrue(m.active(7, 100));
		assertTrue(m.active(7, 159));
		assertFalse(m.active(7, 160));
		assertFalse(m.active(8, 100));
	}

	@Test
	void anotherHitRestartsTheTimer() {
		HitMemory m = new HitMemory();
		m.hit(1, 0, 10);
		m.hit(1, 8, 10);
		assertTrue(m.active(1, 17));
	}

	@Test
	void pruneAndForgetAndClear() {
		HitMemory m = new HitMemory();
		m.hit(1, 0, 10);
		m.hit(2, 0, 100);
		m.prune(50);
		assertEquals(1, m.size());
		m.forget(2);
		assertEquals(0, m.size());
		m.hit(3, 0, 5);
		m.clear();
		assertEquals(0, m.size());
	}

	@Test
	void neverGrowsWithoutBound() {
		HitMemory m = new HitMemory();
		for (int i = 0; i < 10_000; i++) {
			m.hit(i, 0, 1000);
		}
		assertTrue(m.size() <= 2048);
	}
}
