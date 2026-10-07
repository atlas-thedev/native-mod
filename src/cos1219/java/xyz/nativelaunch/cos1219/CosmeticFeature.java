package xyz.nativelaunch.cos1219;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import xyz.nativelaunch.cosmetic.CosmeticLibrary;
import xyz.nativelaunch.cosmetic.CosmeticModel;
import xyz.nativelaunch.cosmetic.CosmeticPose;
import xyz.nativelaunch.cosmetic.CosmeticRenderer;
import xyz.nativelaunch.cosmetic.CosmeticSink;

import java.io.ByteArrayInputStream;
import java.util.List;

/** Minecraft 1.21.9 - 1.21.11 renderer for Native 3D cosmetics. */
public final class CosmeticFeature extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel>
		implements CosmeticSink {
	private static final int FULL_BRIGHT = 0xF000F0;

	private static final class Baked {
		ModelPart[] parts;
		RenderLayer cutout;
		RenderLayer translucent;
		RenderLayer glow;
	}

	private final CosmeticRenderer walker = new CosmeticRenderer();
	/** Set after the first unexpected error: cosmetics switch off instead of crashing the game. */
	private static volatile boolean broken;
	private final ModelPart scratch = new ModelPart(java.util.Collections.<ModelPart.Cuboid>emptyList(), java.util.Collections.<String, ModelPart>emptyMap());
	private MatrixStack matrices;
	private OrderedRenderCommandQueue buffers;
	private int light;

	public CosmeticFeature(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, OrderedRenderCommandQueue buffers, int light, PlayerEntityRenderState state, float yaw, float pitch) {
		if (broken) {
			return;
		}
		if (state.invisible || state.spectator) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		Entity entity = client.world == null ? null : client.world.getEntityById(state.id);
		if (!(entity instanceof AbstractClientPlayerEntity)) {
			return;
		}
		String name = entity.getName().getString();
		List<CosmeticLibrary.Loaded> worn = CosmeticLibrary.worn(name, entity.getUuid());
		if (worn.isEmpty()) {
			return;
		}
		int armor = 0;
		if (filled(state.equippedHeadStack)) armor |= CosmeticRenderer.ARMOR_HEAD;
		if (filled(state.equippedChestStack)) armor |= CosmeticRenderer.ARMOR_CHEST;
		if (filled(state.equippedLegsStack)) armor |= CosmeticRenderer.ARMOR_LEGS;
		if (filled(state.equippedFeetStack)) armor |= CosmeticRenderer.ARMOR_FEET;
		boolean leftMain = ((AbstractClientPlayerEntity) entity).getMainArm() == net.minecraft.util.Arm.LEFT;
		boolean mainBusy = !((AbstractClientPlayerEntity) entity).getMainHandStack().isEmpty();
		boolean offBusy = !((AbstractClientPlayerEntity) entity).getOffHandStack().isEmpty();
		if (leftMain ? mainBusy : offBusy) armor |= CosmeticRenderer.ARMOR_LEFT_HAND;
		if (leftMain ? offBusy : mainBusy) armor |= CosmeticRenderer.ARMOR_RIGHT_HAND;
		this.matrices = matrices;
		this.buffers = buffers;
		this.light = light;
		try {
			walker.render(worn, this, CosmeticPose.now() + CosmeticPose.seed(name), Math.min(1f, state.limbSwingAmplitude), armor);
		} catch (Throwable t) {
			broken = true;
			xyz.nativelaunch.core.Log.warn("3D cosmetics stopped after an error ({}).", t.toString());
		} finally {
			this.matrices = null;
			this.buffers = null;
		}
	}

	private static boolean filled(net.minecraft.item.ItemStack stack) {
		return stack != null && !stack.isEmpty();
	}

	@Override
	public void push() {
		matrices.push();
	}

	@Override
	public void pop() {
		matrices.pop();
	}

	@Override
	public void attach(CosmeticModel.Attach attach) {
		PlayerEntityModel model = getContextModel();
		model.getRootPart().applyTransform(matrices);
		ModelPart part;
		switch (attach) {
			case BODY: part = model.body; break;
			case RIGHT_ARM: part = model.rightArm; break;
			case LEFT_ARM: part = model.leftArm; break;
			case RIGHT_LEG: part = model.rightLeg; break;
			case LEFT_LEG: part = model.leftLeg; break;
			default: part = model.head; break;
		}
		part.applyTransform(matrices);
	}

	@Override
	public void transform(float px, float py, float pz, float pitch, float yaw, float roll) {
		scratch.originX = px;
		scratch.originY = py;
		scratch.originZ = pz;
		scratch.pitch = pitch;
		scratch.yaw = yaw;
		scratch.roll = roll;
		scratch.applyTransform(matrices);
	}

	@Override
	public void draw(CosmeticLibrary.Loaded cosmetic, CosmeticModel.Part part) {
		Baked baked = bake(cosmetic);
		if (baked == null) {
			return;
		}
		ModelPart box = baked.parts[part.index];
		switch (part.layer) {
			case GLOW:
				buffers.submitModelPart(box, matrices, baked.glow, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, null);
				break;
			case TRANSLUCENT:
				buffers.submitModelPart(box, matrices, baked.translucent, light, OverlayTexture.DEFAULT_UV, null);
				break;
			default:
				buffers.submitModelPart(box, matrices, baked.cutout, light, OverlayTexture.DEFAULT_UV, null);
				break;
		}
	}

	private static Baked bake(CosmeticLibrary.Loaded cosmetic) {
		Object existing = cosmetic.baked();
		if (existing instanceof Baked) {
			return (Baked) existing;
		}
		if (existing != null) {
			return null; // failed before
		}
		try {
			Identifier id = Identifier.tryParse("native:cosmetic/" + cosmetic.ref.textureHash);
			NativeImage image = NativeImage.read(new ByteArrayInputStream(cosmetic.png()));
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture(image, id));
			CosmeticModel model = cosmetic.model;
			Baked baked = new Baked();
			baked.parts = new ModelPart[model.flat.size()];
			for (CosmeticModel.Part part : model.flat) {
				java.util.List<ModelPart.Cuboid> cuboids = new java.util.ArrayList<ModelPart.Cuboid>();
				for (CosmeticModel.Cube cube : part.cubes) {
					cuboids.add(cuboid(cube, model.textureWidth, model.textureHeight));
				}
				baked.parts[part.index] = new ModelPart(cuboids, java.util.Collections.<String, ModelPart>emptyMap());
			}
			baked.cutout = Layers.cutout(id);
			baked.translucent = Layers.translucent(id);
			baked.glow = Layers.eyes(id);
			cosmetic.setBaked(baked);
			cosmetic.release();
			return baked;
		} catch (Throwable t) {
			cosmetic.setBaked(Boolean.FALSE);
			xyz.nativelaunch.core.Log.warn("Cosmetic {} could not be prepared ({}).", cosmetic.ref.id, t.toString());
			return null;
		}
	}

	private static java.lang.reflect.Constructor<?> cuboidConstructor;

	/**
	 * ModelPart.Cuboid gained a visible-faces {@code Set<Direction>} parameter during this era, so the constructor
	 * is picked by arity: (u, v, x, y, z, sx, sy, sz, ex, ey, ez, mirror, texW, texH[, faces]).
	 */
	private static ModelPart.Cuboid cuboid(CosmeticModel.Cube c, int texW, int texH) throws ReflectiveOperationException {
		java.lang.reflect.Constructor<?> ctor = cuboidConstructor;
		if (ctor == null) {
			for (java.lang.reflect.Constructor<?> candidate : ModelPart.Cuboid.class.getConstructors()) {
				int n = candidate.getParameterCount();
				Class<?>[] p = candidate.getParameterTypes();
				if ((n == 14 || n == 15) && p[0] == int.class && p[1] == int.class && p[11] == boolean.class) {
					if (ctor == null || n == 15) {
						ctor = candidate;
					}
				}
			}
			if (ctor == null) {
				throw new NoSuchMethodException("ModelPart.Cuboid constructor");
			}
			cuboidConstructor = ctor;
		}
		float e = c.inflate;
		if (ctor.getParameterCount() == 15) {
			Object faces = java.util.EnumSet.allOf(net.minecraft.util.math.Direction.class);
			return (ModelPart.Cuboid) ctor.newInstance(c.u, c.v, c.x, c.y, c.z, (float) c.w, (float) c.h, (float) c.d, e, e, e, c.mirror,
					(float) texW, (float) texH, faces);
		}
		return (ModelPart.Cuboid) ctor.newInstance(c.u, c.v, c.x, c.y, c.z, (float) c.w, (float) c.h, (float) c.d, e, e, e, c.mirror,
				(float) texW, (float) texH);
	}

	/** NativeImageBackedTexture(NativeImage) until 1.21.4, (Supplier<String> label, NativeImage) from 1.21.5. */
	private static NativeImageBackedTexture texture(NativeImage image, final Identifier id) throws ReflectiveOperationException {
		for (java.lang.reflect.Constructor<?> ctor : NativeImageBackedTexture.class.getConstructors()) {
			Class<?>[] p = ctor.getParameterTypes();
			if (p.length == 1 && p[0] == NativeImage.class) {
				return (NativeImageBackedTexture) ctor.newInstance(image);
			}
			if (p.length == 2 && p[0] == java.util.function.Supplier.class && p[1] == NativeImage.class) {
				java.util.function.Supplier<String> label = () -> id.toString();
				return (NativeImageBackedTexture) ctor.newInstance(label, image);
			}
		}
		throw new NoSuchMethodException("NativeImageBackedTexture(NativeImage)");
	}
}
