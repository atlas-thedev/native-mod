package xyz.nativelaunch.health;

/** Decides whether an entity gets an indicator. Pure: the game side fills in {@link Facts}. */
public final class EntityRules {
	private EntityRules() {
	}

	/** What the game knows about one entity this frame. */
	public static final class Facts {
		/** Alive, visible to the player, not the thing they ride, not a spectator. */
		public boolean valid = true;
		public boolean player, self, passive, hostile;
		public boolean recentlyHit, targeted;
		public float health = 20, max = 20, absorption;
		public double distance;
		/** Passed the entity list (blacklist / whitelist). */
		public boolean listed = true;
	}

	public static boolean show(Facts f, HealthSettings s) {
		if (!s.enabled || !f.valid) {
			return false;
		}
		if (!(s.hearts || s.armor || s.override)) {
			return false;
		}
		if (!f.listed) {
			return false;
		}
		if (s.override) {
			return true;
		}
		// "always show other players" and "show myself" skip the criteria below
		if ((s.overridePlayers && f.player && !f.self) || (f.self && s.self)) {
			return true;
		}
		if (!typeAllowed(f, s)) {
			return false;
		}
		if (s.afterAttack && !f.recentlyHit) {
			return false;
		}
		if (s.damagedOnly && f.absorption <= 0
				&& (f.health >= f.max || f.health > f.max * (s.maxHealthPct / 100f))) {
			return false;
		}
		if (s.lookingAt && !f.targeted) {
			return false;
		}
		if (s.withinDistance && f.distance > s.distance) {
			return false;
		}
		return true;
	}

	public static boolean typeAllowed(Facts f, HealthSettings s) {
		if (!s.passive && f.passive) {
			return false;
		}
		if (!s.hostile && f.hostile) {
			return false;
		}
		if (!s.players && f.player) {
			return false;
		}
		if (!s.self && f.self) {
			return false;
		}
		return true;
	}
}
