package xyz.nativelaunch.ui26;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import xyz.nativelaunch.health.HealthScene;

/** Fills the health scene on Minecraft 26.x (official names). */
final class HealthReader26 {
	private static final long LOS_MS = 150;
	private final IdentityHashMap<EntityType<?>, String> typeIds = new IdentityHashMap<EntityType<?>, String>();
	/** entity id -> {last check (ms), result (0/1), last seen frame} */
	private final Map<Integer, long[]> sight = new HashMap<Integer, long[]>();
	private long frame;

	private String typeId(EntityType<?> type) {
		String id = typeIds.get(type);
		if (id == null) {
			id = EntityType.getKey(type).toString();
			typeIds.put(type, id);
		}
		return id;
	}

	boolean read(HealthScene out) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		LocalPlayer me = mc.player;
		if (level == null || me == null) {
			return false;
		}
		Camera cam = mc.gameRenderer.mainCamera();
		if (cam == null) {
			return false;
		}
		Vec3 p = cam.position();
		out.camX = p.x;
		out.camY = p.y;
		out.camZ = p.z;
		out.camYaw = cam.yRot();
		out.camPitch = cam.xRot();
		float fov = cam.getFov();
		out.fov = fov > 1f ? fov : mc.options.fov().get().floatValue();
		out.hardcore = level.getLevelData().isHardcore();
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		long nowMs = System.currentTimeMillis();
		frame++;
		double maxSq = HealthScene.MAX_DISTANCE * HealthScene.MAX_DISTANCE;
		for (Entity ent : level.entitiesForRendering()) {
			if (!(ent instanceof LivingEntity)) {
				continue;
			}
			LivingEntity e = (LivingEntity) ent;
			Vec3 pos = e.getPosition(pt);
			if (pos.distanceToSqr(p) > maxSq) {
				continue;
			}
			HealthScene.Entry r = out.add();
			if (r == null) {
				break;
			}
			boolean self = e == me;
			r.id = e.getId();
			r.x = pos.x;
			r.y = pos.y;
			r.z = pos.z;
			r.width = e.getBbWidth();
			r.height = e.getBbHeight();
			r.health = e.getHealth();
			r.max = e.getMaxHealth();
			r.absorb = e.getAbsorptionAmount();
			r.armor = e.getArmorValue();
			r.self = self;
			r.player = e instanceof Player;
			r.hostile = e instanceof Enemy;
			r.passive = e instanceof Mob && !r.hostile;
			r.type = typeId(e.getType());
			r.name = r.player ? e.getScoreboardName() : "";
			r.valid = e.isAlive() && !e.isSpectator() && me.getVehicle() != e && !e.isInvisibleTo(me);
			r.label = (r.player && !self) || (e.hasCustomName() && e.isCustomNameVisible());
			if (!r.valid) {
				continue;
			}
			r.regen = e.hasEffect(MobEffects.REGENERATION);
			r.kind = e.hasEffect(MobEffects.WITHER) ? 2 : e.hasEffect(MobEffects.POISON) ? 1 : e.isFullyFrozen() ? 3 : 0;
			r.visible = self || canSee(me, e, nowMs);
		}
		// forget entities that were not in this frame's list
		if ((frame & 255L) == 0L) {
			for (Iterator<long[]> it = sight.values().iterator(); it.hasNext();) {
				if (frame - it.next()[2] > 256L) {
					it.remove();
				}
			}
		}
		out.ok = true;
		return true;
	}

	private boolean canSee(LocalPlayer me, LivingEntity e, long nowMs) {
		long[] s = sight.get(e.getId());
		if (s == null) {
			s = new long[3];
			s[0] = -1;
			sight.put(e.getId(), s);
		}
		s[2] = frame;
		if (nowMs - s[0] >= LOS_MS) {
			s[0] = nowMs;
			s[1] = me.hasLineOfSight(e) ? 1 : 0;
		}
		return s[1] == 1;
	}

	String target() {
		Entity t = Minecraft.getInstance().crosshairPickEntity;
		return t == null ? null : typeId(t.getType());
	}

	void say(String text, int rgb, boolean chat) {
		LocalPlayer me = Minecraft.getInstance().player;
		if (me == null) {
			return;
		}
		MutableComponent c = Component.literal(text);
		if (rgb >= 0) {
			c = c.withColor(rgb);
		}
		if (chat) {
			me.sendSystemMessage(c);
		} else {
			me.sendOverlayMessage(c);
		}
	}
}
