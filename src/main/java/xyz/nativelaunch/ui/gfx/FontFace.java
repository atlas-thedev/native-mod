package xyz.nativelaunch.ui.gfx;

import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/** A TrueType face loaded with stb_truetype (shipped by every Minecraft since 1.13). */
public final class FontFace {
	final String name;
	private final ByteBuffer data; // must stay reachable: stb keeps pointers into it
	final STBTTFontinfo info;
	final int ascent, descent, lineGap;

	private FontFace(String name, ByteBuffer data, int offset) {
		this.name = name;
		this.data = data;
		this.info = STBTTFontinfo.malloc();
		if (!STBTruetype.stbtt_InitFont(info, data, offset)) {
			info.free();
			throw new IllegalArgumentException("Not a TrueType font: " + name);
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer a = stack.mallocInt(1), d = stack.mallocInt(1), g = stack.mallocInt(1);
			STBTruetype.stbtt_GetFontVMetrics(info, a, d, g);
			ascent = a.get(0);
			descent = d.get(0);
			lineGap = g.get(0);
		}
	}

	static FontFace fromResource(String path) {
		try (InputStream in = FontFace.class.getResourceAsStream(path)) {
			if (in == null) {
				return null;
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[65536];
			int n;
			while ((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
			}
			return fromBytes(path, out.toByteArray(), 0);
		} catch (Throwable t) {
			return null;
		}
	}

	static FontFace fromFile(Path file) {
		try {
			if (!Files.isRegularFile(file) || Files.size(file) > 40L * 1024 * 1024) {
				return null;
			}
			byte[] bytes = Files.readAllBytes(file);
			int offset = 0;
			if (bytes.length > 4 && bytes[0] == 't' && bytes[1] == 't' && bytes[2] == 'c' && bytes[3] == 'f') {
				ByteBuffer probe = ByteBuffer.allocateDirect(bytes.length);
				probe.put(bytes).flip();
				offset = STBTruetype.stbtt_GetFontOffsetForIndex(probe, 0);
				if (offset < 0) {
					return null;
				}
			}
			return fromBytes(file.toString(), bytes, offset);
		} catch (Throwable t) {
			return null;
		}
	}

	private static FontFace fromBytes(String name, byte[] bytes, int offset) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
		buffer.put(bytes).flip();
		return new FontFace(name, buffer, offset);
	}

	int glyphIndex(int codepoint) {
		return STBTruetype.stbtt_FindGlyphIndex(info, codepoint);
	}

	/** Scale that maps the em square to px pixels (CSS font-size semantics). */
	float scaleForEm(float px) {
		return STBTruetype.stbtt_ScaleForMappingEmToPixels(info, px);
	}

	ByteBuffer keepAlive() {
		return data;
	}
}
