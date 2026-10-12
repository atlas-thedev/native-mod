package xyz.nativelaunch.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageArcTest {
	private static final double DEG90 = Math.PI / 2;

	@Test
	void angleIsRelativeToWhereYouLook() {
		// yaw 0 looks at +z (south): an attacker to the south is dead ahead, to the west on your right
		assertEquals(0f, DamageArc.angle(0, 0, 0, 10, 0f), 1e-4);
		assertEquals(DEG90, DamageArc.angle(0, 0, -10, 0, 0f), 1e-4);
		assertEquals(-DEG90, DamageArc.angle(0, 0, 10, 0, 0f), 1e-4);
		assertEquals(Math.PI, Math.abs(DamageArc.angle(0, 0, 0, -10, 0f)), 1e-4);
		// turn to face west (yaw 90): the same attacker is now straight ahead
		assertEquals(0f, DamageArc.angle(0, 0, -10, 0, 90f), 1e-4);
	}

	@Test
	void degreesWrap() {
		assertEquals(-170f, DamageArc.wrapDegrees(190f), 1e-4);
		assertEquals(170f, DamageArc.wrapDegrees(-190f), 1e-4);
		assertEquals(0f, DamageArc.wrapDegrees(720f), 1e-4);
	}

	@Test
	void fadeCurve() {
		assertEquals(1f, DamageArc.alpha(0, 4, true, 3), 1e-4);
		assertEquals(1f, DamageArc.alpha(1, 4, true, 3), 1e-4);
		assertEquals(0.5f, DamageArc.alpha(2.5f, 4, true, 3), 1e-4);
		assertEquals(0f, DamageArc.alpha(4, 4, true, 3), 1e-4);
		assertEquals(1f, DamageArc.alpha(3.9f, 4, false, 3), 1e-4);
		assertEquals(0f, DamageArc.alpha(5, 4, false, 3), 1e-4);
		assertEquals(1f, DamageArc.alpha(3.9f, 4, true, 0), 1e-4);
	}

	@Test
	void stateExpires() {
		DamageArc.clear();
		assertFalse(DamageArc.active(0, 4));
		DamageArc.mark(5, 6, 1_000_000_000L);
		assertTrue(DamageArc.active(2_000_000_000L, 4));
		assertEquals(5, DamageArc.attackerX(), 0);
		DamageArc.follow(7, 8);
		assertEquals(8, DamageArc.attackerZ(), 0);
		assertEquals(1f, DamageArc.ageSeconds(2_000_000_000L), 1e-4);
		assertFalse(DamageArc.active(6_000_000_000L, 4));
		DamageArc.follow(1, 1); // ignored once expired
		assertEquals(8, DamageArc.attackerZ(), 0);
		DamageArc.clear();
	}

	@Test
	void geometryFillsEveryQuadAroundTheCentre() {
		float[] out = new float[DamageArc.QUAD_FLOATS];
		DamageArc.geometry(100, 100, 0f, 2f, out);
		float minY = Float.MAX_VALUE;
		for (int i = 0; i < DamageArc.SEGMENTS * 8; i += 2) {
			assertTrue(out[i + 1] < 100, "arc pointing up sits above the centre");
			minY = Math.min(minY, out[i + 1]);
		}
		assertTrue(minY > 100 - 60 && minY < 100 - 40);
		// the arrow head lies beyond the band
		assertTrue(out[DamageArc.SEGMENTS * 8 + 1] < minY);
	}
}
