package xyz.nativelaunch.cosmetic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CosmeticRefKeyTest {
	@Test
	void keyIsModelSlashTexture() {
		CosmeticRef ref = new CosmeticRef("hat", "m1", "t1");
		assertEquals("m1/t1", ref.key());
		assertEquals("m1/t1".hashCode(), ref.hashCode());
	}

	@Test
	void equalityStillComparesFilesSideAndId() {
		CosmeticRef a = new CosmeticRef("hat", "m1", "t1", "hats", 1);
		assertEquals(a, new CosmeticRef("hat", "m1", "t1", "hats", 1));
		assertNotEquals(a, new CosmeticRef("hat", "m1", "t1", "hats", 2));
		assertNotEquals(a, new CosmeticRef("cap", "m1", "t1", "hats", 1));
		assertNotEquals(a, new CosmeticRef("hat", "m2", "t1", "hats", 1));
	}
}
