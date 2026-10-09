package xyz.nativelaunch.ui;

import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immediate-mode UI context: each frame the views describe what is on screen and get back what the player did
 * (hover, click, typing). Widget state that must survive between frames (animations, scroll offsets, text
 * fields) is kept in small maps keyed by a string id.
 */
public final class Ui {
	public static final int KEY_ESCAPE = 256, KEY_ENTER = 257, KEY_TAB = 258, KEY_BACKSPACE = 259, KEY_DELETE = 261,
			KEY_RIGHT = 262, KEY_LEFT = 263, KEY_DOWN = 264, KEY_UP = 265, KEY_PAGE_UP = 266, KEY_PAGE_DOWN = 267,
			KEY_HOME = 268, KEY_END = 269, KEY_KP_ENTER = 335, KEY_A = 65, KEY_C = 67, KEY_V = 86, KEY_X = 88;
	public static final int MOD_SHIFT = 1, MOD_CONTROL = 2, MOD_ALT = 4, MOD_SUPER = 8;

	public final Canvas c;
	public float mx = -1, my = -1;
	public boolean down, pressed, released, rightPressed;
	public float scroll;
	public final List<Integer> chars = new ArrayList<Integer>();
	public final List<int[]> keys = new ArrayList<int[]>(); // {key, mods}
	public float dt;
	public long now;
	private long last;
	public String active; // id the mouse went down on
	public String focus;  // id of the focused text field
	private final Map<String, float[]> anims = new HashMap<String, float[]>();
	/** Cleared while a modal layer is open so the content beneath does not react. */
	public boolean interactive = true;
	public boolean cursorText, cursorHand;
	public boolean focusClaimed;
	public Clipboard clipboard;

	public interface Clipboard {
		String get();

		void set(String text);
	}

	public Ui(Canvas c) {
		this.c = c;
	}

	public void begin(List<Input.Event> events) {
		long t = System.nanoTime();
		dt = last == 0 ? 0.016f : Math.min(0.1f, (t - last) / 1e9f);
		last = t;
		now = System.currentTimeMillis();
		pressed = released = rightPressed = false;
		scroll = 0;
		chars.clear();
		keys.clear();
		cursorText = cursorHand = false;
		interactive = true;
		float s = c.scale;
		for (Input.Event e : events) {
			switch (e.type) {
				case Input.MOVE:
					mx = (float) (e.x / s);
					my = (float) (e.y / s);
					break;
				case Input.BUTTON:
					mx = (float) (e.x / s);
					my = (float) (e.y / s);
					if (e.code == 0) {
						if (e.action == 1) {
							down = true;
							pressed = true;
						} else {
							down = false;
							released = true;
						}
					} else if (e.code == 1 && e.action == 1) {
						rightPressed = true;
					}
					break;
				case Input.SCROLL:
					scroll += (float) e.y;
					break;
				case Input.KEY:
					if (e.action != 0) {
						keys.add(new int[] {e.code, e.mods});
					}
					break;
				case Input.CHAR:
					chars.add(e.code);
					break;
				default:
					break;
			}
		}
		if (pressed) {
			active = null;
		}
		focusClaimed = false;
	}

	/** Call after the views drew: forgets the pressed widget once the button is up. */
	public void end() {
		if (pressed && !focusClaimed) {
			focus = null;
		}
		if (!down && !pressed) {
			active = null;
		}
	}

	public boolean hover(float x, float y, float w, float h) {
		return interactive && mx >= x && mx < x + w && my >= y && my < y + h && c.inClip(mx, my);
	}

	/**
	 * A clickable region: true on the frame the button is released over it after being pressed on it.
	 */
	public boolean clicked(String id, float x, float y, float w, float h) {
		boolean over = hover(x, y, w, h);
		if (over) {
			cursorHand = true;
		}
		if (over && pressed) {
			active = id;
		}
		return over && released && id.equals(active);
	}

	public boolean isPressing(String id) {
		return down && id.equals(active);
	}

	public boolean key(int key) {
		for (int[] k : keys) {
			if (k[0] == key) {
				return true;
			}
		}
		return false;
	}

	/** Eases a value towards target; speed ~ 1/seconds. */
	public float anim(String id, float target, float speed) {
		float[] v = anims.get(id);
		if (v == null) {
			v = new float[] {target};
			anims.put(id, v);
			return target;
		}
		float k = 1f - (float) Math.exp(-speed * dt);
		v[0] += (target - v[0]) * k;
		if (Math.abs(target - v[0]) < 0.001f) {
			v[0] = target;
		}
		return v[0];
	}

	public float anim(String id, boolean on, float speed) {
		return anim(id, on ? 1f : 0f, speed);
	}

	public void setAnim(String id, float value) {
		anims.put(id, new float[] {value});
	}

	public boolean animating() {
		return false;
	}

	// ── common widgets ───────────────────────────────────────────────────

	public static final int BTN_GLASS = 0, BTN_PRIMARY = 1, BTN_GHOST = 2, BTN_DANGER = 3;

	/** A launcher-style button; returns true when clicked. */
	public boolean button(String id, float x, float y, float w, float h, String label, int icon, int style) {
		boolean over = hover(x, y, w, h);
		boolean click = clicked(id, x, y, w, h);
		float hv = anim(id + "#h", over, 14f);
		float pr = anim(id + "#p", isPressing(id), 20f);
		float r = Math.min(10, h / 2);
		int bg, border, fg;
		switch (style) {
			case BTN_PRIMARY:
				bg = Theme.mix(0xFFF4F4F5, 0xFFFFFFFF, hv);
				bg = Theme.mix(bg, 0xFFD4D4D8, pr);
				border = 0;
				fg = Theme.SOLID_FG;
				break;
			case BTN_GHOST:
				bg = Theme.alpha(Theme.SUBTLE_HOVER, hv);
				border = 0;
				fg = Theme.mix(Theme.TEXT_SECONDARY, Theme.TEXT_STRONG, hv);
				break;
			case BTN_DANGER:
				bg = Theme.mix(0x26EF4444, 0x40EF4444, hv);
				border = 0x4DEF4444;
				fg = 0xFFFCA5A5;
				break;
			default:
				bg = Theme.mix(0xB808090C, 0xD80E1014, hv);
				bg = Theme.mix(bg, 0xE014171D, pr);
				border = Theme.mix(Theme.HAIRLINE, Theme.BORDER_HOVER, hv);
				fg = Theme.mix(Theme.TEXT, Theme.TEXT_STRONG, hv);
				break;
		}
		c.round(x, y, w, h, r, bg);
		if (border != 0) {
			c.outline(x, y, w, h, r, 1, border);
		}
		float size = h >= 40 ? 14 : 12.5f;
		float tw = label == null ? 0 : c.textWidth(Fonts.SEMIBOLD, size, label);
		float iconW = icon != 0 ? size + 4 + (label == null || label.isEmpty() ? -4 : 6) : 0;
		float total = tw + iconW;
		float tx = x + (w - total) / 2;
		if (icon != 0) {
			c.icon(icon, size + 3, tx + (size + 4) / 2, y + h / 2, fg);
		}
		if (label != null && !label.isEmpty()) {
			c.text(Fonts.SEMIBOLD, size, label, tx + iconW, y + (h - c.lineHeight(Fonts.SEMIBOLD, size)) / 2, fg);
		}
		return click;
	}

	/** Square icon-only button. */
	public boolean iconButton(String id, float x, float y, float size, int icon, String tooltip) {
		boolean over = hover(x, y, size, size);
		boolean click = clicked(id, x, y, size, size);
		float hv = anim(id + "#h", over, 16f);
		c.round(x, y, size, size, Math.min(8, size / 2), Theme.alpha(Theme.SUBTLE_HOVER, hv));
		c.icon(icon, size * 0.5f, x + size / 2, y + size / 2, Theme.mix(Theme.TEXT_MUTED, Theme.TEXT_STRONG, hv));
		if (over && tooltip != null) {
			pendingTooltip = tooltip;
		}
		return click;
	}

	/** A pill toggle switch. */
	public boolean toggle(String id, float x, float y, boolean on) {
		float w = 34, h = 20;
		boolean click = clicked(id, x, y, w, h);
		float t = anim(id + "#t", on, 14f);
		c.round(x, y, w, h, h / 2, Theme.mix(0x33FFFFFF, 0xFFF4F4F5, t));
		float k = h - 6;
		c.circle(x + 3 + k / 2 + t * (w - 6 - k), y + h / 2, k / 2, Theme.mix(0xFFD4D4D8, Theme.SOLID_FG, t));
		return click;
	}

	private String pendingTooltip;

	/** Draws the tooltip requested this frame, if any (call last). */
	public void tooltips() {
		if (pendingTooltip == null) {
			return;
		}
		float size = 11.5f;
		float w = c.textWidth(Fonts.MEDIUM, size, pendingTooltip) + 16;
		float h = 24;
		float x = Math.min(mx + 12, c.width() - w - 4), y = Math.min(my + 16, c.height() - h - 4);
		c.shadow(x, y + 2, w, h, 6, 8, 0x66000000);
		c.round(x, y, w, h, 6, 0xF0141418);
		c.outline(x, y, w, h, 6, 1, Theme.HAIRLINE_STRONG);
		c.text(Fonts.MEDIUM, size, pendingTooltip, x + 8, y + (h - c.lineHeight(Fonts.MEDIUM, size)) / 2, Theme.TEXT);
		pendingTooltip = null;
	}

	/** Spinner made of three pulsing dots. */
	public void dots(float cx, float cy, int color) {
		for (int i = 0; i < 3; i++) {
			float p = (float) (0.5 + 0.5 * Math.sin(now / 160.0 - i * 0.9));
			c.circle(cx + (i - 1) * 9, cy, 2.6f, Theme.alpha(color, 0.35f + 0.65f * p));
		}
	}
}
