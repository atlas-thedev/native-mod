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
	public static final int[] SWATCHES = {0xFFFFFFFF, 0xFFD9A6DA, 0xFF60A5FA, 0xFF22C55E, 0xFFF59E0B, 0xFFEF4444, 0xFFA78BFA, 0xFF2DD4BF, 0xFF000000};
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
		float fs = 11.5f;
		// segments are sized to their text, so a long option is never cut off
		float[] seg = new float[options.length];
		float total = 0;
		for (int i = 0; i < options.length; i++) {
			seg[i] = c.textWidth(Fonts.SEMIBOLD, fs, options[i]) + 20;
			total += seg[i];
		}
		float inner = w - 4, k = total > 0 ? inner / total : 1;
		float[] sx = new float[options.length + 1];
		sx[0] = x + 2;
		for (int i = 0; i < options.length; i++) {
			seg[i] *= k;
			sx[i + 1] = sx[i] + seg[i];
		}
		float sel = ui.anim(id + "#s", value, 18f);
		int lo = Math.max(0, Math.min(options.length - 1, (int) Math.floor(sel)));
		int hi = Math.max(0, Math.min(options.length - 1, lo + 1));
		float t = sel - lo;
		float px = sx[lo] + (sx[hi] - sx[lo]) * t;
		float pw = seg[lo] + (seg[hi] - seg[lo]) * t;
		c.round(px, y + 2, pw, h - 4, (h - 4) / 2, 0xFFF4F4F5);
		int out = value;
		for (int i = 0; i < options.length; i++) {
			boolean over = ui.hover(sx[i], y, seg[i], h);
			if (ui.clicked(id + ":" + i, sx[i], y, seg[i], h)) {
				out = i;
			}
			float on = Math.max(0, 1 - Math.abs(sel - i));
			int fg = Theme.mix(over ? Theme.TEXT_STRONG : Theme.TEXT_SECONDARY, Theme.SOLID_FG, on);
			String label = c.ellipsize(Fonts.SEMIBOLD, fs, options[i], seg[i] - 4);
			c.text(Fonts.SEMIBOLD, fs, label, sx[i] + (seg[i] - c.textWidth(Fonts.SEMIBOLD, fs, label)) / 2,
					y + (h - c.lineHeight(Fonts.SEMIBOLD, fs)) / 2, fg);
		}
		return out;
	}

	public static float swatchesWidth() {
		return SWATCHES.length * 22 - 4;
	}

	/** Preset colours; true when one was picked. */
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
			c.circle(sx + 9, y + 9, 7.6f, 0x33FFFFFF);
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
		if (s == openDevice) {
			return ROW + 30 * (1 + ((Setting.Device) s).options.get().size()) + 8;
		}
		return ROW;
	}

	/** The device list that is unfolded under its row, or null. */
	private static Setting openDevice;
	private static final float LEVEL_MIN = -70;

	private static float deviceRow(Ui ui, String id, float x, float y, float w, Setting.Device d, float labelW) {
		Canvas c = ui.c;
		float bw = Math.max(120, Math.min(240, w - labelW - 40)), bh = 28, bx = x + w - bw, by = y + (ROW - bh) / 2;
		boolean open = openDevice == d;
		java.util.List<String> list = d.options.get();
		String current = d.value.isEmpty() ? d.emptyLabel : d.value;
		boolean missing = !d.value.isEmpty() && !list.contains(d.value);
		boolean over = ui.hover(bx, by, bw, bh);
		c.round(bx, by, bw, bh, 8, over || open ? 0x24FFFFFF : 0x14FFFFFF);
		c.outline(bx, by, bw, bh, 8, 1, open ? 0x40FFFFFF : Theme.HAIRLINE);
		c.text(Fonts.SEMIBOLD, 11.5f, c.ellipsize(Fonts.SEMIBOLD, 11.5f, missing ? current + " (unplugged)" : current, bw - 34), bx + 10,
				by + (bh - c.lineHeight(Fonts.SEMIBOLD, 11.5f)) / 2, missing ? Theme.DANGER : Theme.TEXT_STRONG);
		c.icon(open ? Theme.I_CHEVRON_UP : Theme.I_CHEVRON_DOWN, 12, bx + bw - 14, by + bh / 2, Theme.TEXT_MUTED);
		if (ui.clicked(id, bx, by, bw, bh)) {
			openDevice = open ? null : d;
		}
		if (open) {
			float ly = y + ROW, lw = w;
			for (int i = -1; i < list.size(); i++) {
				String name = i < 0 ? "" : list.get(i);
				String shown = i < 0 ? d.emptyLabel : name;
				boolean sel = d.value.equals(name);
				boolean ho = ui.hover(x, ly, lw, 28);
				if (ho || sel) {
					c.round(x, ly, lw, 28, 7, sel ? 0x1F22C55E : 0x14FFFFFF);
				}
				if (sel) {
					c.icon(Theme.I_CHECK, 12, x + 14, ly + 14, Theme.ONLINE);
				}
				c.text(Fonts.MEDIUM, 12, c.ellipsize(Fonts.MEDIUM, 12, shown, lw - 40), x + 28, ly + (28 - c.lineHeight(Fonts.MEDIUM, 12)) / 2,
						sel ? Theme.TEXT_STRONG : Theme.TEXT);
				if (ui.clicked(id + "#" + i, x, ly, lw, 28)) {
					d.value = name;
					openDevice = null;
					return -1; // changed
				}
				ly += 30;
			}
		}
		return bx;
	}

	private static void levelRow(Ui ui, float x, float y, float w, Setting.Level l, float labelW) {
		Canvas c = ui.c;
		l.shown.run();
		float bw = Math.max(100, Math.min(240, w - labelW - 40)), bh = 10, bx = x + w - bw, by = y + (ROW - bh) / 2;
		String err = l.error.get();
		if (err != null) {
			c.text(Fonts.MEDIUM, 11, c.ellipsize(Fonts.MEDIUM, 11, err, bw), x + w - Math.min(bw, c.textWidth(Fonts.MEDIUM, 11, err)),
					y + (ROW - c.lineHeight(Fonts.MEDIUM, 11)) / 2, Theme.DANGER);
			return;
		}
		float f = (float) Math.max(0, Math.min(1, (l.db.getAsDouble() - LEVEL_MIN) / -LEVEL_MIN));
		float shown = ui.anim("level#" + l.id, f, 22f);
		c.round(bx, by, bw, bh, bh / 2, 0x1AFFFFFF);
		if (shown > 0.01f) {
			c.round(bx, by, Math.max(bh, bw * shown), bh, bh / 2, l.hot.getAsBoolean() ? Theme.ONLINE : 0xFF71717A);
		}
		double mark = l.marker.getAsDouble();
		if (!Double.isNaN(mark)) {
			float mx = bx + bw * (float) Math.max(0, Math.min(1, (mark - LEVEL_MIN) / -LEVEL_MIN));
			c.fill(mx - 1, by - 5, 2, bh + 10, 0xFFF4F4F5);
		}
	}

	/**
	 * One labelled setting row (label left, control right). Marks the modules dirty when something changed.
	 * Returns true when the value changed.
	 */
	public static boolean row(Ui ui, String id, float x, float y, float w, Setting s) {
		Canvas c = ui.c;
		float h = ROW;
		float fs = 12.5f;
		float labelW = c.textWidth(Fonts.MEDIUM, fs, s.label);
		boolean changed = false;
		float controlX = x + w; // left edge of the control, so the reset button can sit just before it
		float labelMax = w;
		if (s instanceof Setting.Bool) {
			labelMax = w - 34 - 40;
		} else if (s instanceof Setting.Num) {
			labelMax = w - 70 - 46 - 36; // a slider never gets squeezed below 70 and never runs into the label
		} else if (s instanceof Setting.Choice) {
			labelMax = w * 0.4f;
		} else if (s instanceof Setting.Color) {
			labelMax = w - swatchesWidth() - 40;
		} else if (s instanceof Setting.Key) {
			labelMax = w - 110 - 40;
		} else if (s instanceof Setting.Device || s instanceof Setting.Level) {
			labelMax = w * 0.38f;
		}
		String label = labelW > labelMax ? c.ellipsize(Fonts.MEDIUM, fs, s.label, Math.max(40, labelMax)) : s.label;
		labelW = c.textWidth(Fonts.MEDIUM, fs, label);
		c.text(Fonts.MEDIUM, fs, label, x, y + (h - c.lineHeight(Fonts.MEDIUM, fs)) / 2, Theme.TEXT);
		if (s instanceof Setting.Device) {
			float left = deviceRow(ui, id, x, y, w, (Setting.Device) s, labelW);
			changed = left < 0;
			controlX = left < 0 ? x + w : left;
		} else if (s instanceof Setting.Level) {
			levelRow(ui, x, y, w, (Setting.Level) s, labelW);
		} else if (s instanceof Setting.Bool) {
			controlX = x + w - 34;
			Setting.Bool b = (Setting.Bool) s;
			if (ui.toggle(id, x + w - 34, y + (h - 20) / 2, b.value)) {
				b.value = !b.value;
				changed = true;
			}
		} else if (s instanceof Setting.Num) {
			Setting.Num n = (Setting.Num) s;
			String v = n.text();
			float vw = 46;
			float sw = Math.max(70, Math.min(170, w - labelW - vw - 36));
			c.text(Fonts.SEMIBOLD, 11.5f, v, x + w - c.textWidth(Fonts.SEMIBOLD, 11.5f, v), y + (h - c.lineHeight(Fonts.SEMIBOLD, 11.5f)) / 2,
					Theme.TEXT_SECONDARY);
			changed = slider(ui, id, x + w - vw - sw, y + (h - 18) / 2, sw, n);
			controlX = x + w - vw - sw - 6;
		} else if (s instanceof Setting.Choice) {
			Setting.Choice ch = (Setting.Choice) s;
			float avail = Math.max(w * 0.6f, w - labelW - 16);
			String[] labels = ch.options;
			float sw = 0;
			for (String o : labels) {
				sw += c.textWidth(Fonts.SEMIBOLD, 11.5f, o) + 24;
			}
			if (sw > avail && ch.shorts != null && ch.shorts.length == ch.options.length) {
				labels = ch.shorts; // the full labels would be cut off: use the short ones
				sw = 0;
				for (String o : labels) {
					sw += c.textWidth(Fonts.SEMIBOLD, 11.5f, o) + 24;
				}
			}
			sw = Math.min(Math.max(sw, 90), avail);
			int v = segmented(ui, id, x + w - sw, y + (h - 26) / 2, sw, 26, labels, ch.value);
			controlX = x + w - sw;
			if (v != ch.value) {
				ch.value = v;
				changed = true;
			}
		} else if (s instanceof Setting.Color) {
			changed = swatches(ui, id, x + w - swatchesWidth(), y + (h - 18) / 2, (Setting.Color) s);
			controlX = x + w - swatchesWidth();
		} else if (s instanceof Setting.Key) {
			Setting.Key k = (Setting.Key) s;
			int v = keybind(ui, id, x + w - 110, y + (h - 28) / 2, 110, 28, k.value);
			controlX = x + w - 110;
			if (v != k.value) {
				k.value = v;
				changed = true;
			}
		}
		// changed from what it ships with: a small reset button brings it back (UI size, keys, module options...)
		if (!s.isDefault() && !ui.isPressing(id)) {
			float rs = 22;
			if (ui.iconButton(id + "#reset", controlX - rs - 6, y + (h - rs) / 2, rs, Theme.I_RESET, "Reset to default")) {
				s.reset();
				changed = true;
			}
		}
		if (changed) {
			Modules.changed();
		}
		return changed;
	}
}
