package xyz.nativelaunch.ui;

import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.ui.mod.Keys;
import xyz.nativelaunch.ui.mod.Modules;
import xyz.nativelaunch.ui.mod.Setting;

/** Setting widgets in the launcher look: slider, segmented choice, colour swatches, keybind button. */
public final class Widgets {
	/** Id of the keybind button waiting for a key, or null. */
	public static String listening;
	public static final int[] SWATCHES = {0xFFFFFFFF, 0xFFD9A6DA, 0xFF60A5FA, 0xFF22C55E, 0xFFF59E0B, 0xFFEF4444, 0xFFA78BFA, 0xFF2DD4BF};
	public static final float ROW = 40;

	private Widgets() {
	}

	/** Esc while a keybind listens: cancels it. True when consumed. */
	public static boolean escape() {
		if (listening != null) {
			listening = null;
			return true;
		}
		return false;
	}

	/** Horizontal slider for a Num; true while the value changed this frame. */
	public static boolean slider(Ui ui, String id, float x, float y, float w, Setting.Num n) {
		Canvas c = ui.c;
		float h = 18;
		boolean over = ui.hover(x - 6, y, w + 12, h);
		ui.clicked(id, x - 6, y, w + 12, h);
		boolean drag = ui.isPressing(id);
		boolean changed = false;
		if (drag) {
			float t = Math.max(0, Math.min(1, (ui.mx - x) / w));
			float old = n.value;
			n.set(n.min + t * (n.max - n.min));
			changed = old != n.value;
		}
		float t = (n.value - n.min) / (n.max - n.min);
		float shown = ui.anim(id + "#v", t, drag ? 40f : 18f);
		float hv = ui.anim(id + "#h", over || drag, 14f);
		float ty = y + h / 2 - 2;
		c.round(x, ty, w, 4, 2, Theme.SUBTLE_HOVER);
		c.round(x, ty, Math.max(4, w * shown), 4, 2, 0xFFF4F4F5);
		float kr = 7 + hv * 1.5f;
		float kx = x + w * shown;
		c.circle(kx, y + h / 2, kr + 3, Theme.alpha(0xFFFFFFFF, 0.08f * hv));
		c.circle(kx, y + h / 2, kr, 0xFFFFFFFF);
		if (over || drag) {
			ui.cursorHand = true;
		}
		return changed;
	}

	/** A pill group; returns the (possibly new) selected index. */
	public static int segmented(Ui ui, String id, float x, float y, float w, float h, String[] options, int value) {
		Canvas c = ui.c;
		c.round(x, y, w, h, h / 2, Theme.SUBTLE);
		c.outline(x, y, w, h, h / 2, 1, Theme.HAIRLINE);
		float seg = (w - 4) / options.length;
		float sel = ui.anim(id + "#s", value, 18f);
		c.round(x + 2 + sel * seg, y + 2, seg, h - 4, (h - 4) / 2, 0xFFF4F4F5);
		int out = value;
		for (int i = 0; i < options.length; i++) {
			float sx = x + 2 + i * seg;
			boolean over = ui.hover(sx, y, seg, h);
			if (ui.clicked(id + ":" + i, sx, y, seg, h)) {
				out = i;
			}
			float on = Math.max(0, 1 - Math.abs(sel - i));
			int fg = Theme.mix(over ? Theme.TEXT_STRONG : Theme.TEXT_SECONDARY, Theme.SOLID_FG, on);
			float fs = 11.5f;
			String t = c.ellipsize(Fonts.SEMIBOLD, fs, options[i], seg - 6);
			c.text(Fonts.SEMIBOLD, fs, t, sx + (seg - c.textWidth(Fonts.SEMIBOLD, fs, t)) / 2, y + (h - c.lineHeight(Fonts.SEMIBOLD, fs)) / 2, fg);
		}
		return out;
	}

	public static float swatchesWidth() {
		return SWATCHES.length * 22 - 4;
	}

	/** Eight preset colours; true when one was picked. */
	public static boolean swatches(Ui ui, String id, float x, float y, Setting.Color col) {
		Canvas c = ui.c;
		boolean changed = false;
		for (int i = 0; i < SWATCHES.length; i++) {
			float sx = x + i * 22;
			int sw = SWATCHES[i];
			boolean sel = (sw | 0xFF000000) == (col.value | 0xFF000000);
			boolean over = ui.hover(sx, y, 18, 18);
			if (ui.clicked(id + ":" + i, sx, y, 18, 18)) {
				col.value = (col.value & 0xFF000000) == 0 ? sw : (col.value & 0xFF000000) | (sw & 0xFFFFFF);
				changed = true;
			}
			float hv = ui.anim(id + ":" + i + "#h", over || sel, 16f);
			if (hv > 0.01f) {
				c.circle(sx + 9, y + 9, 9 + 2 * hv, Theme.alpha(0xFFFFFFFF, sel ? 0.9f : 0.25f * hv));
				c.circle(sx + 9, y + 9, 9 + 0.5f * hv, 0xFF08090C);
			}
			c.circle(sx + 9, y + 9, 7, sw);
		}
		return changed;
	}

	/** Keybind button; returns the new GLFW code (or the same one). Backspace unbinds (-1), Esc cancels. */
	public static int keybind(Ui ui, String id, float x, float y, float w, float h, int value) {
		Canvas c = ui.c;
		boolean wait = id.equals(listening);
		int out = value;
		if (wait) {
			for (int[] k : ui.keys) {
				int code = k[0];
				if (code == Ui.KEY_ESCAPE) {
					continue;
				}
				out = code == Ui.KEY_BACKSPACE ? -1 : code;
				listening = null;
				wait = false;
				break;
			}
		}
		boolean over = ui.hover(x, y, w, h);
		if (ui.clicked(id, x, y, w, h)) {
			listening = wait ? null : id;
			wait = !wait;
		}
		float hv = ui.anim(id + "#h", over, 14f);
		float lv = ui.anim(id + "#l", wait, 12f);
		c.round(x, y, w, h, 8, Theme.mix(Theme.mix(0xB808090C, 0xD80E1014, hv), 0x26D9A6DA, lv));
		c.outline(x, y, w, h, 8, 1, Theme.mix(Theme.mix(Theme.HAIRLINE_STRONG, Theme.BORDER_HOVER, hv), Theme.alpha(Theme.ACCENT, 0.7f), lv));
		String t = wait ? "Press a key\u2026" : Keys.name(out);
		float fs = 11.5f;
		t = c.ellipsize(Fonts.SEMIBOLD, fs, t, w - 12);
		int fg = wait ? Theme.alpha(Theme.ACCENT, 0.6f + 0.4f * (float) (0.5 + 0.5 * Math.sin(ui.now / 220.0))) : Theme.TEXT_STRONG;
		c.text(Fonts.SEMIBOLD, fs, t, x + (w - c.textWidth(Fonts.SEMIBOLD, fs, t)) / 2, y + (h - c.lineHeight(Fonts.SEMIBOLD, fs)) / 2, fg);
		return out;
	}

	/** Height a setting row takes. */
	public static float rowHeight(Setting s) {
		return ROW;
	}

	/**
	 * One labelled setting row (label left, control right). Marks the modules dirty when something changed.
	 * Returns true when the value changed.
	 */
	public static boolean row(Ui ui, String id, float x, float y, float w, Setting s) {
		Canvas c = ui.c;
		float h = ROW;
		float fs = 12.5f;
		c.text(Fonts.MEDIUM, fs, s.label, x, y + (h - c.lineHeight(Fonts.MEDIUM, fs)) / 2, Theme.TEXT);
		boolean changed = false;
		if (s instanceof Setting.Bool) {
			Setting.Bool b = (Setting.Bool) s;
			if (ui.toggle(id, x + w - 34, y + (h - 20) / 2, b.value)) {
				b.value = !b.value;
				changed = true;
			}
		} else if (s instanceof Setting.Num) {
			Setting.Num n = (Setting.Num) s;
			String v = n.text();
			float vw = 46;
			float sw = Math.min(170, w * 0.45f);
			c.text(Fonts.SEMIBOLD, 11.5f, v, x + w - c.textWidth(Fonts.SEMIBOLD, 11.5f, v), y + (h - c.lineHeight(Fonts.SEMIBOLD, 11.5f)) / 2,
					Theme.TEXT_SECONDARY);
			changed = slider(ui, id, x + w - vw - sw, y + (h - 18) / 2, sw, n);
		} else if (s instanceof Setting.Choice) {
			Setting.Choice ch = (Setting.Choice) s;
			float sw = 0;
			for (String o : ch.options) {
				sw += c.textWidth(Fonts.SEMIBOLD, 11.5f, o) + 22;
			}
			sw = Math.min(Math.max(sw, 90), w * 0.68f);
			int v = segmented(ui, id, x + w - sw, y + (h - 26) / 2, sw, 26, ch.options, ch.value);
			if (v != ch.value) {
				ch.value = v;
				changed = true;
			}
		} else if (s instanceof Setting.Color) {
			changed = swatches(ui, id, x + w - swatchesWidth(), y + (h - 18) / 2, (Setting.Color) s);
		} else if (s instanceof Setting.Key) {
			Setting.Key k = (Setting.Key) s;
			int v = keybind(ui, id, x + w - 110, y + (h - 28) / 2, 110, 28, k.value);
			if (v != k.value) {
				k.value = v;
				changed = true;
			}
		}
		if (changed) {
			Modules.changed();
		}
		return changed;
	}
}
