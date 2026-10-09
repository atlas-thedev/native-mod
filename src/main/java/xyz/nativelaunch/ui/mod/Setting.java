package xyz.nativelaunch.ui.mod;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** One option of a module, shown in its settings panel and saved to config/native-modules.json. */
public abstract class Setting {
	public final String id, label;

	Setting(String id, String label) {
		this.id = id;
		this.label = label;
	}

	abstract JsonElement save();

	abstract void load(JsonElement e);

	abstract void reset();

	public static final class Bool extends Setting {
		public boolean value;
		private final boolean def;

		public Bool(String id, String label, boolean def) {
			super(id, label);
			this.value = this.def = def;
		}

		JsonElement save() {
			return new JsonPrimitive(value);
		}

		void load(JsonElement e) {
			value = e.getAsBoolean();
		}

		void reset() {
			value = def;
		}
	}

	public static final class Num extends Setting {
		public float value;
		public final float min, max, step;
		public final String suffix;
		private final float def;

		public Num(String id, String label, float def, float min, float max, float step, String suffix) {
			super(id, label);
			this.value = this.def = def;
			this.min = min;
			this.max = max;
			this.step = step;
			this.suffix = suffix;
		}

		public void set(float v) {
			v = Math.max(min, Math.min(max, v));
			value = Math.round(v / step) * step;
		}

		public String text() {
			float v = value;
			String n = step >= 1 ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.ROOT, step >= 0.1f ? "%.1f" : "%.2f", v);
			return n + suffix;
		}

		JsonElement save() {
			return new JsonPrimitive(value);
		}

		void load(JsonElement e) {
			set(e.getAsFloat());
		}

		void reset() {
			value = def;
		}
	}

	public static final class Color extends Setting {
		public int value;
		private final int def;

		public Color(String id, String label, int def) {
			super(id, label);
			this.value = this.def = def;
		}

		JsonElement save() {
			return new JsonPrimitive(String.format("#%08X", value));
		}

		void load(JsonElement e) {
			String s = e.getAsString().replace("#", "");
			value = (int) Long.parseLong(s.length() == 6 ? "FF" + s : s, 16);
		}

		void reset() {
			value = def;
		}
	}

	public static final class Choice extends Setting {
		public int value;
		public final String[] options;
		private final int def;

		public Choice(String id, String label, int def, String... options) {
			super(id, label);
			this.value = this.def = def;
			this.options = options;
		}

		JsonElement save() {
			return new JsonPrimitive(options[value]);
		}

		void load(JsonElement e) {
			String s = e.getAsString();
			for (int i = 0; i < options.length; i++) {
				if (options[i].equals(s)) {
					value = i;
				}
			}
		}

		void reset() {
			value = def;
		}
	}

	public static final class Key extends Setting {
		public int value;
		private final int def;

		public Key(String id, String label, int def) {
			super(id, label);
			this.value = this.def = def;
		}

		JsonElement save() {
			return new JsonPrimitive(value);
		}

		void load(JsonElement e) {
			value = e.getAsInt();
		}

		void reset() {
			value = def;
		}
	}
}
