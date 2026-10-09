package xyz.nativelaunch.ui.gfx;

/** A rasterised glyph in the atlas, in physical pixels. */
public final class Glyph {
	final int ax, ay, w, h;
	final int xoff, yoff;
	final float advance;

	Glyph(int ax, int ay, int w, int h, int xoff, int yoff, float advance) {
		this.ax = ax;
		this.ay = ay;
		this.w = w;
		this.h = h;
		this.xoff = xoff;
		this.yoff = yoff;
		this.advance = advance;
	}
}
