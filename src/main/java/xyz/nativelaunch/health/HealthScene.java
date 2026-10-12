package xyz.nativelaunch.health;

/**
 * What the game looks like this frame, as plain numbers: the camera and every living thing worth an indicator.
 * Filled by the per-version reader ({@code McBridge.health}); everything else works from this, so the drawing and the
 * rules are the same on every Minecraft version. Reused between frames (no allocation).
 */
public final class HealthScene {
	public static final int CAPACITY = 160;
	/** Entities farther than this from the camera are not read at all. */
	public static final double MAX_DISTANCE = 128.0;

	/** False when the reader could not read this version / there is no world. */
	public boolean ok;
	public double camX, camY, camZ;
	/** Minecraft yaw / pitch in degrees, and the vertical field of view in degrees. */
	public float camYaw, camPitch, fov = 70f;
	public boolean hardcore;
	/** True for the one frame where the local player was just hurt (readers that cannot see who did it). */
	public boolean playerHurt;
	public int count;
	public final Entry[] entries = new Entry[CAPACITY];

	public HealthScene() {
		for (int i = 0; i < CAPACITY; i++) {
			entries[i] = new Entry();
		}
	}

	public void reset() {
		ok = false;
		playerHurt = false;
		count = 0;
	}

	/** The next free entry, or null when full. */
	public Entry add() {
		if (count >= CAPACITY) {
			return null;
		}
		Entry e = entries[count++];
		e.clear();
		return e;
	}

	/** One living thing. Positions are already interpolated for the frame. */
	public static final class Entry {
		public int id;
		/** "minecraft:zombie" */
		public String type = "";
		/** Only filled for players. */
		public String name = "";
		public double x, y, z;
		public float width = 0.6f, height = 1.8f;
		public float health = 20, max = 20, absorb;
		public int armor;
		/** Alive, not a spectator, not invisible to us, not the thing we ride. */
		public boolean valid;
		public boolean player, self, hostile, passive;
		/** Not behind blocks (line of sight from the player). */
		public boolean visible;
		/** A name tag floats above it. */
		public boolean label;
		public boolean regen;
		/** 0 normal, 1 poisoned, 2 withered, 3 frozen */
		public int kind;

		void clear() {
			id = 0;
			type = "";
			name = "";
			valid = player = self = hostile = passive = visible = label = regen = false;
			kind = 0;
			armor = 0;
			absorb = 0;
		}
	}
}
