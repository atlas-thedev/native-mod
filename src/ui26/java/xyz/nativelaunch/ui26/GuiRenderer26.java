package xyz.nativelaunch.ui26;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.joml.Matrix3x2f;
import org.lwjgl.system.MemoryUtil;
import xyz.nativelaunch.core.Log;
import xyz.nativelaunch.ui.UiRuntime;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Image;
import xyz.nativelaunch.ui.gfx.Renderer;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Minecraft 26.x: draws the Native canvas through the game's own GUI pipeline (works on the OpenGL and the Vulkan
 * backend alike). Each texture batch becomes one GUI element on its own layer so the painter's order is kept.
 * Types whose package moved between 26.1 and 26.3 (pipelines, texture views, samplers) are only touched reflectively.
 */
public final class GuiRenderer26 implements Renderer {
	private static final Matrix3x2f IDENTITY = new Matrix3x2f();

	private boolean failed, ready;
	private NativeImage atlasPixels;
	private DynamicTexture atlasTex;
	private Object atlasSetup;
	private final Map<Image, Object> imageSetups = new IdentityHashMap<Image, Object>();
	private final Map<Image, DynamicTexture> imageTextures = new IdentityHashMap<Image, DynamicTexture>();

	private Object pipeline, nearest, linear;
	private Method textureView, singleTexture;
	private Field stateField;

	@Override
	public void render(Canvas c) {
		if (failed || c.quads == 0 || !(UiRuntime.guiContext instanceof GuiGraphicsExtractor)) {
			return;
		}
		GuiGraphicsExtractor g = (GuiGraphicsExtractor) UiRuntime.guiContext;
		try {
			if (!ready) {
				init(c);
			}
			draw(g, c);
		} catch (Throwable t) {
			failed = true;
			Log.warn("Native UI renderer (26.x) disabled ({}).", t.toString());
		}
	}

	private void init(Canvas c) throws Exception {
		Field f = Class.forName("net.minecraft.client.renderer.RenderPipelines").getField("GUI_TEXTURED");
		pipeline = f.get(null);
		textureView = AbstractTexture.class.getMethod("getTextureView");
		Object cache = RenderSystem.getSamplerCache();
		Method clamp = null;
		for (Method m : cache.getClass().getMethods()) {
			if (m.getName().equals("getClampToEdge") && m.getParameterCount() == 1) {
				clamp = m;
			}
		}
		if (clamp == null) {
			throw new IllegalStateException("no SamplerCache.getClampToEdge(FilterMode)");
		}
		@SuppressWarnings({"unchecked", "rawtypes"})
		Class<? extends Enum> mode = (Class<? extends Enum>) clamp.getParameterTypes()[0];
		nearest = clamp.invoke(cache, Enum.valueOf(mode, "NEAREST"));
		linear = clamp.invoke(cache, Enum.valueOf(mode, "LINEAR"));
		for (Method m : Class.forName("net.minecraft.client.gui.render.TextureSetup").getMethods()) {
			if (m.getName().equals("singleTexture") && m.getParameterCount() == 2) {
				singleTexture = m;
			}
		}
		for (Field x : GuiGraphicsExtractor.class.getDeclaredFields()) {
			if (x.getType() == GuiRenderState.class) {
				x.setAccessible(true);
				stateField = x;
			}
		}
		if (singleTexture == null || stateField == null) {
			throw new IllegalStateException("GUI pipeline not found");
		}
		int size = c.atlas.size;
		atlasPixels = new NativeImage(size, size, false);
		atlasTex = new DynamicTexture(() -> "native-ui-atlas", atlasPixels);
		atlasSetup = setup(atlasTex, false);
		ready = true;
		Log.info("Native UI renderer ready (Minecraft GUI pipeline, {}).", RenderSystem.getBackendDescription());
	}

	private Object setup(AbstractTexture tex, boolean smooth) throws Exception {
		return singleTexture.invoke(null, textureView.invoke(tex), smooth ? linear : nearest);
	}

	private void uploadAtlas(Canvas c) {
		int[] dirty = c.atlas.takeDirty();
		if (dirty == null) {
			return;
		}
		int size = c.atlas.size;
		ByteBuffer dst = MemoryUtil.memByteBuffer(atlasPixels.getPointer(), size * size * 4);
		for (int row = 0; row < dirty[3]; row++) {
			int at = ((dirty[1] + row) * size + dirty[0]) * 4;
			dst.position(at);
			dst.put(c.atlas.pixels, at, dirty[2] * 4);
		}
		atlasTex.upload();
	}

	private Object imageSetup(Image img) throws Exception {
		Object s = imageSetups.get(img);
		if (s != null) {
			return s;
		}
		NativeImage px = new NativeImage(img.width, img.height, false);
		for (int y = 0; y < img.height; y++) {
			for (int x = 0; x < img.width; x++) {
				px.setPixel(x, y, img.argb[y * img.width + x]);
			}
		}
		DynamicTexture tex = new DynamicTexture(() -> "native-ui-image", px);
		s = setup(tex, img.linear);
		imageTextures.put(img, tex);
		imageSetups.put(img, s);
		return s;
	}

	private void draw(GuiGraphicsExtractor g, Canvas c) throws Exception {
		uploadAtlas(c);
		float k = 1f / Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
		GuiRenderState state = (GuiRenderState) stateField.get(g);
		ScreenRectangle bounds = new ScreenRectangle(0, 0, g.guiWidth(), g.guiHeight());
		g.nextStratum(); // above everything the game drew this frame
		for (int b = 0; b < c.batches; b++) {
			int first = c.batchStart[b], end = c.batchEnd(b);
			if (end <= first) {
				continue;
			}
			Image tex = c.batchTex[b];
			Object textures = tex == null ? atlasSetup : imageSetup(tex);
			float[] v = new float[(end - first) * 16];
			int[] col = new int[(end - first) * 4];
			int p = 0, q4 = 0;
			for (int q = first; q < end; q++) {
				int g8 = q * 8, k4 = q * 4;
				int fq = c.free[q];
				if (fq >= 0) {
					float[] f = c.freeGeo;
					// counter-clockwise (0, 3, 2, 1): 26.3 culls back faces, vanilla emits CCW quads
					for (int j = 0; j < 4; j++) {
						int i = j == 0 ? 0 : 4 - j;
						v[p++] = f[fq + i * 2] * k;
						v[p++] = f[fq + i * 2 + 1] * k;
						v[p++] = f[fq + 8 + i * 2];
						v[p++] = f[fq + 9 + i * 2];
						col[q4++] = c.col[k4];
					}
					continue;
				}
				float x0 = c.geo[g8] * k, y0 = c.geo[g8 + 1] * k, x1 = c.geo[g8 + 2] * k, y1 = c.geo[g8 + 3] * k;
				float u0 = c.geo[g8 + 4], v0 = c.geo[g8 + 5], u1 = c.geo[g8 + 6], v1 = c.geo[g8 + 7];
				// x0y0 -> x0y1 -> x1y1 -> x1y0, the same winding vanilla's ColoredRectangleRenderState uses
				v[p++] = x0; v[p++] = y0; v[p++] = u0; v[p++] = v0; col[q4++] = c.col[k4];
				v[p++] = x0; v[p++] = y1; v[p++] = u0; v[p++] = v1; col[q4++] = c.col[k4 + 3];
				v[p++] = x1; v[p++] = y1; v[p++] = u1; v[p++] = v1; col[q4++] = c.col[k4 + 2];
				v[p++] = x1; v[p++] = y0; v[p++] = u1; v[p++] = v0; col[q4++] = c.col[k4 + 1];
			}
			if (b > 0) {
				state.up();
			}
			state.addGuiElement(element(v, col, q4, textures, bounds));
		}
	}

	/** A GuiElementRenderState (its pipeline() return type differs per 26.x release, so it is a dynamic proxy). */
	private GuiElementRenderState element(final float[] v, final int[] col, final int count, final Object textures, final ScreenRectangle bounds) {
		final Object pipe = pipeline;
		InvocationHandler h = (proxy, m, args) -> {
			switch (m.getName()) {
				case "buildVertices": {
					VertexConsumer out = (VertexConsumer) args[0];
					for (int i = 0, p = 0; i < count; i++, p += 4) {
						out.addVertexWith2DPose(IDENTITY, v[p], v[p + 1]).setUv(v[p + 2], v[p + 3]).setColor(col[i]);
					}
					return null;
				}
				case "pipeline":
					return pipe;
				case "textureSetup":
					return textures;
				case "scissorArea":
					return null;
				case "bounds":
					return bounds;
				case "hashCode":
					return System.identityHashCode(proxy);
				case "equals":
					return proxy == args[0];
				case "toString":
					return "NativeUiElement";
				default:
					if (m.isDefault()) {
						return InvocationHandler.invokeDefault(proxy, m, args);
					}
					return null;
			}
		};
		return (GuiElementRenderState) Proxy.newProxyInstance(GuiElementRenderState.class.getClassLoader(), new Class<?>[] {GuiElementRenderState.class}, h);
	}
}
