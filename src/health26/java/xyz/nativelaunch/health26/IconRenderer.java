package xyz.nativelaunch.health26;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import xyz.nativelaunch.health.HealthLayout;
import xyz.nativelaunch.health.HealthSettings;

/**
 * Draws the hearts / armour / number as camera-facing quads using the vanilla HUD sprites. Each sprite kind is one
 * batched pass over all icons that use it, so an entity costs a handful of submissions however many hearts it has.
 */
final class IconRenderer {
	private static final int FULL_BRIGHT = 15728880;
	private static final int PASS_CONTAINER = -1;
	private static final String[] VARIANT = {"", "poisoned_", "withered_", "frozen_", "absorbing_"};
	private static final int V_ABSORBING = 4;

	private static final Identifier[] HEARTS = new Identifier[VARIANT.length * 4];
	private static final Identifier[] CONTAINER = new Identifier[2];
	private static final Identifier[] ARMOR = new Identifier[3];

	private IconRenderer() {
	}

	private static Identifier sprite(String path) {
		return Identifier.withDefaultNamespace("textures/gui/sprites/hud/" + path + ".png");
	}

	private static Identifier heart(int variant, boolean hardcore, boolean half) {
		int i = variant * 4 + (hardcore ? 2 : 0) + (half ? 1 : 0);
		Identifier id = HEARTS[i];
		if (id == null) {
			id = sprite("heart/" + VARIANT[variant] + (hardcore ? "hardcore_" : "") + (half ? "half" : "full"));
			HEARTS[i] = id;
		}
		return id;
	}

	private static Identifier container(boolean hardcore) {
		int i = hardcore ? 1 : 0;
		if (CONTAINER[i] == null) {
			CONTAINER[i] = sprite(hardcore ? "heart/container_hardcore" : "heart/container");
		}
		return CONTAINER[i];
	}

	private static Identifier armor(int kind) {
		if (ARMOR[kind] == null) {
			ARMOR[kind] = sprite(kind == 0 ? "armor_empty" : kind == 1 ? "armor_full" : "armor_half");
		}
		return ARMOR[kind];
	}

	static void draw(Snapshot sn, HealthSettings s, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam, Minecraft mc) {
		int alpha = Math.round(Math.max(0, Math.min(100, s.opacity)) * 2.55f);
		if (alpha <= 0) {
			return;
		}
		Quaternionf rot = cam.orientation;
		if (rot == null) {
			rot = mc.getEntityRenderDispatcher().camera.rotation();
		}
		float size = s.size;
		float rowH = 10f * size;
		float base = sn.baseHeight + s.offset * rowH;
		float depth = sn.seeThrough ? 0f : HealthLayout.depthOffsetPx(sn.distanceSq, size);
		int perRow = Math.max(1, s.iconsPerRow);

		int heartRows = 0;
		HealthLayout layout = null;
		if (sn.hearts) {
			layout = new HealthLayout().set(sn.health, sn.max, sn.absorb, s.percent, s.percentMax);
			if (layout.overflow) {
				sn.number = true;
			}
			if (sn.number) {
				drawNumber(sn, s, ps, c, rot, base, size, alpha, mc.font);
				heartRows = 1;
			} else {
				heartRows = HealthLayout.rows(layout.total, perRow);
				int hop = sn.regen ? HealthLayout.regenIndex((int) sn.ageInTicks, sn.max) : -1;
				heartPasses(ps, c, layout, sn, rot, base, size, perRow, alpha, depth, hop);
			}
		}
		if (sn.armor) {
			float armorBase = base + heartRows * rowH;
			armorPasses(ps, c, sn, rot, armorBase, size, perRow, alpha, depth);
		}
	}

	private static void heartPasses(PoseStack ps, SubmitNodeCollector c, HealthLayout l, Snapshot sn, Quaternionf rot, float base, float size,
			int perRow, int alpha, float depth, int hop) {
		boolean see = sn.seeThrough;
		// containers behind everything
		submit(ps, c, 0, container(sn.hardcore), see, Pass.hearts(l, PASS_CONTAINER, perRow, rot, base, size, alpha, -depth, hop));
		int kind = sn.heartKind;
		boolean hc = sn.hardcore;
		int redFull = l.red - (l.redHalf ? 1 : 0);
		if (redFull > 0) {
			submit(ps, c, 1, heart(kind, hc, false), see, Pass.hearts(l, HealthLayout.RED_FULL, perRow, rot, base, size, alpha, depth, hop));
		}
		if (l.redHalf) {
			submit(ps, c, 1, heart(kind, hc, true), see, Pass.hearts(l, HealthLayout.RED_HALF, perRow, rot, base, size, alpha, depth, hop));
		}
		int yellowFull = l.yellow - (l.yellowHalf ? 1 : 0);
		if (yellowFull > 0) {
			submit(ps, c, 1, heart(V_ABSORBING, hc, false), see, Pass.hearts(l, HealthLayout.YELLOW_FULL, perRow, rot, base, size, alpha, depth, hop));
		}
		if (l.yellowHalf) {
			submit(ps, c, 1, heart(V_ABSORBING, hc, true), see, Pass.hearts(l, HealthLayout.YELLOW_HALF, perRow, rot, base, size, alpha, depth, hop));
		}
	}

	private static void armorPasses(PoseStack ps, SubmitNodeCollector c, Snapshot sn, Quaternionf rot, float base, float size, int perRow, int alpha, float depth) {
		boolean see = sn.seeThrough;
		int slots = HealthLayout.armorSlots(sn.armorValue);
		submit(ps, c, 2, armor(0), see, Pass.armor(slots, sn.armorValue, PASS_CONTAINER, perRow, rot, base, size, alpha, -depth));
		if (hasType(slots, sn.armorValue, 1)) {
			submit(ps, c, 3, armor(1), see, Pass.armor(slots, sn.armorValue, 1, perRow, rot, base, size, alpha, depth));
		}
		if (hasType(slots, sn.armorValue, 2)) {
			submit(ps, c, 3, armor(2), see, Pass.armor(slots, sn.armorValue, 2, perRow, rot, base, size, alpha, depth));
		}
	}

	private static boolean hasType(int slots, int armorValue, int type) {
		for (int i = 0; i < slots; i++) {
			if (HealthLayout.armorType(i, armorValue) == type) {
				return true;
			}
		}
		return false;
	}

	private static void submit(PoseStack ps, SubmitNodeCollector c, int order, Identifier tex, boolean seeThrough, Pass pass) {
		RenderType type = seeThrough ? RenderTypes.textSeeThrough(tex) : RenderTypes.text(tex);
		c.order(order).submitCustomGeometry(ps, type, pass);
	}

	private static void drawNumber(Snapshot sn, HealthSettings s, PoseStack ps, SubmitNodeCollector c, Quaternionf rot, float base,
			float size, int alpha, Font font) {
		var text = sn.text(font, s.percent, s.decimals);
		int a = Math.max(5, alpha);
		int color = (a << 24) | (s.numberColor & 0xFFFFFF);
		ps.pushPose();
		ps.translate(0f, base, 0f);
		ps.last().pose().rotate(rot);
		ps.scale(size, -size, size);
		c.order(1).submitText(ps, -sn.textWidth() / 2f, 0f, text, s.numberShadow,
				sn.seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, FULL_BRIGHT, color, 0, 0);
		ps.popPose();
	}

	/** One sprite kind: draws every icon of that kind in one go. Plain fields, no references to live game objects. */
	private static final class Pass implements SubmitNodeCollector.CustomGeometryRenderer {
		private final boolean armor;
		private final int want, perRow, alpha, hop;
		// hearts
		private final int red, normal, total;
		private final boolean redHalf, yellowHalf;
		// armour
		private final int slots, armorValue;
		private final float qx, qy, qz, qw, base, size, z;

		/** Hearts. */
		static Pass hearts(HealthLayout l, int want, int perRow, Quaternionf q, float base, float size, int alpha, float z, int hop) {
			return new Pass(l, want, perRow, q, base, size, alpha, z, hop);
		}

		static Pass armor(int slots, int armorValue, int want, int perRow, Quaternionf q, float base, float size, int alpha, float z) {
			return new Pass(slots, armorValue, want, perRow, q, base, size, alpha, z);
		}

		private Pass(HealthLayout l, int want, int perRow, Quaternionf q, float base, float size, int alpha, float z, int hop) {
			this.armor = false;
			this.want = want;
			this.perRow = perRow;
			this.alpha = alpha;
			this.hop = hop;
			this.red = l.red;
			this.normal = l.normal;
			this.total = l.total;
			this.redHalf = l.redHalf;
			this.yellowHalf = l.yellowHalf;
			this.slots = 0;
			this.armorValue = 0;
			this.qx = q.x;
			this.qy = q.y;
			this.qz = q.z;
			this.qw = q.w;
			this.base = base;
			this.size = size;
			this.z = z;
		}

		/** Armour. */
		private Pass(int slots, int armorValue, int want, int perRow, Quaternionf q, float base, float size, int alpha, float z) {
			this.armor = true;
			this.want = want;
			this.perRow = perRow;
			this.alpha = alpha;
			this.hop = -1;
			this.red = 0;
			this.normal = 0;
			this.total = 0;
			this.redHalf = false;
			this.yellowHalf = false;
			this.slots = slots;
			this.armorValue = armorValue;
			this.qx = q.x;
			this.qy = q.y;
			this.qz = q.z;
			this.qw = q.w;
			this.base = base;
			this.size = size;
			this.z = z;
		}

		private int typeAt(int i) {
			if (i < red) {
				return i == red - 1 && redHalf ? HealthLayout.RED_HALF : HealthLayout.RED_FULL;
			}
			if (i < normal) {
				return HealthLayout.EMPTY;
			}
			return i == total - 1 && yellowHalf ? HealthLayout.YELLOW_HALF : HealthLayout.YELLOW_FULL;
		}

		@Override
		public void render(com.mojang.blaze3d.vertex.PoseStack.Pose pose, VertexConsumer vc) {
			Matrix4f m = new Matrix4f(pose.pose());
			m.translate(0f, base, 0f).rotate(new Quaternionf(qx, qy, qz, qw)).scale(-size, size, size);
			int n = armor ? slots : total;
			for (int i = 0; i < n; i++) {
				if (want != PASS_CONTAINER && (armor ? HealthLayout.armorType(i, armorValue) : typeAt(i)) != want) {
					continue;
				}
				float x = HealthLayout.iconX(i, n, perRow);
				float y = HealthLayout.rowOf(i, perRow) * 10f + (i == hop ? 2f : 0f);
				quad(vc, m, x, y);
			}
		}

		private void quad(VertexConsumer vc, Matrix4f m, float x, float y) {
			vertex(vc, m, x, y - 9f, 0f, 1f);
			vertex(vc, m, x - 9f, y - 9f, 1f, 1f);
			vertex(vc, m, x - 9f, y, 1f, 0f);
			vertex(vc, m, x, y, 0f, 0f);
		}

		private void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float u, float v) {
			vc.addVertex(m, x, y, z).setColor(255, 255, 255, alpha).setUv(u, v).setLight(FULL_BRIGHT);
		}
	}
}
