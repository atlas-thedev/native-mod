package xyz.nativelaunch.health;

/** Turns world points into screen points (and aim rays into entity hits) for a camera given by position, yaw, pitch and FOV. */
public final class Projector {
	private double cx, cy, cz;
	private double fx, fy, fz, rx, ry, rz, ux, uy, uz;
	private double tanHalf = 1, aspect = 1;
	private float width = 1, height = 1;

	/** @param fovDeg vertical field of view; @param width / height the screen in the units the points should come out in. */
	public void set(double x, double y, double z, float yawDeg, float pitchDeg, float fovDeg, float width, float height) {
		cx = x;
		cy = y;
		cz = z;
		double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
		double sy = Math.sin(yaw), cyw = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
		fx = -sy * cp;
		fy = -sp;
		fz = cyw * cp;
		rx = -cyw;
		ry = 0;
		rz = -sy;
		ux = ry * fz - rz * fy;
		uy = rz * fx - rx * fz;
		uz = rx * fy - ry * fx;
		tanHalf = Math.tan(Math.toRadians(Math.max(1f, Math.min(170f, fovDeg))) / 2.0);
		this.width = width;
		this.height = height;
		aspect = height <= 0 ? 1 : width / (double) height;
	}

	public double forwardX() {
		return fx;
	}

	public double forwardY() {
		return fy;
	}

	public double forwardZ() {
		return fz;
	}

	/** Writes the screen x, y of the world point into {@code out[0..1]} and its distance along the view direction into {@code out[2]}. False when it is behind the camera. */
	public boolean project(double x, double y, double z, float[] out) {
		double dx = x - cx, dy = y - cy, dz = z - cz;
		double depth = dx * fx + dy * fy + dz * fz;
		if (depth < 0.05) {
			return false;
		}
		double nx = (dx * rx + dy * ry + dz * rz) / (depth * tanHalf * aspect);
		double ny = (dx * ux + dy * uy + dz * uz) / (depth * tanHalf);
		out[0] = (float) ((nx + 1.0) * 0.5 * width);
		out[1] = (float) ((1.0 - ny) * 0.5 * height);
		out[2] = (float) depth;
		return true;
	}

	/**
	 * Distance along the camera's view ray to the box (centre x, z at the feet y; width and height), or -1 when it
	 * is missed. {@code grow} makes the box a bit bigger, like vanilla's targeting margin.
	 */
	public double rayBox(double x, double y, double z, float w, float h, double grow) {
		double hw = w / 2.0 + grow;
		double[] min = {x - hw, y - grow, z - hw};
		double[] max = {x + hw, y + h + grow, z + hw};
		double[] o = {cx, cy, cz};
		double[] d = {fx, fy, fz};
		double t0 = 0, t1 = Double.MAX_VALUE;
		for (int i = 0; i < 3; i++) {
			if (Math.abs(d[i]) < 1e-9) {
				if (o[i] < min[i] || o[i] > max[i]) {
					return -1;
				}
			} else {
				double a = (min[i] - o[i]) / d[i], b = (max[i] - o[i]) / d[i];
				if (a > b) {
					double t = a;
					a = b;
					b = t;
				}
				t0 = Math.max(t0, a);
				t1 = Math.min(t1, b);
				if (t0 > t1) {
					return -1;
				}
			}
		}
		return t0;
	}
}
