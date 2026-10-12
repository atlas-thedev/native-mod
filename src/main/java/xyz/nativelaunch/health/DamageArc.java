package xyz.nativelaunch.health;

/**
 * The red arc around the crosshair that points at whatever last hurt you. State (who, where, when) plus the maths
 * for the angle, the fade and the arc's corner points. The UI side only turns the quads into pixels.
 */
public final class DamageArc {
	public static final int SEGMENTS = 20;
	/** Corner floats of the main arc ({@link #SEGMENTS} quads), plus the arrow head (1 quad). */
	public static final int QUAD_FLOATS = (SEGMENTS + 1) * 8;

	private static boolean active;
	private static long hitNanos;
	private static double ax, az;

	private DamageArc() {
	}

	public static void mark(double attackerX, double attackerZ, long nowNanos) {
		active = true;
		hitNanos = nowNanos;
		ax = attackerX;
		az = attackerZ;
	}

	/** Hurt from a known direction but not a known place: {@code dirDeg} is the world heading to the attacker (atan2(dz, dx) in degrees). */
	public static void markFrom(double playerX, double playerZ, double dirDeg, long nowNanos) {
		double r = Math.toRadians(dirDeg);
		mark(playerX + Math.cos(r) * 1000.0, playerZ + Math.sin(r) * 1000.0, nowNanos);
	}

	/** Follows the attacker while it is still around. */
	public static void follow(double attackerX, double attackerZ) {
		if (active) {
			ax = attackerX;
			az = attackerZ;
		}
	}

	public static void clear() {
		active = false;
	}

	public static boolean active(long nowNanos, int visibleSeconds) {
		if (active && nowNanos - hitNanos > visibleSeconds * 1_000_000_000L) {
			active = false;
		}
		return active;
	}

	public static double attackerX() {
		return ax;
	}

	public static double attackerZ() {
		return az;
	}

	public static float ageSeconds(long nowNanos) {
		return (nowNanos - hitNanos) / 1.0e9f;
	}

	public static float wrapDegrees(float d) {
		d = d % 360f;
		if (d >= 180f) {
			d -= 360f;
		}
		if (d < -180f) {
			d += 360f;
		}
		return d;
	}

	/**
	 * Angle in radians from straight ahead (0, top of the screen) to the attacker, clockwise positive. {@code yawDeg} is
	 * the player's Minecraft yaw (0 = south, +z).
	 */
	public static float angle(double px, double pz, double attackerX, double attackerZ, float yawDeg) {
		double dx = attackerX - px, dz = attackerZ - pz;
		float toAttacker = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
		return (float) Math.toRadians(wrapDegrees(toAttacker - yawDeg));
	}

	/** Opacity 0 - 1 after {@code age} seconds: full, then fading out over the last {@code fadeSeconds} of {@code visibleSeconds}. */
	public static float alpha(float age, int visibleSeconds, boolean fade, int fadeSeconds) {
		if (age >= visibleSeconds) {
			return 0f;
		}
		if (!fade || fadeSeconds <= 0) {
			return 1f;
		}
		float start = Math.max(0f, visibleSeconds - fadeSeconds);
		if (age <= start) {
			return 1f;
		}
		return Math.max(0f, 1f - (age - start) / Math.min(fadeSeconds, visibleSeconds));
	}

	/**
	 * Fills {@code out} with the arc's quads (8 floats each: x, y of four corners) around ({@code cx}, {@code cy}) pointing
	 * at {@code angle}: {@link #SEGMENTS} quads for the band, then one for the arrow head (a triangle as a quad).
	 */
	public static void geometry(float cx, float cy, float angle, float scale, float[] out) {
		float radius = 22f * scale;
		float thick = 1.8f * scale;
		float span = (float) Math.toRadians(50.0);
		int o = 0;
		for (int i = 0; i < SEGMENTS; i++) {
			float a1 = angle - span / 2 + span * i / SEGMENTS;
			float a2 = angle - span / 2 + span * (i + 1) / SEGMENTS;
			o = quad(out, o, cx, cy, a1, a2, radius, radius + thick);
		}
		float len = 5f * scale, wing = 4f * scale;
		float bx = cx + (radius + thick) * sin(angle), by = cy - (radius + thick) * cos(angle);
		float tx = cx + (radius + thick + len) * sin(angle), ty = cy - (radius + thick + len) * cos(angle);
		float perp = angle + (float) Math.PI / 2f;
		float lx = bx + wing * sin(perp), ly = by - wing * cos(perp);
		float rx = bx - wing * sin(perp), ry = by + wing * cos(perp);
		out[o++] = tx;
		out[o++] = ty;
		out[o++] = lx;
		out[o++] = ly;
		out[o++] = rx;
		out[o++] = ry;
		out[o++] = rx;
		out[o++] = ry;
	}

	private static int quad(float[] out, int o, float cx, float cy, float a1, float a2, float r0, float r1) {
		out[o++] = cx + r0 * sin(a1);
		out[o++] = cy - r0 * cos(a1);
		out[o++] = cx + r0 * sin(a2);
		out[o++] = cy - r0 * cos(a2);
		out[o++] = cx + r1 * sin(a2);
		out[o++] = cy - r1 * cos(a2);
		out[o++] = cx + r1 * sin(a1);
		out[o++] = cy - r1 * cos(a1);
		return o;
	}

	private static float sin(float a) {
		return (float) Math.sin(a);
	}

	private static float cos(float a) {
		return (float) Math.cos(a);
	}
}
