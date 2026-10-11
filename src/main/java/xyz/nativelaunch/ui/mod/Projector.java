package xyz.nativelaunch.ui.mod;

/**
 * Turns a point in the world into a spot on the screen, from the camera's position, rotation and field of view. The
 * game version bridges feed it the camera each frame, so the Health Indicator can put hearts over mobs and players
 * without touching the world renderer (which differs wildly between 1.16 and 26.x).
 */
public final class Projector {
	private double cx, cy, cz;
	private double fx, fy, fz, rx, ry, rz, ux, uy, uz;
	private double tanHalf = 1, aspect = 1;

	/** Result of the last {@link #project}: screen position as a fraction (0 - 1) of the width / height. */
	public float nx, ny;
	/** Distance in front of the camera, and the fraction of the screen height one block spans at that distance. */
	public float depth, perBlock;

	/** yaw / pitch in degrees as the game stores them (yaw 0 faces +Z, positive pitch looks down); fov is vertical. */
	public void set(double x, double y, double z, float yaw, float pitch, float fovDegrees, float aspectRatio) {
		cx = x;
		cy = y;
		cz = z;
		double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
		double sy = Math.sin(yr), cyw = Math.cos(yr), sp = Math.sin(pr), cp = Math.cos(pr);
		fx = -sy * cp;
		fy = -sp;
		fz = cyw * cp;
		rx = -cyw;
		ry = 0;
		rz = -sy;
		// up = right x forward
		ux = ry * fz - rz * fy;
		uy = rz * fx - rx * fz;
		uz = rx * fy - ry * fx;
		tanHalf = Math.tan(Math.toRadians(Math.max(1f, Math.min(170f, fovDegrees))) / 2);
		aspect = Math.max(0.1f, aspectRatio);
	}

	/** False when the point is behind the camera. */
	public boolean project(double x, double y, double z) {
		double dx = x - cx, dy = y - cy, dz = z - cz;
		double d = dx * fx + dy * fy + dz * fz;
		if (d < 0.05) {
			return false;
		}
		double sx = (dx * rx + dy * ry + dz * rz) / (d * tanHalf * aspect);
		double sy = (dx * ux + dy * uy + dz * uz) / (d * tanHalf);
		nx = (float) (0.5 + sx * 0.5);
		ny = (float) (0.5 - sy * 0.5);
		depth = (float) d;
		perBlock = (float) (0.5 / (d * tanHalf));
		return true;
	}
}
