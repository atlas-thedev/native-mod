package xyz.nativelaunch.cosmetic;

/** Animation math, shared by every Minecraft version's renderer. */
public final class CosmeticPose {
	private static final double TAU = Math.PI * 2;

	private CosmeticPose() {
	}

	/**
	 * The transform of one part at {@code time} seconds: pivot x/y/z (pixels) then pitch/yaw/roll (radians)
	 * into {@code out[0..5]}. Returns false while a blink channel hides the part.
	 *
	 * @param moving walk speed, 0 (standing) .. 1 (running)
	 */
	public static boolean pose(CosmeticModel.Part part, double time, float moving, float[] out) {
		out[0] = part.px;
		out[1] = part.py;
		out[2] = part.pz;
		out[3] = part.rx;
		out[4] = part.ry;
		out[5] = part.rz;
		boolean visible = true;
		float walk = Math.max(0f, Math.min(1f, moving));
		for (CosmeticModel.Anim anim : part.anims) {
			switch (anim.type) {
				case SPIN: {
					double degrees = (anim.speed * time + anim.phase * 360.0) % 360.0;
					out[3 + anim.axis] += (float) Math.toRadians(degrees);
					break;
				}
				case SWING: {
					double amplitude = anim.amplitude + anim.moving * walk;
					out[3 + anim.axis] += (float) Math.toRadians(amplitude * Math.sin(TAU * cycle(anim, time)));
					break;
				}
				case BOB: {
					double amplitude = anim.amplitude + anim.moving * walk;
					out[anim.axis] += (float) (amplitude * Math.sin(TAU * cycle(anim, time)));
					break;
				}
				case BLINK: {
					double frac = cycle(anim, time);
					if (frac >= anim.amplitude) {
						visible = false;
					}
					break;
				}
				default:
					break;
			}
		}
		return visible;
	}

	/** Position in the channel's cycle, 0..1. */
	private static double cycle(CosmeticModel.Anim anim, double time) {
		double c = (anim.speed * time + anim.phase) % 1.0;
		return c < 0 ? c + 1 : c;
	}

	/** A stable per-player offset (0..10 s) so nearby players do not animate in lockstep. */
	public static double seed(String name) {
		if (name == null) {
			return 0;
		}
		int h = name.toLowerCase(java.util.Locale.ROOT).hashCode();
		return ((h & 0x7fffffff) % 1000) / 100.0;
	}

	private static final long START = System.nanoTime();

	/** Seconds since the mod started (double, so long sessions stay smooth). */
	public static double now() {
		return (System.nanoTime() - START) / 1.0e9;
	}
}
