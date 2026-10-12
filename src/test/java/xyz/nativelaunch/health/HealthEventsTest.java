package xyz.nativelaunch.health;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthEventsTest {
	/** Stands in for a game entity: has the official-name getId. */
	public static class Official {
		public int getId() {
			return 42;
		}
	}

	/** And one with the intermediary name. */
	public static class Intermediary {
		public int method_5628() {
			return 7;
		}
	}

	@BeforeEach
	void on() {
		HealthSettings.cur.enabled = true;
		HealthEvents.HITS.clear();
	}

	@AfterEach
	void off() {
		HealthSettings.cur.enabled = false;
		HealthEvents.HITS.clear();
		DamageArc.clear();
	}

	@Test
	void attacksAreRememberedByEntityId() {
		HealthEvents.attacked(new Official());
		HealthEvents.attacked(new Intermediary());
		long now = HealthEvents.tick();
		assertTrue(HealthEvents.HITS.active(42, now));
		assertTrue(HealthEvents.HITS.active(7, now));
		assertFalse(HealthEvents.HITS.active(8, now));
	}

	@Test
	void nothingIsRememberedWhileOff() {
		HealthSettings.cur.enabled = false;
		HealthEvents.attacked(new Official());
		HealthEvents.attackedId(5);
		assertFalse(HealthEvents.HITS.active(42, HealthEvents.tick()));
		assertFalse(HealthEvents.HITS.active(5, HealthEvents.tick()));
	}

	@Test
	void junkObjectsAreIgnored() {
		HealthEvents.attacked(new Object());
		HealthEvents.attacked(null);
		assertTrue(HealthEvents.HITS.size() == 0);
	}

	@Test
	void arcOnlyWhenEnabled() {
		HealthSettings.cur.arc = false;
		HealthEvents.hurtBy(1, 2);
		assertFalse(DamageArc.active(System.nanoTime(), 4));
		HealthSettings.cur.arc = true;
		HealthEvents.hurtBy(1, 2);
		assertTrue(DamageArc.active(System.nanoTime(), 4));
		HealthSettings.cur.arc = false;
	}
}
