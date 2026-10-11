package xyz.nativelaunch.ui.mod;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusTest {
	@Test
	void romanNumerals() {
		assertEquals("I", Status.roman(1));
		assertEquals("IV", Status.roman(4));
		assertEquals("X", Status.roman(10));
		assertEquals("11", Status.roman(11));
		assertEquals("0", Status.roman(0));
	}

	@Test
	void timeIsRoundedUpLikeTheGame() {
		assertEquals("0:01", Status.time(1));
		assertEquals("0:01", Status.time(20));
		assertEquals("0:02", Status.time(21));
		assertEquals("1:05", Status.time(20 * 65));
		assertEquals("10:00", Status.time(20 * 600));
		assertEquals("1:02:03", Status.time(20 * 3723));
		assertEquals("0:00", Status.time(-5));
	}

	@Test
	void effectIdsAndNames() {
		assertEquals("speed", Game.effectId("effect.minecraft.speed"));
		assertEquals("mymod:bless", Game.effectId("effect.mymod.bless"));
		assertEquals("Night Vision", Status.effectName("night_vision"));
		assertEquals("Bad Luck", Status.effectName("unluck"));
		assertEquals("Bless", Status.effectName("mymod:bless"));
		assertEquals("diamond_sword", Game.bareId("minecraft:diamond_sword"));
		assertTrue(Status.harmful("poison"));
		assertFalse(Status.harmful("speed"));
	}

	@Test
	void foodTable() {
		assertEquals(8, Status.foodHunger("cooked_beef"));
		assertEquals(12.8f, Status.foodSaturation("cooked_beef"), 0.001f);
		assertEquals(0, Status.foodHunger("diamond_sword"));
		assertEquals(0, Status.foodHunger(null));
	}

	@Test
	void wearColourRunsGreenToRed() {
		assertEquals(0xFF00FF00, Status.wear(1f));
		assertEquals(0xFFFF0000, Status.wear(0f));
		assertEquals(0xFFFFFF00, Status.wear(0.5f));
	}

	@Test
	void healthNumbers() {
		assertEquals("20", Status.hp(20f));
		assertEquals("7.5", Status.hp(7.5f));
	}

	@Test
	void durabilityFraction() {
		Game.Slot s = new Game.Slot();
		s.set("diamond_sword", 1, 390, 1561);
		assertEquals(0.75f, s.durability(), 0.01f);
		s.set("stone", 64, 0, 0);
		assertEquals(1f, s.durability(), 0f);
	}

	/** Module ids are unique, settings never clash inside a module, and the menu only lists settings the module owns. */
	@Test
	void moduleSettingsAreConsistent() {
		Modules.init(null);
		Set<String> ids = new HashSet<String>();
		for (Module m : Modules.all()) {
			assertTrue(ids.add(m.id), "duplicate module id " + m.id);
			Set<String> own = new HashSet<String>();
			for (Setting s : m.allSettings()) {
				assertTrue(own.add(s.id), m.id + " has two settings called " + s.id);
			}
			for (Setting s : m.shownAll()) {
				assertTrue(m.allSettings().contains(s), m.id + " lists a setting it does not own: " + s.id);
			}
		}
		for (String id : new String[] {"effects", "armor", "inventory", "healthindicator", "crosshair"}) {
			assertTrue(ids.contains(id), "missing module " + id);
		}
	}
}
