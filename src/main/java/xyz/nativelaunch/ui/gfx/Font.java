package xyz.nativelaunch.ui.gfx;

import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

/** One face at one pixel size: lazily rasterises glyphs into the atlas and measures text. */
public final class Font {
	private final Fonts owner;
	private final FontFace face;
	final int px;
	private final float scale;
	public final float ascent, descent, lineHeight;
	private final Map<Integer, Glyph> glyphs = new HashMap<Integer, Glyph>();
	private final Map<Integer, Float> advances = new HashMap<Integer, Float>();
	private int generation = -1;
	private static final Glyph EMPTY = new Glyph(0, 0, 0, 0, 0, 0, 0);
	private static ByteBuffer scratch;
	private static byte[] scratchBytes = new byte[0];

	Font(Fonts owner, FontFace face, int px) {
		this.owner = owner;
		this.face = face;
		this.px = px;
		this.scale = face.scaleForEm(px);
		this.ascent = face.ascent * scale;
		this.descent = -face.descent * scale;
		this.lineHeight = (face.ascent - face.descent + face.lineGap) * scale;
	}

	/** Width in physical pixels. */
	public float width(CharSequence text) {
		float w = 0;
		for (int i = 0; i < text.length(); ) {
			int cp = Character.codePointAt(text, i);
			i += Character.charCount(cp);
			w += advance(cp);
		}
		return w;
	}

	public float advance(int cp) {
		Float cached = advances.get(cp);
		if (cached != null) {
			return cached;
		}
		FontFace f = faceFor(cp);
		float adv;
		if (f == null) {
			adv = px * 0.5f;
		} else {
			float s = f == face ? scale : f.scaleForEm(px);
			try (MemoryStack stack = MemoryStack.stackPush()) {
				IntBuffer a = stack.mallocInt(1), l = stack.mallocInt(1);
				STBTruetype.stbtt_GetGlyphHMetrics(f.info, f.glyphIndex(cp), a, l);
				adv = a.get(0) * s;
			}
		}
		advances.put(cp, adv);
		return adv;
	}

	private FontFace faceFor(int cp) {
		if (face.glyphIndex(cp) != 0) {
			return face;
		}
		return owner.fallbackFor(cp);
	}

	Glyph glyph(Atlas atlas, int cp) {
		if (generation != atlas.generation) {
			glyphs.clear();
			generation = atlas.generation;
		}
		Glyph g = glyphs.get(cp);
		if (g != null) {
			return g;
		}
		g = rasterise(atlas, cp);
		if (g != null) {
			glyphs.put(cp, g);
		}
		return g == null ? EMPTY : g;
	}

	private Glyph rasterise(Atlas atlas, int cp) {
		FontFace f = faceFor(cp);
		float adv = advance(cp);
		if (f == null || cp == ' ' || cp == '\t') {
			return new Glyph(0, 0, 0, 0, 0, 0, adv);
		}
		float s = f == face ? scale : f.scaleForEm(px);
		int index = f.glyphIndex(cp);
		int x0, y0, w, h;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer ix0 = stack.mallocInt(1), iy0 = stack.mallocInt(1), ix1 = stack.mallocInt(1), iy1 = stack.mallocInt(1);
			STBTruetype.stbtt_GetGlyphBitmapBox(f.info, index, s, s, ix0, iy0, ix1, iy1);
			x0 = ix0.get(0);
			y0 = iy0.get(0);
			w = ix1.get(0) - x0;
			h = iy1.get(0) - y0;
		}
		if (w <= 0 || h <= 0) {
			return new Glyph(0, 0, 0, 0, 0, 0, adv);
		}
		int[] at = atlas.alloc(w, h);
		if (at == null) {
			return null; // atlas full: drawn next frame after the reset
		}
		int bytes = w * h;
		if (scratch == null || scratch.capacity() < bytes) {
			if (scratch != null) {
				MemoryUtil.memFree(scratch);
			}
			scratch = MemoryUtil.memAlloc(Math.max(bytes, 64 * 64));
			scratchBytes = new byte[scratch.capacity()];
		}
		scratch.clear();
		STBTruetype.stbtt_MakeGlyphBitmap(f.info, scratch, w, h, w, s, s, index);
		scratch.get(scratchBytes, 0, bytes);
		// a light gamma lift keeps small light-on-dark text from looking thin
		for (int i = 0; i < bytes; i++) {
			int a = scratchBytes[i] & 0xFF;
			scratchBytes[i] = (byte) GAMMA[a];
		}
		atlas.putAlpha(at[0], at[1], w, h, scratchBytes, w);
		return new Glyph(at[0], at[1], w, h, x0, y0, adv);
	}

	private static final int[] GAMMA = new int[256];
	static {
		for (int i = 0; i < 256; i++) {
			GAMMA[i] = Math.min(255, Math.round((float) Math.pow(i / 255.0, 0.8) * 255f));
		}
	}
}
