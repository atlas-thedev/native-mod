package xyz.nativelaunch.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Input events for the Native UI, in framebuffer pixels and GLFW key codes. Filled by whichever bridge owns the
 * window (GLFW callbacks on 1.16 - 26.2, Minecraft's screen events on 26.3+) and drained once per frame.
 */
public final class Input {
	public static final int MOVE = 0, BUTTON = 1, SCROLL = 2, KEY = 3, CHAR = 4;

	public static final class Event {
		public final int type;
		public final double x, y;
		public final int code, action, mods;

		Event(int type, double x, double y, int code, int action, int mods) {
			this.type = type;
			this.x = x;
			this.y = y;
			this.code = code;
			this.action = action;
			this.mods = mods;
		}
	}

	private static final List<Event> queue = new ArrayList<Event>();
	public static volatile double mouseX, mouseY;

	private Input() {
	}

	public static synchronized void move(double x, double y) {
		mouseX = x;
		mouseY = y;
		if (!queue.isEmpty() && queue.get(queue.size() - 1).type == MOVE) {
			queue.set(queue.size() - 1, new Event(MOVE, x, y, 0, 0, 0));
		} else {
			add(new Event(MOVE, x, y, 0, 0, 0));
		}
	}

	/** action: 1 press, 0 release */
	public static synchronized void button(int button, int action, int mods) {
		add(new Event(BUTTON, mouseX, mouseY, button, action, mods));
	}

	public static synchronized void scroll(double dx, double dy) {
		add(new Event(SCROLL, dx, dy, 0, 0, 0));
	}

	/** action: 1 press, 2 repeat, 0 release */
	public static synchronized void key(int key, int action, int mods) {
		add(new Event(KEY, 0, 0, key, action, mods));
	}

	public static synchronized void character(int codepoint) {
		add(new Event(CHAR, 0, 0, codepoint, 0, 0));
	}

	private static void add(Event e) {
		if (queue.size() < 512) {
			queue.add(e);
		}
	}

	public static synchronized List<Event> drain() {
		if (queue.isEmpty()) {
			return java.util.Collections.emptyList();
		}
		List<Event> out = new ArrayList<Event>(queue);
		queue.clear();
		return out;
	}

	public static synchronized void clear() {
		queue.clear();
	}
}
