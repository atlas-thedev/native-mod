package xyz.nativelaunch.cosmetic;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadCoverTest {
	@Test
	void boxAroundTheHeadCoversIt() {
		assertTrue(CosmeticModel.parse("{\"parts\":[{\"attach\":\"head\",\"cubes\":[{\"origin\":[-5,-9,-5],\"size\":[10,10,10]}]}]}").coversHead);
	}

	@Test
	void rotatedHoodCoversIt() {
		// the store's cyber hood: a 12x12 box turned 135 degrees around the head
		assertTrue(CosmeticModel.parse("{\"parts\":[{\"attach\":\"head\",\"pivot\":[0,-3.3168,-1.6249],\"rotation\":[-2.1223,-2.1208,135.0393],"
				+ "\"cubes\":[{\"origin\":[-6.9812,-5.0187,-3.8149],\"size\":[12,12,10.5]}]}]}").coversHead);
	}

	@Test
	void capsAndBackItemsDoNot() {
		assertFalse(CosmeticModel.parse("{\"parts\":[{\"attach\":\"head\",\"cubes\":[{\"origin\":[-4,-9,-4],\"size\":[8,1,8]}]}]}").coversHead);
		assertFalse(CosmeticModel.parse("{\"parts\":[{\"attach\":\"body\",\"cubes\":[{\"origin\":[-6,-10,-6],\"size\":[12,12,12]}]}]}").coversHead);
		assertFalse(CosmeticModel.parse("{\"parts\":[{\"attach\":\"head\",\"cubes\":[{\"origin\":[-5,-20,-5],\"size\":[10,10,10]}]}]}").coversHead);
	}
}
