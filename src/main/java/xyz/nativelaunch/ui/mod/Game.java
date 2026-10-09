package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.McBridge;

/**
 * One frame's view of the game for the client mods: position, speed, fps, clicks, keys. Updated once per frame
 * on the render thread (cheap: a few reflective reads), read by every HUD module.
 */
public final class Game {
	public static final String[] CONTROLS = {"forward", "left", "back", "right", "jump", "sneak", "sprint", "attack", "use"};
	public static final int FORWARD = 0, LEFT = 1, BACK = 2, RIGHT = 3, JUMP = 4, SNEAK = 5, SPRINT = 6, ATTACK = 7, USE = 8;

	public boolean inWorld, sample;
	public int fps;
	public double x, y, z;
	public float yaw, pitch;
	/** Horizontal speed in blocks per second. */
	public float speed;
	public int ping = -1;
	public String server;
	public int cpsLeft, cpsRight;
	public long memUsed, memMax;
	/** Bound GLFW code per control (CONTROLS order), -1 when unknown. */
	public final int[] keys = new int[CONTROLS.length];
	public final boolean[] down = new boolean[CONTROLS.length];
	public boolean sprintToggled, sneakToggled;
	public float zoom = 1;
	/** GLFW window, and true while in a world with no screen open (keys go to the game). */
	public long window;
	public boolean playing;

	// fps / cps bookkeeping
	private static int frameCount;
	private static long frameWindow;
	private static int fpsValue;
	private static final long[] clicksL = new long[64], clicksR = new long[64];
	private static int headL, headR;
	private final double[] pos = new double[5];
	private double lastX, lastZ;
	private long lastPosAt;
	private int slow;

	public Game() {
		java.util.Arrays.fill(keys, -1);
	}

	/** Called at the start of every frame. */
	public static void countFrame() {
		long now = System.nanoTime();
		if (frameWindow == 0) {
			frameWindow = now;
		}
		frameCount++;
		if (now - frameWindow >= 1_000_000_000L) {
			fpsValue = (int) Math.round(frameCount * 1e9 / (now - frameWindow));
			frameCount = 0;
			frameWindow = now;
		}
	}

	/** A mouse button went down in game (not over a Native screen). */
	public static void click(int button) {
		long now = System.currentTimeMillis();
		if (button == 0) {
			clicksL[headL++ & 63] = now;
		} else if (button == 1) {
			clicksR[headR++ & 63] = now;
		}
	}

	private static int cps(long[] ring, long now) {
		int n = 0;
		for (long t : ring) {
			if (t != 0 && now - t < 1000) {
				n++;
			}
		}
		return n;
	}

	public void update(McBridge mc, long window) {
		sample = false;
		this.window = window;
		fps = fpsValue;
		long now = System.currentTimeMillis();
		cpsLeft = cps(clicksL, now);
		cpsRight = cps(clicksR, now);
		inWorld = mc.inWorld() && mc.player(pos);
		if (inWorld) {
			x = pos[0];
			y = pos[1];
			z = pos[2];
			yaw = (float) pos[3];
			pitch = (float) pos[4];
			long t = System.nanoTime();
			if (lastPosAt != 0) {
				double dt = (t - lastPosAt) / 1e9;
				if (dt >= 0.05) {
					double dx = x - lastX, dz = z - lastZ;
					float v = (float) (Math.sqrt(dx * dx + dz * dz) / dt);
					speed = v > 100 ? speed : speed + (v - speed) * 0.5f; // ignore teleports
					lastX = x;
					lastZ = z;
					lastPosAt = t;
				}
			} else {
				lastX = x;
				lastZ = z;
				lastPosAt = t;
			}
		} else {
			lastPosAt = 0;
			speed = 0;
		}
		// the rest changes slowly: every 10th frame is plenty
		if (slow++ % 10 == 0) {
			Runtime rt = Runtime.getRuntime();
			memMax = rt.maxMemory();
			memUsed = rt.totalMemory() - rt.freeMemory();
			if (inWorld) {
				ping = mc.ping();
				server = mc.server();
				for (int i = 0; i < CONTROLS.length; i++) {
					keys[i] = mc.boundKey(CONTROLS[i]);
				}
			}
		}
		for (int i = 0; i < CONTROLS.length; i++) {
			down[i] = window != 0 && Keys.isDown(window, keys[i]);
		}
	}

	/** Believable values for previews (menu, HUD editor outside a world). */
	public static Game sample() {
		Game g = new Game();
		g.sample = true;
		g.inWorld = true;
		g.fps = 144;
		g.x = 128.5;
		g.y = 64;
		g.z = -342.3;
		g.yaw = 35;
		g.speed = 5.6f;
		g.ping = 38;
		g.server = "play.native.gg";
		g.cpsLeft = 9;
		g.cpsRight = 2;
		Runtime rt = Runtime.getRuntime();
		g.memMax = rt.maxMemory();
		g.memUsed = rt.totalMemory() - rt.freeMemory();
		int[] def = {87, 65, 83, 68, 32, 340, 341, 0, 1};
		System.arraycopy(def, 0, g.keys, 0, def.length);
		long t = System.currentTimeMillis() / 180;
		g.down[FORWARD] = true;
		g.down[ATTACK] = t % 3 == 0;
		g.down[JUMP] = t % 7 == 0;
		return g;
	}

	/** Facing as a compass word (N, NE, ...), from the vanilla yaw (0 = south, 90 = west). */
	public String facing() {
		String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
		float a = ((yaw % 360) + 360) % 360;
		return names[Math.round(a / 45f) & 7];
	}
}
