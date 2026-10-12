package xyz.nativelaunch.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityRulesTest {
	private static HealthSettings on() {
		HealthSettings s = new HealthSettings();
		s.enabled = true;
		return s;
	}

	@Test
	void offMeansOff() {
		HealthSettings s = new HealthSettings();
		assertFalse(EntityRules.show(new EntityRules.Facts(), s));
		s = on();
		s.hearts = false;
		s.armor = false;
		assertFalse(EntityRules.show(new EntityRules.Facts(), s));
	}

	@Test
	void invalidAndUnlistedEntitiesNeverShowEvenWithOverride() {
		HealthSettings s = on();
		s.override = true;
		EntityRules.Facts f = new EntityRules.Facts();
		f.valid = false;
		assertFalse(EntityRules.show(f, s));
		f.valid = true;
		f.listed = false;
		assertFalse(EntityRules.show(f, s));
		f.listed = true;
		assertTrue(EntityRules.show(f, s));
	}

	@Test
	void typeFilters() {
		HealthSettings s = on();
		EntityRules.Facts f = new EntityRules.Facts();
		f.passive = true;
		assertTrue(EntityRules.show(f, s));
		s.passive = false;
		assertFalse(EntityRules.show(f, s));
		f.passive = false;
		f.hostile = true;
		s.hostile = false;
		assertFalse(EntityRules.show(f, s));
		f.hostile = false;
		f.player = true;
		s.players = false;
		assertFalse(EntityRules.show(f, s));
	}

	@Test
	void selfOnlyWhenAsked() {
		HealthSettings s = on();
		EntityRules.Facts f = new EntityRules.Facts();
		f.player = true;
		f.self = true;
		assertFalse(EntityRules.show(f, s));
		s.self = true;
		assertTrue(EntityRules.show(f, s));
		// and then the other criteria do not apply to it
		s.lookingAt = true;
		assertTrue(EntityRules.show(f, s));
	}

	@Test
	void overridePlayersSkipsTheCriteriaForOthers() {
		HealthSettings s = on();
		s.overridePlayers = true;
		s.afterAttack = true;
		s.lookingAt = true;
		EntityRules.Facts f = new EntityRules.Facts();
		f.player = true;
		assertTrue(EntityRules.show(f, s));
		f.player = false;
		assertFalse(EntityRules.show(f, s));
	}

	@Test
	void afterAttackNeedsARecentHit() {
		HealthSettings s = on();
		s.afterAttack = true;
		EntityRules.Facts f = new EntityRules.Facts();
		assertFalse(EntityRules.show(f, s));
		f.recentlyHit = true;
		assertTrue(EntityRules.show(f, s));
	}

	@Test
	void damagedOnlyHonoursTheThreshold() {
		HealthSettings s = on();
		s.damagedOnly = true;
		s.maxHealthPct = 50;
		EntityRules.Facts f = new EntityRules.Facts();
		f.max = 20;
		f.health = 20;
		assertFalse(EntityRules.show(f, s));
		f.health = 15;
		assertFalse(EntityRules.show(f, s)); // hurt, but still above 50 %
		f.health = 10;
		assertTrue(EntityRules.show(f, s));
		f.health = 20;
		f.absorption = 4;
		assertTrue(EntityRules.show(f, s)); // absorption counts as "has something to show"
	}

	@Test
	void lookingAtAndDistance() {
		HealthSettings s = on();
		s.lookingAt = true;
		EntityRules.Facts f = new EntityRules.Facts();
		assertFalse(EntityRules.show(f, s));
		f.targeted = true;
		assertTrue(EntityRules.show(f, s));
		s.withinDistance = true;
		s.distance = 10;
		f.distance = 11;
		assertFalse(EntityRules.show(f, s));
		f.distance = 10;
		assertTrue(EntityRules.show(f, s));
	}

	@Test
	void overrideShowsEverythingValid() {
		HealthSettings s = on();
		s.hearts = false;
		s.passive = false;
		s.afterAttack = true;
		s.override = true;
		EntityRules.Facts f = new EntityRules.Facts();
		f.passive = true;
		assertTrue(EntityRules.show(f, s));
	}

	@Test
	void numberStyleSelection() {
		HealthSettings s = on();
		assertFalse(s.useNumber(20, false));
		assertTrue(s.useNumber(20, true));
		s.style = HealthSettings.STYLE_NUMBER;
		assertTrue(s.useNumber(20, false));
		s.style = HealthSettings.STYLE_DYNAMIC;
		s.dynamicThreshold = 100;
		assertFalse(s.useNumber(100, false));
		assertTrue(s.useNumber(101, false));
	}
}
