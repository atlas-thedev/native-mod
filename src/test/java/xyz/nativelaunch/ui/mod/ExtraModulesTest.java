package xyz.nativelaunch.ui.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import xyz.nativelaunch.ui.gfx.Image;

class ExtraModulesTest {
	@Test
	void theDaySplitsIntoDayDuskNightAndDawn() {
		assertEquals(0, ExtraModules.DayNight.phaseOf(0));
		assertEquals(0, ExtraModules.DayNight.phaseOf(11999));
		assertEquals(1, ExtraModules.DayNight.phaseOf(12000));
		assertEquals(2, ExtraModules.DayNight.phaseOf(13000));
		assertEquals(2, ExtraModules.DayNight.phaseOf(22999));
		assertEquals(3, ExtraModules.DayNight.phaseOf(23000));
		assertEquals(3, ExtraModules.DayNight.phaseOf(23999));
	}

	@Test
	void netherCoordinatesAreTheOverworldOnesDividedByEight() {
		Game g = new Game();
		g.sample = true;
		g.x = 800.9;
		g.z = -81;
		String[] l = new String[6], v = new String[6];
		ExtraModules.NetherCoords m = new ExtraModules.NetherCoords();
		assertEquals(2, m.lines(g, l, v));
		assertEquals("Nether X", l[0]);
		assertEquals("100", v[0]);
		assertEquals("-11", v[1], "rounds down like the game's block coordinates");
		m.compact.value = true;
		assertEquals(1, m.lines(g, l, v));
		assertEquals("100 -11", v[0]);
	}

	@Test
	void countsAnItemInTheBackpackHotbarAndOffHand() {
		Game g = new Game();
		g.inv[0].set("totem_of_undying", 1, 0, 0);
		g.inv[20].set("totem_of_undying", 2, 0, 0);
		g.inv[3].set("ender_pearl", 16, 0, 0);
		g.offhand.set("totem_of_undying", 1, 0, 0);
		assertEquals(4, ExtraModules.count(g, "totem_of_undying"));
		assertEquals(16, ExtraModules.count(g, "ender_pearl"));
		assertEquals(0, ExtraModules.count(g, "diamond"));
	}

	@Test
	void theItemCounterFollowsTheHandWhenNoItemIsPicked() {
		Game g = new Game();
		ExtraModules.ItemCounter c = new ExtraModules.ItemCounter();
		assertEquals(null, c.item(g), "empty hand: nothing to count");
		g.hand.set("golden_apple", 5, 0, 0);
		assertEquals("golden_apple", c.item(g));
		c.pick.value = "Ender Pearl";
		assertEquals("ender_pearl", c.item(g));
		assertEquals("Golden Apple", c.itemName("golden_apple"));
		assertEquals("Warped Fungus", c.itemName("warped_fungus"));
	}

	@Test
	void playtimeIsShownAsClockAndSpan() {
		assertEquals("1:23:45", ExtraModules.Playtime.hms(5_025_000L));
		assertEquals("0:09", ExtraModules.Playtime.hms(9_000L));
		assertEquals("39h 33m", ExtraModules.Playtime.span(142_380_000L));
		assertEquals("12m", ExtraModules.Playtime.span(12 * 60_000L));
		assertEquals("120h", ExtraModules.Playtime.span(120 * 3_600_000L));
	}

	@Test
	void theMoonIsLitOnTheRightWhileWaxingAndTheLeftWhileWaning() {
		int px = 32;
		Image full = ExtraModules.moon(0, px), nw = ExtraModules.moon(4, px), waxing = ExtraModules.moon(6, px), waning = ExtraModules.moon(2, px);
		assertTrue(brightness(full, 16, 16) > 200, "full moon is bright in the middle");
		assertTrue(brightness(nw, 16, 16) < 90, "new moon is dark");
		assertTrue(brightness(waxing, 26, 16) > 150 && brightness(waxing, 6, 16) < 90, "first quarter: right side lit");
		assertTrue(brightness(waning, 6, 16) > 150 && brightness(waning, 26, 16) < 90, "last quarter: left side lit");
		assertEquals(0, full.argb[0] >>> 24, "outside the disc is transparent");
	}

	@Test
	void theHurtTintIsWrittenAsAbgr() {
		assertEquals(0xB20000FF, DamageTint.abgr(0xFFFF0000, 70), "the game's own red at 70 %");
		assertEquals(0xFFFF0000, DamageTint.abgr(0xFF0000FF, 100), "blue becomes the B byte");
		assertEquals(0, DamageTint.abgr(0xFF123456, 0) >>> 24, "0 % is invisible");
		assertFalse(DamageTint.abgr(0xFF00FF00, 50) == DamageTint.abgr(0xFF00FF00, 100));
	}

	private static int brightness(Image img, int x, int y) {
		int p = img.argb[y * img.width + x];
		return (((p >> 16) & 255) + ((p >> 8) & 255) + (p & 255)) / 3;
	}

	@Test
	void aComboGrowsPerHitEndsOnDamageAndOnSilence() {
		ExtraModules.ComboTracker t = new ExtraModules.ComboTracker();
		t.hit(1000);
		t.hit(1200); // inside the target's damage immunity: no damage, no combo
		assertEquals(1, t.combo);
		t.hit(1500);
		t.hit(2100);
		assertEquals(3, t.combo);
		assertEquals(3, t.best);
		t.damaged();
		assertEquals(0, t.combo);
		assertEquals(3, t.best, "the best of the session stays");
		t.hit(5000);
		t.tick(7000, 3000);
		assertEquals(1, t.combo, "still going 2 s after the hit");
		t.tick(8100, 3000);
		assertEquals(0, t.combo, "no hit for over 3 s ends it");
	}

	@Test
	void theBlockOutlineIsRecolouredOnlyWhileTheModuleIsOnAndOnlyFromTheGamesDefault() {
		int vanilla = 0x66000000;
		assertEquals(vanilla, BlockOverlay.outline(vanilla), "module never started: the game's outline");
		BlockOverlay m = new BlockOverlay();
		m.color.value = 0xFF00FF00;
		m.opacity.value = 50;
		m.frame(new Game());
		assertEquals(0x8000FF00, BlockOverlay.outline(vanilla), "green at half strength replaces the default");
		assertEquals(0xFFFFFFFF, BlockOverlay.outline(0xFFFFFFFF), "a high-contrast outline the player chose stays");
		float[] rgba = {0f, 0f, 0f, 0.4f};
		assertTrue(BlockOverlay.outline(rgba));
		assertEquals(1f, rgba[1], 1e-6);
		assertEquals(0f, rgba[0], 1e-6);
		float[] other = {1f, 0f, 0f, 1f};
		assertFalse(BlockOverlay.outline(other), "debug colours are left alone");
		m.rainbowSetting.value = true;
		m.frame(new Game());
		int c = BlockOverlay.outline(vanilla);
		assertEquals(0x80, c >>> 24, "the strength still applies to the rainbow");
		assertEquals(0xFF0000, BlockOverlay.hue(0f, 0) & 0xFFFFFF, "hue 0 is red");
		assertEquals(0x00FF00, BlockOverlay.hue(1f / 3, 0) & 0xFFFFFF, "a third of the way is green");
		m.setEnabled(true);
		m.setEnabled(false);
		assertEquals(vanilla, BlockOverlay.outline(vanilla), "switched off: the game's outline again");
	}
}
