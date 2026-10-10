package xyz.nativelaunch.ui.gfx;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;
import xyz.nativelaunch.core.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws a {@link Canvas} straight into the default framebuffer with one tiny shader, one vertex buffer and one
 * draw call per texture batch, right before Minecraft swaps buffers. Every piece of GL state it touches is read
 * first and restored exactly afterwards, so Minecraft's own state cache never notices. Works on the GL 2.1
 * compatibility context of 1.16 and the 3.2 core context of 1.17+.
 */
public final class GlRenderer implements Renderer {
	private static final int FLOATS_PER_VERTEX = 5; // x, y, u, v, packed colour
	private boolean ready, failed, detected, core, vaoCapable, samplers, fbo;
	private int program, vao, vbo, atlasTex, uScreen, uTex;
	private ByteBuffer vertices = MemoryUtil.memAlloc(6 * 4 * FLOATS_PER_VERTEX * 4096);
	private final List<Image> uploaded = new ArrayList<Image>();

	public boolean failed() {
		return failed;
	}

	private void detect() {
		GLCapabilities caps = GL.getCapabilities();
		vaoCapable = caps.OpenGL30;
		fbo = caps.OpenGL30;
		samplers = caps.OpenGL33;
		core = false;
		if (caps.OpenGL32) {
			int mask = GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK);
			core = (mask & GL32.GL_CONTEXT_CORE_PROFILE_BIT) != 0;
		}
		detected = true;
	}

	private void init(Atlas atlas) {
		String vs, fs;
		if (core) {
			vs = "#version 150\nin vec2 aPos; in vec2 aUv; in vec4 aCol; uniform vec2 uScreen; out vec2 vUv; out vec4 vCol;\n"
					+ "void main(){ vUv=aUv; vCol=aCol; gl_Position=vec4(aPos.x/uScreen.x*2.0-1.0, 1.0-aPos.y/uScreen.y*2.0, 0.0, 1.0); }";
			fs = "#version 150\nin vec2 vUv; in vec4 vCol; uniform sampler2D uTex; out vec4 oColor;\n"
					+ "void main(){ oColor = texture(uTex, vUv) * vCol; }";
		} else {
			vs = "#version 120\nattribute vec2 aPos; attribute vec2 aUv; attribute vec4 aCol; uniform vec2 uScreen; varying vec2 vUv; varying vec4 vCol;\n"
					+ "void main(){ vUv=aUv; vCol=aCol; gl_Position=vec4(aPos.x/uScreen.x*2.0-1.0, 1.0-aPos.y/uScreen.y*2.0, 0.0, 1.0); }";
			fs = "#version 120\nvarying vec2 vUv; varying vec4 vCol; uniform sampler2D uTex;\n"
					+ "void main(){ gl_FragColor = texture2D(uTex, vUv) * vCol; }";
		}
		int v = compile(GL20.GL_VERTEX_SHADER, vs), f = compile(GL20.GL_FRAGMENT_SHADER, fs);
		program = GL20.glCreateProgram();
		GL20.glAttachShader(program, v);
		GL20.glAttachShader(program, f);
		GL20.glBindAttribLocation(program, 0, "aPos");
		GL20.glBindAttribLocation(program, 1, "aUv");
		GL20.glBindAttribLocation(program, 2, "aCol");
		if (core) {
			GL30.glBindFragDataLocation(program, 0, "oColor");
		}
		GL20.glLinkProgram(program);
		GL20.glDeleteShader(v);
		GL20.glDeleteShader(f);
		if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
			throw new IllegalStateException("UI shader link failed: " + GL20.glGetProgramInfoLog(program));
		}
		uScreen = GL20.glGetUniformLocation(program, "uScreen");
		uTex = GL20.glGetUniformLocation(program, "uTex");
		vbo = GL15.glGenBuffers();
		if (vaoCapable) {
			vao = GL30.glGenVertexArrays();
			GL30.glBindVertexArray(vao);
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
			pointers();
		}
		atlasTex = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
		params(false);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, atlas.size, atlas.size, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
		ready = true;
		Log.info("Native UI renderer ready (OpenGL {}, {} profile).", GL11.glGetString(GL11.GL_VERSION), core ? "core" : "compatibility");
	}

	private static void pointers() {
		int stride = FLOATS_PER_VERTEX * 4;
		GL20.glEnableVertexAttribArray(0);
		GL20.glEnableVertexAttribArray(1);
		GL20.glEnableVertexAttribArray(2);
		GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0);
		GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 8);
		GL20.glVertexAttribPointer(2, 4, GL11.GL_UNSIGNED_BYTE, true, stride, 16);
	}

	private static void params(boolean linear) {
		int filter = linear ? GL11.GL_LINEAR : GL11.GL_NEAREST;
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
	}

	private static int compile(int type, String src) {
		int s = GL20.glCreateShader(type);
		GL20.glShaderSource(s, src);
		GL20.glCompileShader(s);
		if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0) {
			throw new IllegalStateException("UI shader compile failed: " + GL20.glGetShaderInfoLog(s));
		}
		return s;
	}

	/** Renders the canvas over whatever Minecraft drew this frame. */
	@Override
	public void render(Canvas c) {
		if (failed || c.quads == 0) {
			return;
		}
		Saved saved = new Saved();
		try {
			if (!detected) {
				detect();
			}
			saved.capture(vaoCapable, samplers, fbo, !core);
			if (!ready) {
				init(c.atlas);
			}
			draw(c);
			sweep();
		} catch (Throwable t) {
			failed = true;
			Log.warn("Native UI renderer disabled ({}).", t.toString());
		} finally {
			try {
				saved.restore(vaoCapable, samplers, fbo);
			} catch (Throwable ignored) {
				// nothing more we can do
			}
		}
	}

	private void draw(Canvas c) {
		if (fbo) {
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		}
		GL11.glViewport(0, 0, c.fbW, c.fbH);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_SCISSOR_TEST);
		GL11.glDisable(GL11.GL_STENCIL_TEST);
		if (!core) {
			GL11.glDisable(GL11.GL_ALPHA_TEST);
		}
		GL11.glColorMask(true, true, true, true);
		GL11.glDepthMask(false);
		GL11.glEnable(GL11.GL_BLEND);
		GL14.glBlendEquation(GL14.GL_FUNC_ADD);
		GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL20.glUseProgram(program);
		GL20.glUniform2f(uScreen, c.fbW, c.fbH);
		GL20.glUniform1i(uTex, 0);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		if (samplers) {
			GL33.glBindSampler(0, 0);
		}
		// pixel transfer defaults for our uploads
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
		GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);
		GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);

		GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
		int[] dirty = c.atlas.takeDirty();
		if (dirty != null) {
			ByteBuffer up = MemoryUtil.memAlloc(dirty[2] * dirty[3] * 4);
			try {
				for (int row = 0; row < dirty[3]; row++) {
					up.put(c.atlas.pixels, ((dirty[1] + row) * c.atlas.size + dirty[0]) * 4, dirty[2] * 4);
				}
				up.flip();
				GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
				GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
				GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
				GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, dirty[0], dirty[1], dirty[2], dirty[3], GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, up);
			} finally {
				MemoryUtil.memFree(up);
			}
		}

		// vertices: 6 per quad
		int needed = c.quads * 6 * FLOATS_PER_VERTEX * 4;
		if (vertices.capacity() < needed) {
			MemoryUtil.memFree(vertices);
			vertices = MemoryUtil.memAlloc(Math.max(needed, vertices.capacity() * 2));
		}
		vertices.clear();
		java.nio.FloatBuffer fv = vertices.asFloatBuffer();
		java.nio.IntBuffer iv = vertices.asIntBuffer();
		int p = 0;
		for (int q = 0; q < c.quads; q++) {
			int g = q * 8, k = q * 4;
			int fq = c.free[q];
			if (fq >= 0) {
				float[] f = c.freeGeo;
				int cc = abgr(c.col[k]);
				p = vertex(fv, iv, p, f[fq], f[fq + 1], f[fq + 8], f[fq + 9], cc);
				p = vertex(fv, iv, p, f[fq + 2], f[fq + 3], f[fq + 10], f[fq + 11], cc);
				p = vertex(fv, iv, p, f[fq + 4], f[fq + 5], f[fq + 12], f[fq + 13], cc);
				p = vertex(fv, iv, p, f[fq], f[fq + 1], f[fq + 8], f[fq + 9], cc);
				p = vertex(fv, iv, p, f[fq + 4], f[fq + 5], f[fq + 12], f[fq + 13], cc);
				p = vertex(fv, iv, p, f[fq + 6], f[fq + 7], f[fq + 14], f[fq + 15], cc);
				continue;
			}
			float x0 = c.geo[g], y0 = c.geo[g + 1], x1 = c.geo[g + 2], y1 = c.geo[g + 3];
			float u0 = c.geo[g + 4], v0 = c.geo[g + 5], u1 = c.geo[g + 6], v1 = c.geo[g + 7];
			int c0 = abgr(c.col[k]), c1 = abgr(c.col[k + 1]), c2 = abgr(c.col[k + 2]), c3 = abgr(c.col[k + 3]);
			p = vertex(fv, iv, p, x0, y0, u0, v0, c0);
			p = vertex(fv, iv, p, x1, y0, u1, v0, c1);
			p = vertex(fv, iv, p, x1, y1, u1, v1, c2);
			p = vertex(fv, iv, p, x0, y0, u0, v0, c0);
			p = vertex(fv, iv, p, x1, y1, u1, v1, c2);
			p = vertex(fv, iv, p, x0, y1, u0, v1, c3);
		}
		vertices.limit(p * 4);
		if (vaoCapable) {
			GL30.glBindVertexArray(vao);
		}
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STREAM_DRAW);
		if (!vaoCapable) {
			pointers();
		}
		for (int b = 0; b < c.batches; b++) {
			Image tex = c.batchTex[b];
			if (tex == null) {
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTex);
			} else {
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureFor(tex));
			}
			int first = c.batchStart[b], count = c.batchEnd(b) - first;
			GL11.glDrawArrays(GL11.GL_TRIANGLES, first * 6, count * 6);
		}
		if (!vaoCapable) {
			GL20.glDisableVertexAttribArray(0);
			GL20.glDisableVertexAttribArray(1);
			GL20.glDisableVertexAttribArray(2);
		}
	}

	private int textureFor(Image img) {
		img.lastUsed = System.currentTimeMillis();
		if (img.handle != 0) {
			return img.handle;
		}
		int id = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
		params(img.linear);
		ByteBuffer up = MemoryUtil.memAlloc(img.width * img.height * 4);
		try {
			for (int c : img.argb) {
				up.put((byte) (c >>> 16)).put((byte) (c >>> 8)).put((byte) c).put((byte) (c >>> 24));
			}
			up.flip();
			GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
			GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
			GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, img.width, img.height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, up);
		} finally {
			MemoryUtil.memFree(up);
		}
		img.handle = id;
		uploaded.add(img);
		return id;
	}

	private long lastSweep;

	/**
	 * Frees textures nothing has drawn for a while. Chat pictures and GIF frames come and go (the media cache
	 * forgets them), so without this the textures piled up for the whole session until the driver gave out.
	 */
	private void sweep() {
		long now = System.currentTimeMillis();
		if (now - lastSweep < 2000 && uploaded.size() <= TextureBudget.MAX) {
			return;
		}
		lastSweep = now;
		for (Image img : TextureBudget.stale(uploaded, now)) {
			release(img);
		}
	}

	/** Frees an image's texture (call on the render thread). */
	public void release(Image img) {
		if (img != null && img.handle != 0) {
			GL11.glDeleteTextures(img.handle);
			img.handle = 0;
			uploaded.remove(img);
		}
	}

	private static int vertex(java.nio.FloatBuffer fv, java.nio.IntBuffer iv, int p, float x, float y, float u, float v, int abgr) {
		fv.put(p, x);
		fv.put(p + 1, y);
		fv.put(p + 2, u);
		fv.put(p + 3, v);
		iv.put(p + 4, abgr);
		return p + FLOATS_PER_VERTEX;
	}

	/** ARGB -> bytes R,G,B,A in memory (little-endian int ABGR). */
	private static int abgr(int argb) {
		int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		if (java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.LITTLE_ENDIAN) {
			return (a << 24) | (b << 16) | (g << 8) | r;
		}
		return (r << 24) | (g << 16) | (b << 8) | a;
	}

	/** Snapshot of all GL state the renderer changes. */
	private static final class Saved {
		int program, vao, arrayBuffer, activeTexture, texture0, sampler0, drawFb, readFb;
		int blendSrcRgb, blendDstRgb, blendSrcA, blendDstA, blendEqRgb, blendEqA;
		boolean blend, depth, cull, scissor, stencil, alphaTest, depthMask;
		boolean[] colorMask = new boolean[4];
		int[] viewport = new int[4];
		int unpackAlign, unpackRow, unpackSkipRows, unpackSkipPixels, unpackSkipImages, unpackImageHeight;
		boolean compat;
		int attrib0, attrib1, attrib2;

		void capture(boolean vaoCapable, boolean samplers, boolean fbo, boolean compat) {
			this.compat = compat;
			program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
			if (vaoCapable) {
				vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
			}
			arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
			activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
			if (samplers) {
				sampler0 = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
			}
			if (fbo) {
				drawFb = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
				readFb = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
			}
			blend = GL11.glIsEnabled(GL11.GL_BLEND);
			blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
			blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
			blendSrcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
			blendDstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
			blendEqRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
			blendEqA = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
			depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
			cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
			scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
			stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
			if (compat) {
				alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
			}
			depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
			java.nio.ByteBuffer mask = MemoryUtil.memAlloc(16);
			try {
				GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
				for (int i = 0; i < 4; i++) {
					colorMask[i] = mask.get(i) != 0;
				}
			} finally {
				MemoryUtil.memFree(mask);
			}
			GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
			unpackAlign = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
			unpackRow = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
			unpackSkipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
			unpackSkipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
			unpackSkipImages = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_IMAGES);
			unpackImageHeight = GL11.glGetInteger(GL12.GL_UNPACK_IMAGE_HEIGHT);
			if (!vaoCapable) {
				attrib0 = GL20.glGetVertexAttribi(0, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED);
				attrib1 = GL20.glGetVertexAttribi(1, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED);
				attrib2 = GL20.glGetVertexAttribi(2, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED);
			}
		}

		void restore(boolean vaoCapable, boolean samplers, boolean fbo) {
			GL20.glUseProgram(program);
			if (vaoCapable) {
				GL30.glBindVertexArray(vao);
			} else {
				if (attrib0 != 0) GL20.glEnableVertexAttribArray(0);
				if (attrib1 != 0) GL20.glEnableVertexAttribArray(1);
				if (attrib2 != 0) GL20.glEnableVertexAttribArray(2);
			}
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture0);
			if (samplers) {
				GL33.glBindSampler(0, sampler0);
			}
			GL13.glActiveTexture(activeTexture);
			if (fbo) {
				GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFb);
				GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFb);
			}
			set(GL11.GL_BLEND, blend);
			GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcA, blendDstA);
			GL20.glBlendEquationSeparate(blendEqRgb, blendEqA);
			set(GL11.GL_DEPTH_TEST, depth);
			set(GL11.GL_CULL_FACE, cull);
			set(GL11.GL_SCISSOR_TEST, scissor);
			set(GL11.GL_STENCIL_TEST, stencil);
			if (compat) {
				set(GL11.GL_ALPHA_TEST, alphaTest);
			}
			GL11.glDepthMask(depthMask);
			GL11.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
			GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpackAlign);
			GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, unpackRow);
			GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, unpackSkipRows);
			GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, unpackSkipPixels);
			GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, unpackSkipImages);
			GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, unpackImageHeight);
		}

		private static void set(int cap, boolean on) {
			if (on) {
				GL11.glEnable(cap);
			} else {
				GL11.glDisable(cap);
			}
		}
	}
}
