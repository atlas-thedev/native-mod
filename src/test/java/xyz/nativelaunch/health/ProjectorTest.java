package xyz.nativelaunch.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectorTest {
	private final float[] out = new float[3];

	private Projector camera(float yaw, float pitch, float fov) {
		Projector p = new Projector();
		p.set(0, 0, 0, yaw, pitch, fov, 800, 600);
		return p;
	}

	@Test
	void whatYouLookAtIsInTheMiddle() {
		// yaw 0 looks south (+z)
		assertTrue(camera(0, 0, 70).project(0, 0, 10, out));
		assertEquals(400f, out[0], 0.01);
		assertEquals(300f, out[1], 0.01);
		assertEquals(10f, out[2], 0.01);
		// yaw 90 looks west (-x), yaw -90 east
		assertTrue(camera(90, 0, 70).project(-10, 0, 0, out));
		assertEquals(400f, out[0], 0.01);
		assertTrue(camera(-90, 0, 70).project(10, 0, 0, out));
		assertEquals(400f, out[0], 0.01);
		// pitch +90 looks straight down
		assertTrue(camera(0, 90, 70).project(0, -10, 0, out));
		assertEquals(300f, out[1], 0.01);
	}

	@Test
	void rightIsRightAndUpIsUp() {
		Projector p = camera(0, 0, 70);
		assertTrue(p.project(-2, 0, 10, out)); // facing south, west is on your right
		assertTrue(out[0] > 400);
		assertTrue(p.project(2, 0, 10, out));
		assertTrue(out[0] < 400);
		assertTrue(p.project(0, 2, 10, out));
		assertTrue(out[1] < 300);
		assertTrue(p.project(0, -2, 10, out));
		assertTrue(out[1] > 300);
	}

	@Test
	void theFovSetsWhereTheEdgeIs() {
		// 90 degrees vertical: a point at +z distance d and height d is exactly on the top edge
		Projector p = camera(0, 0, 90);
		assertTrue(p.project(0, 5, 5, out));
		assertEquals(0f, out[1], 0.01);
		// the 4:3 screen is wider than tall by the aspect ratio
		assertTrue(p.project(-5 * 4f / 3f, 0, 5, out));
		assertEquals(800f, out[0], 0.05);
	}

	@Test
	void sizeOnScreenFollowsFovNotJustDistance() {
		Projector wide = camera(0, 0, 90), zoomed = camera(0, 0, 30);
		wide.project(0, 1, 10, out);
		float wideOff = 300 - out[1];
		zoomed.project(0, 1, 10, out);
		float zoomOff = 300 - out[1];
		assertTrue(zoomOff > wideOff * 2.5f);
	}

	@Test
	void behindTheCameraIsNotDrawn() {
		assertFalse(camera(0, 0, 70).project(0, 0, -5, out));
		assertFalse(camera(0, 0, 70).project(0, 0, 0.01, out));
	}

	@Test
	void aimRayHitsTheBoxInFrontAndNothingElse() {
		Projector p = camera(0, 0, 70);
		// a 0.6 x 1.8 mob standing 5 blocks ahead, eyes at 1.6 look at its chest
		p.set(0, 1.6, 0, 0, 0, 70, 800, 600);
		assertEquals(4.6, p.rayBox(0, 0, 5, 0.6f, 1.8f, 0.1), 0.01);
		assertEquals(-1, p.rayBox(3, 0, 5, 0.6f, 1.8f, 0.1), 0);
		assertEquals(-1, p.rayBox(0, 0, -5, 0.6f, 1.8f, 0.1), 0);
		assertEquals(-1, p.rayBox(0, 5, 5, 0.6f, 1.8f, 0.1), 0);
		// from inside the box the hit is at distance 0
		assertEquals(0, p.rayBox(0, 0, 0, 2f, 3f, 0), 1e-9);
	}
}
