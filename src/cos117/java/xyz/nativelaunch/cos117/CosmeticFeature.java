package xyz.nativelaunch.cos117;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.Identifier;
import xyz.nativelaunch.cosmetic.CosmeticLibrary;
import xyz.nativelaunch.cosmetic.CosmeticModel;
import xyz.nativelaunch.cosmetic.CosmeticPose;
import xyz.nativelaunch.cosmetic.CosmeticRenderer;
import xyz.nativelaunch.cosmetic.CosmeticSink;

import java.io.ByteArrayInputStream;
import java.util.List;

/** Minecraft 1.17 - 1.21.1 renderer for Native 3D cosmetics. */
public final class CosmeticFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>>
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
	private VertexConsumerProvider buffers;
	private int light;

	public CosmeticFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, AbstractClientPlayerEntity player,
			float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (broken) {
			return;
		}
		if (player.isInvisible() || player.isSpectator()) {
			return;
		}
		String name = player.getName().getString();
		List<CosmeticLibrary.Loaded> worn = CosmeticLibrary.worn(name, player.getUuid());
		if (worn.isEmpty()) {
			return;
		}
		int armor = 0;
		if (!player.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) armor |= CosmeticRenderer.ARMOR_HEAD;
		if (!player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()) armor |= CosmeticRenderer.ARMOR_CHEST;
		if (!player.getEquippedStack(EquipmentSlot.LEGS).isEmpty()) armor |= CosmeticRenderer.ARMOR_LEGS;
		if (!player.getEquippedStack(EquipmentSlot.FEET).isEmpty()) armor |= CosmeticRenderer.ARMOR_FEET;
		boolean leftMain = player.getMainArm() == net.minecraft.util.Arm.LEFT;
		boolean mainBusy = !player.getMainHandStack().isEmpty();
		boolean offBusy = !player.getOffHandStack().isEmpty();
		if (leftMain ? mainBusy : offBusy) armor |= CosmeticRenderer.ARMOR_LEFT_HAND;
		if (leftMain ? offBusy : mainBusy) armor |= CosmeticRenderer.ARMOR_RIGHT_HAND;
		this.matrices = matrices;
		this.buffers = buffers;
		this.light = light;
		try {
			walker.render(worn, this, CosmeticPose.now() + CosmeticPose.seed(name), Math.min(1f, limbDistance), armor);
		} catch (Throwable t) {
			broken = true;
			xyz.nativelaunch.core.Log.warn("3D cosmetics stopped after an error ({}).", t.toString());
		} finally {
			this.matrices = null;
			this.buffers = null;
		}
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
		PlayerEntityModel<AbstractClientPlayerEntity> model = getContextModel();
		ModelPart part;
		switch (attach) {
			case BODY: part = model.body; break;
			case RIGHT_ARM: part = model.rightArm; break;
			case LEFT_ARM: part = model.leftArm; break;
			case RIGHT_LEG: part = model.rightLeg; break;
			case LEFT_LEG: part = model.leftLeg; break;
			default: part = model.head; break;
		}
		part.rotate(matrices);
	}

	@Override
	public void transform(float px, float py, float pz, float pitch, float yaw, float roll) {
		scratch.pivotX = px;
		scratch.pivotY = py;
		scratch.pivotZ = pz;
		scratch.pitch = pitch;
		scratch.yaw = yaw;
		scratch.roll = roll;
		scratch.rotate(matrices);
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
				box.render(matrices, buffers.getBuffer(baked.glow), FULL_BRIGHT, OverlayTexture.DEFAULT_UV);
				break;
			case TRANSLUCENT:
				box.render(matrices, buffers.getBuffer(baked.translucent), light, OverlayTexture.DEFAULT_UV);
				break;
			default:
				box.render(matrices, buffers.getBuffer(baked.cutout), light, OverlayTexture.DEFAULT_UV);
				break;
		}
	}

	private static Baked bake(CosmeticLibrary.Loaded cosmetic) {
		Object existing = cosmetic.baked;
		if (existing instanceof Baked) {
			return (Baked) existing;
		}
		if (existing != null) {
			return null; // failed before
		}
		try {
			Identifier id = Identifier.tryParse("native:cosmetic/" + cosmetic.ref.textureHash);
			NativeImage image = NativeImage.read(new ByteArrayInputStream(cosmetic.png));
			MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
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
			baked.cutout = RenderLayer.getEntityCutoutNoCull(id);
			baked.translucent = RenderLayer.getEntityTranslucent(id);
			baked.glow = RenderLayer.getEyes(id);
			cosmetic.baked = baked;
			cosmetic.release();
			return baked;
		} catch (Throwable t) {
			cosmetic.baked = Boolean.FALSE;
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
}
