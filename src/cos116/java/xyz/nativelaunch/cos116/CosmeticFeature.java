package xyz.nativelaunch.cos116;

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

/** Minecraft 1.16.x renderer for Native 3D cosmetics. */
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
	private final ModelPart scratch = new ModelPart(64, 64, 0, 0);
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
				ModelPart box = new ModelPart(model.textureWidth, model.textureHeight, 0, 0);
				for (CosmeticModel.Cube cube : part.cubes) {
					box.setTextureOffset(cube.u, cube.v);
					box.addCuboid(cube.x, cube.y, cube.z, cube.w, cube.h, cube.d, cube.inflate, cube.mirror);
				}
				baked.parts[part.index] = box;
			}
			baked.cutout = RenderLayer.getEntityCutoutNoCull(id);
			baked.translucent = RenderLayer.getEntityTranslucent(id);
			baked.glow = RenderLayer.getEyes(id);
			cosmetic.baked = baked;
			return baked;
		} catch (Throwable t) {
			cosmetic.baked = Boolean.FALSE;
			xyz.nativelaunch.core.Log.warn("Cosmetic {} could not be prepared ({}).", cosmetic.ref.id, t.toString());
			return null;
		}
	}
}
