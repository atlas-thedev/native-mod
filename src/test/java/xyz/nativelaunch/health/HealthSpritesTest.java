package xyz.nativelaunch.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthSpritesTest {
	private static int[] cell(int[] sheet, int cell) {
		int[] px = new int[81];
		for (int y = 0; y < 9; y++) {
			for (int x = 0; x < 9; x++) {
				px[y * 9 + x] = sheet[(1 + y) * HealthSprites.sheetWidth() + cell * HealthSprites.CELL + 1 + x];
			}
		}
		return px;
	}

	private static int opaque(int[] px) {
		int n = 0;
		for (int p : px) {
			if ((p >>> 24) != 0) {
				n++;
			}
		}
		return n;
	}

	@Test
	void everyCellHasAnIconAndTheGuttersStayClear() {
		int[] sheet = HealthSprites.sheet();
		assertEquals(HealthSprites.sheetWidth() * HealthSprites.sheetHeight(), sheet.length);
		for (int c = 0; c < HealthSprites.CELLS; c++) {
			assertTrue(opaque(cell(sheet, c)) > 30, "cell " + c);
			for (int y = 0; y < HealthSprites.CELL; y++) {
				assertEquals(0, sheet[y * HealthSprites.sheetWidth() + c * HealthSprites.CELL] >>> 24);
				assertEquals(0, sheet[y * HealthSprites.sheetWidth() + c * HealthSprites.CELL + HealthSprites.CELL - 1] >>> 24);
			}
		}
	}

	@Test
	void heartVariantsAreAllDifferent() {
		int[] sheet = HealthSprites.sheet();
		java.util.Set<String> seen = new java.util.HashSet<String>();
		for (int c = 0; c < HealthSprites.ARMOR_EMPTY; c++) {
			assertTrue(seen.add(java.util.Arrays.toString(cell(sheet, c))), "cell " + c + " duplicates another");
		}
		assertNotEquals(HealthSprites.heart(0, false, false), HealthSprites.heart(0, false, true));
		assertEquals(HealthSprites.ARMOR_EMPTY - 1, HealthSprites.heart(HealthSprites.ABSORB, true, true));
	}

	@Test
	void uvsStayInsideTheSheet() {
		for (int c = 0; c < HealthSprites.CELLS; c++) {
			assertTrue(HealthSprites.u0(c) > 0 && HealthSprites.u1(c) < 1 && HealthSprites.u0(c) < HealthSprites.u1(c));
		}
		assertTrue(HealthSprites.v0() > 0 && HealthSprites.v1() < 1);
	}
}
