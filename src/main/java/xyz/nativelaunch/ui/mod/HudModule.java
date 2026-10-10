package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;

import java.util.ArrayList;
import java.util.List;

/**
 * A module drawn on the in-game HUD. Its place is kept as a fraction of the free screen space (0..1 on each axis),
 * so it stays glued to the same edge when the window is resized. Text modules only implement {@link #lines};
 * graphical ones (keystrokes, compass) override {@link #measure} and {@link #paint}.
 */
public abstract class HudModule extends Module {
	public static final float MARGIN = 6;
	public static final int STYLE_CARD = 0, STYLE_TEXT = 1, STYLE_BRACKETS = 2;

	public float fx, fy;
	final float defX, defY;
	/** Shared look, shown under "Appearance". */
	public final List<Setting> appearance = new ArrayList<Setting>();
	public final Setting.Num scale;
	public final Setting.Choice style;
	public final Setting.Color color;
	public final Setting.Num opacity;
	public final Setting.Bool shadow;
	/** Off shows the bare number ("144" instead of "144 FPS"). */
	public final Setting.Bool labels;
	private final List<Setting> every = new ArrayList<Setting>();

	protected HudModule(String id, String name, String description, int icon, boolean enabled, float x, float y) {
		this(id, name, description, HUD, icon, enabled, x, y);
	}

	protected HudModule(String id, String name, String description, String category, int icon, boolean enabled, float x, float y) {
		super(id, name, description, category, icon, enabled);
		fx = defX = x;
		fy = defY = y;
		scale = look(new Setting.Num("scale", "Size", 1f, 0.5f, 2.5f, 0.05f, "x"));
		style = look(new Setting.Choice("style", "Style", STYLE_CARD, "Card", "Text", "Brackets"));
		color = look(new Setting.Color("color", "Text colour", 0xFFFFFFFF));
		opacity = look(new Setting.Num("opacity", "Background", 55, 0, 100, 5, "%"));
		shadow = look(new Setting.Bool("shadow", "Text shadow", true));
		labels = look(new Setting.Bool("labels", "Show labels", true));
	}

	private <T extends Setting> T look(T s) {
		appearance.add(s);
		return s;
	}

	@Override
	public boolean isHud() {
		return true;
	}

	@Override
	public List<Setting> allSettings() {
		every.clear();
		every.addAll(settings);
		every.addAll(appearance);
		return every;
	}

	/** Screen position (logical px) for an anchor fraction, a screen side and the module size. */
	public static float pos(float f, float screen, float size) {
		return MARGIN + f * Math.max(0, screen - size - 2 * MARGIN);
	}

	/** Inverse of {@link #pos}. */
	public static float frac(float p, float screen, float size) {
		float free = screen - size - 2 * MARGIN;
		return free <= 0 ? 0 : Math.max(0, Math.min(1, (p - MARGIN) / free));
	}

	public void resetPosition() {
		fx = defX;
		fy = defY;
	}

	/** Shown on the HUD right now (a module may hide itself, e.g. server IP in singleplayer). */
	public boolean visible(Game g) {
		return true;
	}

	// ── text modules ─────────────────────────────────────────────────────

	/** Fills label / value pairs, one per line. Return the number of lines. */
	protected int lines(Game g, String[] labels, String[] values) {
		return 0;
	}

	/** True when the value is written before the label ("144 FPS"). */
	protected boolean valueFirst() {
		return false;
	}

	private final String[] lineLabels = new String[6], lineValues = new String[6];
	private final float[] size = new float[2];

	private static final float FONT = 12f;

	private String join(int i) {
		String l = lineLabels[i], v = lineValues[i];
		if (l == null || l.isEmpty()) {
			return v;
		}
		if (v == null || v.isEmpty()) {
			return l;
		}
		if (!labels.value) {
			return v;
		}
		return valueFirst() ? v + " " + l : l + (style.value == STYLE_BRACKETS ? ": " : " ") + v;
	}

	/** Size in logical px at the module's scale. */
	public float[] measure(Canvas c, Game g) {
		float s = scale.value;
		int n = lines(g, lineLabels, lineValues);
		float w = 0;
		for (int i = 0; i < n; i++) {
			String t = join(i);
			if (style.value == STYLE_BRACKETS) {
				t = "[" + t + "]";
			}
			w = Math.max(w, c.textWidth(Fonts.SEMIBOLD, FONT * s, t));
		}
		float lh = c.lineHeight(Fonts.SEMIBOLD, FONT * s);
		boolean card = style.value == STYLE_CARD;
		size[0] = w + (card ? 16 * s : 2 * s);
		size[1] = n * lh + Math.max(0, n - 1) * 2 * s + (card ? 10 * s : 2 * s);
		return size;
	}

	public void paint(Ui ui, float x, float y, Game g) {
		Canvas c = ui.c;
		float s = scale.value;
		float[] sz = measure(c, g);
		int n = lines(g, lineLabels, lineValues);
		boolean card = style.value == STYLE_CARD;
		if (card) {
			background(c, x, y, sz[0], sz[1], s);
		}
		float lh = c.lineHeight(Fonts.SEMIBOLD, FONT * s);
		float ty = y + (card ? 5 * s : s);
		int col = color.value;
		int muted = Theme.alpha(col, 0.62f);
		for (int i = 0; i < n; i++) {
			float tx = x + (card ? 8 * s : s);
			if (style.value == STYLE_BRACKETS) {
				text(c, s, "[" + join(i) + "]", tx, ty, col);
			} else if (!labels.value || lineLabels[i] == null || lineLabels[i].isEmpty()
					|| lineValues[i] == null || lineValues[i].isEmpty()) {
				text(c, s, join(i), tx, ty, col);
			} else if (valueFirst()) {
				tx = text(c, s, lineValues[i] + " ", tx, ty, col);
				text(c, s, lineLabels[i], tx, ty, muted);
			} else {
				tx = text(c, s, lineLabels[i] + " ", tx, ty, muted);
				text(c, s, lineValues[i], tx, ty, col);
			}
			ty += lh + 2 * s;
		}
	}

	/** Glass card behind a module (Card style). */
	protected void background(Canvas c, float x, float y, float w, float h, float s) {
		float a = opacity.value / 100f;
		if (a <= 0.01f) {
			return;
		}
		c.round(x, y, w, h, 6 * s, Theme.alpha(0xFF08090C, a));
		c.outline(x, y, w, h, 6 * s, 1, Theme.alpha(Theme.HAIRLINE, Math.min(1f, a * 1.6f)));
	}

	/** Text with the optional drop shadow; returns the x after it. */
	protected float text(Canvas c, float s, String t, float x, float y, int argb) {
		return text(c, Fonts.SEMIBOLD, FONT * s, t, x, y, argb);
	}

	protected float text(Canvas c, int face, float size, String t, float x, float y, int argb) {
		if (shadow.value) {
			float o = Math.max(1f, size / 12f) / c.scale * Math.max(1f, c.scale);
			c.text(face, size, t, x + o, y + o, Theme.alpha(0xFF000000, 0.55f * ((argb >>> 24) / 255f)));
		}
		c.text(face, size, t, x, y, argb);
		return x + c.textWidth(face, size, t);
	}
}
