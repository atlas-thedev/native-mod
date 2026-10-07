package xyz.nativelaunch.cos26;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import xyz.nativelaunch.cosmetic.CosmeticLibrary;
import xyz.nativelaunch.cosmetic.CosmeticModel;
import xyz.nativelaunch.cosmetic.CosmeticPose;
import xyz.nativelaunch.cosmetic.CosmeticRenderer;
import xyz.nativelaunch.cosmetic.CosmeticSink;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/** Minecraft 26.x renderer for Native 3D cosmetics (written against the game's own unobfuscated classes). */
public final class CosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> implements CosmeticSink {
	private static final int FULL_BRIGHT = 0xF000F0;

	private static final class Baked {
		ModelPart[] parts;
		RenderType cutout;
		RenderType translucent;
		RenderType glow;
	}

	private final CosmeticRenderer walker = new CosmeticRenderer();
	/** Set after the first unexpected error: cosmetics switch off instead of crashing the game. */
	private static volatile boolean broken;
	private final ModelPart scratch = new ModelPart(Collections.<ModelPart.Cube>emptyList(), Collections.<String, ModelPart>emptyMap());
	private PoseStack poses;
	private SubmitNodeCollector collector;
	private int light;

	@SuppressWarnings({"unchecked", "rawtypes"})
	public CosmeticLayer(RenderLayerParent parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poses, SubmitNodeCollector collector, int light, AvatarRenderState state, float yaw, float pitch) {
		if (broken) {
			return;
		}
		if (state.isInvisible || state.isSpectator) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Entity entity = client.level == null ? null : client.level.getEntity(state.id);
		if (!(entity instanceof AbstractClientPlayer)) {
			return;
		}
		String name = entity.getName().getString();
		List<CosmeticLibrary.Loaded> worn = CosmeticLibrary.worn(name, entity.getUUID());
		if (worn.isEmpty()) {
			return;
		}
		int armor = 0;
		if (filled(state.headEquipment)) armor |= CosmeticRenderer.ARMOR_HEAD;
		if (filled(state.chestEquipment)) armor |= CosmeticRenderer.ARMOR_CHEST;
		if (filled(state.legsEquipment)) armor |= CosmeticRenderer.ARMOR_LEGS;
		if (filled(state.feetEquipment)) armor |= CosmeticRenderer.ARMOR_FEET;
		boolean leftMain = ((AbstractClientPlayer) entity).getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT;
		boolean mainBusy = !((AbstractClientPlayer) entity).getMainHandItem().isEmpty();
		boolean offBusy = !((AbstractClientPlayer) entity).getOffhandItem().isEmpty();
		if (leftMain ? mainBusy : offBusy) armor |= CosmeticRenderer.ARMOR_LEFT_HAND;
		if (leftMain ? offBusy : mainBusy) armor |= CosmeticRenderer.ARMOR_RIGHT_HAND;
		this.poses = poses;
		this.collector = collector;
		this.light = light;
		try {
			walker.render(worn, this, CosmeticPose.now() + CosmeticPose.seed(name), Math.min(1f, state.walkAnimationSpeed), armor);
		} catch (Throwable t) {
			broken = true;
			xyz.nativelaunch.core.Log.warn("3D cosmetics stopped after an error ({}).", t.toString());
		} finally {
			this.poses = null;
			this.collector = null;
		}
	}

	private static boolean filled(ItemStack stack) {
		return stack != null && !stack.isEmpty();
	}

	@Override
	public void push() {
		poses.pushPose();
	}

	@Override
	public void pop() {
		poses.popPose();
	}

	@Override
	public void attach(CosmeticModel.Attach attach) {
		PlayerModel model = getParentModel();
		model.root().translateAndRotate(poses);
		ModelPart part;
		switch (attach) {
			case BODY: part = model.body; break;
			case RIGHT_ARM: part = model.rightArm; break;
			case LEFT_ARM: part = model.leftArm; break;
			case RIGHT_LEG: part = model.rightLeg; break;
			case LEFT_LEG: part = model.leftLeg; break;
			default: part = model.head; break;
		}
		part.translateAndRotate(poses);
	}

	@Override
	public void transform(float px, float py, float pz, float pitch, float yaw, float roll) {
		scratch.x = px;
		scratch.y = py;
		scratch.z = pz;
		scratch.xRot = pitch;
		scratch.yRot = yaw;
		scratch.zRot = roll;
		scratch.translateAndRotate(poses);
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
				submit(box, baked.glow, FULL_BRIGHT);
				break;
			case TRANSLUCENT:
				submit(box, baked.translucent, light);
				break;
			default:
				submit(box, baked.cutout, light);
				break;
		}
	}

	private static volatile java.lang.reflect.Method submitModelPart;

	/**
	 * SubmitNodeCollector.submitModelPart(part, poses, type, light, overlay, sprite-or-uv-mapping): the last
	 * parameter is a TextureAtlasSprite on 26.1 - 26.2 and a UvMapping from 26.3 (always null here), so the
	 * six-argument overload is found by shape.
	 */
	private void submit(ModelPart box, RenderType type, int packedLight) {
		try {
			java.lang.reflect.Method m = submitModelPart;
			if (m == null) {
				for (java.lang.reflect.Method candidate : net.minecraft.client.renderer.OrderedSubmitNodeCollector.class.getMethods()) {
					Class<?>[] p = candidate.getParameterTypes();
					if (candidate.getName().equals("submitModelPart") && p.length == 6 && p[0] == ModelPart.class && p[1] == PoseStack.class
							&& p[2] == RenderType.class && p[3] == int.class && p[4] == int.class && !p[5].isPrimitive()) {
						m = candidate;
						break;
					}
				}
				if (m == null) {
					throw new NoSuchMethodException("submitModelPart");
				}
				submitModelPart = m;
			}
			m.invoke(collector, box, poses, type, packedLight, OverlayTexture.NO_OVERLAY, null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Baked bake(CosmeticLibrary.Loaded cosmetic) {
		Object existing = cosmetic.baked;
		if (existing instanceof Baked) {
			return (Baked) existing;
		}
		if (existing != null) {
			return null;
		}
		try {
			final Identifier id = Identifier.tryParse("native:cosmetic/" + cosmetic.ref.textureHash);
			NativeImage image = NativeImage.read(new ByteArrayInputStream(cosmetic.png));
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
			CosmeticModel model = cosmetic.model;
			Baked baked = new Baked();
			baked.parts = new ModelPart[model.flat.size()];
			for (CosmeticModel.Part part : model.flat) {
				List<ModelPart.Cube> cubes = new ArrayList<ModelPart.Cube>();
				for (CosmeticModel.Cube c : part.cubes) {
					float e = c.inflate;
					cubes.add(new ModelPart.Cube(c.u, c.v, c.x, c.y, c.z, c.w, c.h, c.d, e, e, e, c.mirror,
							model.textureWidth, model.textureHeight, EnumSet.allOf(Direction.class)));
				}
				baked.parts[part.index] = new ModelPart(cubes, Collections.<String, ModelPart>emptyMap());
			}
			// 26.x renamed the layers: entityCutout is the no-cull one (the culled one is entityCutoutCull)
			baked.cutout = RenderTypes.entityCutout(id);
			baked.translucent = RenderTypes.entityTranslucent(id);
			baked.glow = RenderTypes.eyes(id);
			cosmetic.baked = baked;
			cosmetic.release();
			return baked;
		} catch (Throwable t) {
			cosmetic.baked = Boolean.FALSE;
			xyz.nativelaunch.core.Log.warn("Cosmetic {} could not be prepared ({}).", cosmetic.ref.id, t.toString());
			return null;
		}
	}
}
