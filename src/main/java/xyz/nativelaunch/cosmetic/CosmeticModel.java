package xyz.nativelaunch.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A Native cosmetic model ("NCM", format 1): Minecraft-style boxes with box UV, attached to a part of the
 * player model and animated by a few simple, time-driven channels. Coordinates are in model pixels
 * (1/16 block, y pointing down) relative to the vanilla part the root is attached to, exactly like vanilla
 * entity models, so a hat at {@code origin [-4,-9,-4]} sits on top of the head.
 *
 * <pre>
 * {"format":1, "texture":[64,32], "parts":[
 *   {"id":"cap", "attach":"head", "pivot":[0,0,0], "rotation":[0,0,0], "layer":"cutout",
 *    "armor":{"slot":"head","mode":"hide"},
 *    "cubes":[{"origin":[-4,-9,-4], "size":[8,1,8], "uv":[0,0], "inflate":0.25, "mirror":false}],
 *    "anim":[{"type":"spin","axis":"y","speed":540}],
 *    "children":[ ...parts... ]}]}
 * </pre>
 */
public final class CosmeticModel {
	public static final int MAX_PARTS = 512;
	public static final int MAX_CUBES = 2048;
	public static final int MAX_DEPTH = 8;

	public enum Attach { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

	public enum Layer { CUTOUT, TRANSLUCENT, GLOW }

	/** Armor slot a part reacts to. */
	public enum Slot { NONE, HEAD, CHEST, LEGS, FEET, LEFT_HAND, RIGHT_HAND }

	public static final class Cube {
		public final float x, y, z;
		public final float w, h, d;
		public final int u, v;
		public final float inflate;
		public final boolean mirror;

		Cube(float x, float y, float z, float w, float h, float d, int u, int v, float inflate, boolean mirror) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.w = w;
			this.h = h;
			this.d = d;
			this.u = u;
			this.v = v;
			this.inflate = inflate;
			this.mirror = mirror;
		}
	}

	public static final class Anim {
		public enum Type { SPIN, SWING, BOB, BLINK }

		public final Type type;
		/** 0 = x (pitch), 1 = y (yaw), 2 = z (roll). */
		public final int axis;
		/** spin: degrees per second; swing/bob/blink: cycles per second. */
		public final float speed;
		/** swing: degrees; bob: pixels; blink: fraction of the cycle the part is shown (0..1). */
		public final float amplitude;
		/** Extra amplitude while the player walks or runs (swing/bob), scaled by the walk speed. */
		public final float moving;
		/** Offset into the cycle (0..1). */
		public final float phase;

		Anim(Type type, int axis, float speed, float amplitude, float moving, float phase) {
			this.type = type;
			this.axis = axis;
			this.speed = speed;
			this.amplitude = amplitude;
			this.moving = moving;
			this.phase = phase;
		}
	}

	public static final class Part {
		public final String id;
		public final Attach attach;
		public final float px, py, pz;
		/** Rest rotation in radians. */
		public final float rx, ry, rz;
		public final List<Cube> cubes;
		public final List<Part> children;
		public final List<Anim> anims;
		public final Layer layer;
		public final Slot armorSlot;
		/** True: hidden while that armor slot is filled. False: pushed by {@link #armorOffset} instead. */
		public final boolean armorHide;
		public final float[] armorOffset;
		/** Index in {@link CosmeticModel#flat}, handy for per-part caches in renderers. */
		public final int index;
		/** 0 = any, 1 = only when the player picked the left hand/side, 2 = right. */
		public final int side;

		Part(String id, Attach attach, float[] pivot, float[] rotation, List<Cube> cubes, List<Part> children, List<Anim> anims,
				Layer layer, Slot armorSlot, boolean armorHide, float[] armorOffset, int index, int side) {
			this.id = id;
			this.attach = attach;
			this.px = pivot[0];
			this.py = pivot[1];
			this.pz = pivot[2];
			this.rx = (float) Math.toRadians(rotation[0]);
			this.ry = (float) Math.toRadians(rotation[1]);
			this.rz = (float) Math.toRadians(rotation[2]);
			this.cubes = cubes;
			this.children = children;
			this.anims = anims;
			this.layer = layer;
			this.armorSlot = armorSlot;
			this.armorHide = armorHide;
			this.armorOffset = armorOffset;
			this.index = index;
			this.side = side;
		}

		public boolean animated() {
			return !anims.isEmpty();
		}
	}

	public final int textureWidth;
	public final int textureHeight;
	public final List<Part> roots;
	/** Every part, parents before children. */
	public final List<Part> flat;
	/**
	 * A hood, helmet or mask that wraps the whole head: while it is worn the skin's hat layer and the vanilla
	 * helmet / head item are not drawn (they would poke through it).
	 */
	public final boolean coversHead;

	private CosmeticModel(int textureWidth, int textureHeight, List<Part> roots, List<Part> flat) {
		this.textureWidth = textureWidth;
		this.textureHeight = textureHeight;
		this.roots = roots;
		this.flat = flat;
		boolean covers = false;
		for (Part root : roots) {
			if (root.attach == Attach.HEAD && wraps(root, new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1}, new float[3])) {
				covers = true;
				break;
			}
		}
		this.coversHead = covers;
	}

	/**
	 * True when one of the part's (rest pose) cubes is at least head sized and holds the head's centre, i.e. the
	 * head sits inside it. {@code m} / {@code t} map the part's parent space to head space (vanilla ModelPart
	 * order: translate by the pivot, then rotate z, y, x).
	 */
	private static boolean wraps(Part part, float[] m, float[] t) {
		float[] nt = {
				t[0] + m[0] * part.px + m[1] * part.py + m[2] * part.pz,
				t[1] + m[3] * part.px + m[4] * part.py + m[5] * part.pz,
				t[2] + m[6] * part.px + m[7] * part.py + m[8] * part.pz};
		float[] nm = mul(m, mul(rot(2, part.rz), mul(rot(1, part.ry), rot(0, part.rx))));
		// head centre (0, -4, 0) in this part's space: nm is a rotation, so its inverse is its transpose
		float dx = 0 - nt[0], dy = -4 - nt[1], dz = 0 - nt[2];
		float lx = nm[0] * dx + nm[3] * dy + nm[6] * dz;
		float ly = nm[1] * dx + nm[4] * dy + nm[7] * dz;
		float lz = nm[2] * dx + nm[5] * dy + nm[8] * dz;
		for (Cube c : part.cubes) {
			float e = c.inflate;
			if (c.w + 2 * e >= 8 && c.h + 2 * e >= 8 && c.d + 2 * e >= 8
					&& lx >= c.x - e && lx <= c.x + c.w + e && ly >= c.y - e && ly <= c.y + c.h + e && lz >= c.z - e && lz <= c.z + c.d + e) {
				return true;
			}
		}
		for (Part child : part.children) {
			if (wraps(child, nm, nt)) {
				return true;
			}
		}
		return false;
	}

	private static float[] rot(int axis, float a) {
		float c = (float) Math.cos(a), s = (float) Math.sin(a);
		switch (axis) {
			case 0: return new float[] {1, 0, 0, 0, c, -s, 0, s, c};
			case 1: return new float[] {c, 0, s, 0, 1, 0, -s, 0, c};
			default: return new float[] {c, -s, 0, s, c, 0, 0, 0, 1};
		}
	}

	private static float[] mul(float[] a, float[] b) {
		float[] o = new float[9];
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 3; c++) {
				o[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
			}
		}
		return o;
	}

	/** The side of the first side-flagged root (1 left, 2 right), or 0 when the model has none. */
	public int defaultSide() {
		for (Part root : roots) {
			if (root.side != 0) {
				return root.side;
			}
		}
		return 0;
	}

	public static CosmeticModel parse(byte[] json) {
		return parse(new String(json, StandardCharsets.UTF_8));
	}

	/** Parses and validates a model; throws IllegalArgumentException on anything malformed or oversized. */
	public static CosmeticModel parse(String json) {
		JsonElement rootElement = new JsonParser().parse(json);
		if (!rootElement.isJsonObject()) {
			throw new IllegalArgumentException("model is not an object");
		}
		JsonObject root = rootElement.getAsJsonObject();
		int format = root.has("format") ? root.get("format").getAsInt() : 1;
		if (format != 1) {
			throw new IllegalArgumentException("unsupported model format " + format);
		}
		float[] texture = floats(root, "texture", 2, new float[] {64, 64});
		int tw = (int) texture[0];
		int th = (int) texture[1];
		if (tw < 1 || th < 1 || tw > 2048 || th > 2048) {
			throw new IllegalArgumentException("bad texture size");
		}
		if (!root.has("parts") || !root.get("parts").isJsonArray()) {
			throw new IllegalArgumentException("model has no parts");
		}
		List<Part> flat = new ArrayList<Part>();
		int[] cubeCount = {0};
		List<Part> roots = parts(root.getAsJsonArray("parts"), null, 0, flat, cubeCount);
		if (roots.isEmpty()) {
			throw new IllegalArgumentException("model has no parts");
		}
		return new CosmeticModel(tw, th, roots, Collections.unmodifiableList(flat));
	}

	private static List<Part> parts(JsonArray array, Attach inherited, int depth, List<Part> flat, int[] cubeCount) {
		if (depth > MAX_DEPTH) {
			throw new IllegalArgumentException("model nests too deep");
		}
		List<Part> out = new ArrayList<Part>();
		for (JsonElement element : array) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject o = element.getAsJsonObject();
			if (flat.size() >= MAX_PARTS) {
				throw new IllegalArgumentException("too many parts");
			}
			Attach attach = inherited != null ? inherited : attach(text(o, "attach", "head"));
			List<Cube> cubes = new ArrayList<Cube>();
			if (o.has("cubes") && o.get("cubes").isJsonArray()) {
				for (JsonElement c : o.getAsJsonArray("cubes")) {
					if (!c.isJsonObject()) {
						continue;
					}
					if (++cubeCount[0] > MAX_CUBES) {
						throw new IllegalArgumentException("too many cubes");
					}
					cubes.add(cube(c.getAsJsonObject()));
				}
			}
			List<Anim> anims = new ArrayList<Anim>();
			if (o.has("anim") && o.get("anim").isJsonArray()) {
				for (JsonElement a : o.getAsJsonArray("anim")) {
					if (a.isJsonObject() && anims.size() < 8) {
						Anim anim = anim(a.getAsJsonObject());
						if (anim != null) {
							anims.add(anim);
						}
					}
				}
			}
			Slot armorSlot = Slot.NONE;
			boolean armorHide = true;
			float[] armorOffset = {0, 0, 0};
			if (o.has("armor") && o.get("armor").isJsonObject()) {
				JsonObject armor = o.getAsJsonObject("armor");
				armorSlot = slot(text(armor, "slot", "none"));
				armorHide = !"push".equals(text(armor, "mode", "hide"));
				armorOffset = clampAll(floats(armor, "offset", 3, armorOffset), 8);
			}
			int index = flat.size();
			Part placeholder = null;
			flat.add(placeholder);
			List<Part> children = o.has("children") && o.get("children").isJsonArray()
					? parts(o.getAsJsonArray("children"), attach, depth + 1, flat, cubeCount)
					: Collections.<Part>emptyList();
			Part part = new Part(text(o, "id", "part" + index), attach,
					clampAll(floats(o, "pivot", 3, new float[] {0, 0, 0}), 64),
					clampAll(floats(o, "rotation", 3, new float[] {0, 0, 0}), 360),
					Collections.unmodifiableList(cubes), children, Collections.unmodifiableList(anims),
					layer(text(o, "layer", o.has("glow") && o.get("glow").getAsBoolean() ? "glow" : "cutout")),
					armorSlot, armorHide, armorOffset, index, side(text(o, "side", "")));
			flat.set(index, part);
			out.add(part);
		}
		return Collections.unmodifiableList(out);
	}

	private static Cube cube(JsonObject o) {
		float[] origin = clampAll(floats(o, "origin", 3, new float[] {0, 0, 0}), 64);
		float[] size = floats(o, "size", 3, new float[] {1, 1, 1});
		float[] s = new float[3];
		for (int i = 0; i < 3; i++) {
			s[i] = Math.max(0f, Math.min(64f, size[i]));
		}
		float[] uv = floats(o, "uv", 2, new float[] {0, 0});
		float inflate = o.has("inflate") ? Math.max(-2f, Math.min(4f, o.get("inflate").getAsFloat())) : 0f;
		boolean mirror = o.has("mirror") && o.get("mirror").getAsBoolean();
		return new Cube(origin[0], origin[1], origin[2], s[0], s[1], s[2],
				Math.max(0, Math.min(2048, (int) uv[0])), Math.max(0, Math.min(2048, (int) uv[1])), inflate, mirror);
	}

	private static Anim anim(JsonObject o) {
		Anim.Type type;
		String name = text(o, "type", "").toLowerCase(Locale.ROOT);
		if ("spin".equals(name)) {
			type = Anim.Type.SPIN;
		} else if ("swing".equals(name) || "flap".equals(name)) {
			type = Anim.Type.SWING;
		} else if ("bob".equals(name)) {
			type = Anim.Type.BOB;
		} else if ("blink".equals(name)) {
			type = Anim.Type.BLINK;
		} else {
			return null;
		}
		String axisName = text(o, "axis", type == Anim.Type.BOB ? "y" : "y");
		int axis = "x".equals(axisName) ? 0 : "z".equals(axisName) ? 2 : 1;
		float speed = num(o, "speed", type == Anim.Type.SPIN ? 90 : 1, 4000);
		float amplitude = num(o, "amplitude", type == Anim.Type.BLINK ? 0.5f : 10, 360);
		float moving = num(o, "moving", 0, 360);
		float phase = num(o, "phase", 0, 1000);
		return new Anim(type, axis, speed, amplitude, moving, phase);
	}

	private static Attach attach(String name) {
		String n = name.toLowerCase(Locale.ROOT).replace("_", "");
		if ("body".equals(n) || "back".equals(n) || "torso".equals(n)) {
			return Attach.BODY;
		}
		if ("rightarm".equals(n)) {
			return Attach.RIGHT_ARM;
		}
		if ("leftarm".equals(n)) {
			return Attach.LEFT_ARM;
		}
		if ("rightleg".equals(n) || "rightfoot".equals(n)) {
			return Attach.RIGHT_LEG;
		}
		if ("leftleg".equals(n) || "leftfoot".equals(n)) {
			return Attach.LEFT_LEG;
		}
		return Attach.HEAD;
	}

	private static Layer layer(String name) {
		if ("glow".equals(name) || "emissive".equals(name)) {
			return Layer.GLOW;
		}
		if ("translucent".equals(name)) {
			return Layer.TRANSLUCENT;
		}
		return Layer.CUTOUT;
	}

	private static int side(String name) {
		return "left".equals(name) ? 1 : "right".equals(name) ? 2 : 0;
	}

	private static Slot slot(String name) {
		name = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
		if ("lefthand".equals(name) || "offhand".equals(name)) {
			return Slot.LEFT_HAND;
		}
		if ("righthand".equals(name) || "mainhand".equals(name)) {
			return Slot.RIGHT_HAND;
		}
		if ("head".equals(name) || "helmet".equals(name)) {
			return Slot.HEAD;
		}
		if ("chest".equals(name)) {
			return Slot.CHEST;
		}
		if ("legs".equals(name)) {
			return Slot.LEGS;
		}
		if ("feet".equals(name) || "boots".equals(name)) {
			return Slot.FEET;
		}
		return Slot.NONE;
	}

	private static String text(JsonObject o, String key, String fallback) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
	}

	private static float num(JsonObject o, String key, float fallback, float limit) {
		if (!o.has(key) || !o.get(key).isJsonPrimitive()) {
			return fallback;
		}
		float value = o.get(key).getAsFloat();
		if (Float.isNaN(value) || Float.isInfinite(value)) {
			return fallback;
		}
		return Math.max(-limit, Math.min(limit, value));
	}

	private static float[] floats(JsonObject o, String key, int n, float[] fallback) {
		if (!o.has(key) || !o.get(key).isJsonArray()) {
			return fallback.clone();
		}
		JsonArray array = o.getAsJsonArray(key);
		float[] out = fallback.clone();
		for (int i = 0; i < n && i < array.size(); i++) {
			float value = array.get(i).getAsFloat();
			out[i] = Float.isNaN(value) || Float.isInfinite(value) ? fallback[i] : value;
		}
		return out;
	}

	private static float[] clampAll(float[] values, float limit) {
		for (int i = 0; i < values.length; i++) {
			values[i] = Math.max(-limit, Math.min(limit, values[i]));
		}
		return values;
	}
}
