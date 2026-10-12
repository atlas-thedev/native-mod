package xyz.nativelaunch.health26;

import java.util.IdentityHashMap;
import java.util.function.Predicate;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import xyz.nativelaunch.core.Log;
import xyz.nativelaunch.health.DamageArc;
import xyz.nativelaunch.health.EntityRules;
import xyz.nativelaunch.health.HealthSettings;
import xyz.nativelaunch.health.HitMemory;
import xyz.nativelaunch.ui.mod.HealthModule;

/**
 * Everything the mixins call. Each entry point swallows its own failures (and switches the feature off after the
 * first one) so a mismatch with some future game build can never crash the game.
 */
public final class Hooks {
	private static final HitMemory HITS = new HitMemory();
	private static final EntityRules.Facts FACTS = new EntityRules.Facts();
	private static final IdentityHashMap<EntityType<?>, String> TYPE_IDS = new IdentityHashMap<>();
	private static final Predicate<Entity> PICKABLE = e -> !e.isSpectator() && e.isPickable();

	private static volatile boolean broken;
	private static int targetId = -1, wallId = -1;
	private static boolean hardcore;
	private static int epoch;
	private static Object lastLevel;
	private static Entity attacker;

	private Hooks() {
	}

	static String typeId(EntityType<?> type) {
		String id = TYPE_IDS.get(type);
		if (id == null) {
			id = EntityType.getKey(type).toString();
			TYPE_IDS.put(type, id);
		}
		return id;
	}

	private static void fail(String where, Throwable t) {
		if (!broken) {
			broken = true;
			Log.warn("Health indicators switched off: {} failed ({}).", where, t.toString());
		}
	}

	// ── render state extraction (once per entity per frame) ──

	public static void extract(LivingEntity e, LivingEntityRenderState st) {
		if (broken) {
			return;
		}
		try {
			doExtract(e, st);
		} catch (Throwable t) {
			fail("extract", t);
		}
	}

	private static void doExtract(LivingEntity e, LivingEntityRenderState st) {
		Snapshot sn = ((HealthCarrier) st).nativeHealth$snapshot();
		sn.show = false;
		HealthSettings s = HealthSettings.cur;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer me = mc.player;
		if (!s.enabled || me == null || mc.level == null) {
			return;
		}
		EntityRules.Facts f = FACTS;
		boolean isPlayer = e instanceof Player;
		f.valid = e.isAlive() && !e.isSpectator() && me.getVehicle() != e && !e.isInvisibleTo(me)
				&& e.showVehicleHealth() && !e.touchingUnloadedChunk();
		if (!f.valid) {
			return;
		}
		f.player = isPlayer;
		f.self = e == me;
		f.hostile = e instanceof Enemy;
		f.passive = e instanceof Mob && !f.hostile;
		f.health = e.getHealth();
		f.max = e.getMaxHealth();
		f.absorption = e.getAbsorptionAmount();
		f.distance = Math.sqrt(st.distanceToCameraSq);
		f.recentlyHit = HITS.active(e.getId(), mc.level.getGameTime());
		int id = e.getId();
		f.targeted = id == (s.throughWalls ? wallId : targetId);
		f.listed = HealthModule.list().allows(typeId(e.getType()), isPlayer ? e.getScoreboardName() : null);
		if (!EntityRules.show(f, s)) {
			return;
		}

		boolean armor = (s.armor || s.override) && e.getArmorValue() > 0;
		boolean hearts = s.hearts || s.override;
		if (!hearts && !armor) {
			return;
		}
		sn.hearts = hearts;
		sn.armor = armor;
		sn.max = Math.max(0f, f.max);
		sn.health = Math.max(0f, Math.min(f.health, sn.max));
		sn.absorb = Math.max(0f, f.absorption);
		sn.armorValue = e.getArmorValue();
		sn.number = hearts && s.useNumber(sn.max, false);
		sn.seeThrough = s.throughWalls && f.targeted && !isPlayer;
		sn.hardcore = hardcore && s.effects;
		sn.regen = s.effects && e.hasEffect(MobEffects.REGENERATION);
		sn.heartKind = 0;
		if (s.effects) {
			if (e.hasEffect(MobEffects.WITHER)) {
				sn.heartKind = 2;
			} else if (e.hasEffect(MobEffects.POISON)) {
				sn.heartKind = 1;
			} else if (e.isFullyFrozen()) {
				sn.heartKind = 3;
			}
		}
		sn.ageInTicks = st.ageInTicks;
		sn.distanceSq = st.distanceToCameraSq;

		// stay clear of the name tag (and the scoreboard line under it)
		float size = s.size;
		float lift = 0f;
		boolean label = (st.nameTag != null || (s.higherForPlayers && isPlayer && !f.self)) && st.distanceToCameraSq <= 4096.0;
		if (label) {
			lift += 9f * 1.15f * size;
			if (isPlayer && st.scoreText != null && st.distanceToCameraSq < 100.0) {
				lift += 9f * 1.15f * size;
			}
		}
		sn.baseHeight = st.boundingBoxHeight + 0.5f + lift;
		sn.show = true;
	}

	// ── drawing (once per entity per frame, after the entity itself) ──

	public static void submit(LivingEntityRenderState st, PoseStack ps, SubmitNodeCollector c, CameraRenderState cam) {
		if (broken) {
			return;
		}
		try {
			Snapshot sn = ((HealthCarrier) st).nativeHealth$snapshot();
			if (sn.show) {
				IconRenderer.draw(sn, HealthSettings.cur, ps, c, cam, Minecraft.getInstance());
			}
		} catch (Throwable t) {
			fail("submit", t);
		}
	}

	// ── game events ──

	/** End of every client tick: who is under the crosshair, forget stale hits, keep the arc following its attacker. */
	public static void clientTick(Minecraft mc) {
		if (broken) {
			return;
		}
		try {
			doTick(mc);
		} catch (Throwable t) {
			fail("tick", t);
		}
	}

	private static void doTick(Minecraft mc) {
		HealthSettings s = HealthSettings.cur;
		if (mc.level == null || mc.player == null) {
			targetId = wallId = -1;
			attacker = null;
			if (lastLevel != null) {
				lastLevel = null;
				HITS.clear();
				DamageArc.clear();
			}
			return;
		}
		if (lastLevel != mc.level) {
			lastLevel = mc.level;
			HITS.clear();
			DamageArc.clear();
			attacker = null;
		}
		hardcore = mc.level.getLevelData().isHardcore();
		long now = mc.level.getGameTime();
		if (epoch != s.hitMemoryEpoch) {
			epoch = s.hitMemoryEpoch;
			HITS.clear();
		}
		if ((now & 63L) == 0L) {
			HITS.prune(now);
		}
		if (attacker != null) {
			if (attacker.isRemoved() || !s.arc || !DamageArc.active(System.nanoTime(), s.arcSeconds)) {
				attacker = null;
			} else {
				DamageArc.follow(attacker.getX(), attacker.getZ());
			}
		}
		if (!s.enabled || !(s.lookingAt || s.throughWalls)) {
			targetId = wallId = -1;
			return;
		}
		Entity cam = mc.getCameraEntity();
		if (cam == null) {
			targetId = wallId = -1;
			return;
		}
		double range = s.lookingAt ? s.reach : 64.0;
		Vec3 eye = cam.getEyePosition();
		Vec3 look = cam.getViewVector(1.0f);
		// like vanilla: a block in the way shortens the ray
		double blocked = range;
		HitResult hit = cam.pick(range, 1.0f, false);
		if (hit.getType() != HitResult.Type.MISS) {
			blocked = Math.min(range, hit.getLocation().distanceTo(eye));
		}
		targetId = rayEntity(cam, eye, look, blocked);
		wallId = s.throughWalls ? rayEntity(cam, eye, look, range) : -1;
	}

	private static int rayEntity(Entity cam, Vec3 eye, Vec3 look, double dist) {
		Vec3 end = eye.add(look.x * dist, look.y * dist, look.z * dist);
		AABB box = cam.getBoundingBox().expandTowards(look.scale(dist)).inflate(1.0);
		EntityHitResult r = ProjectileUtil.getEntityHitResult(cam, eye, end, box, PICKABLE, dist * dist);
		return r == null ? -1 : r.getEntity().getId();
	}

	/** A damage event reached this client (the entity was hurt, by {@code source}). */
	public static void onDamage(LivingEntity victim, DamageSource source) {
		if (broken) {
			return;
		}
		try {
			HealthSettings s = HealthSettings.cur;
			Minecraft mc = Minecraft.getInstance();
			LocalPlayer me = mc.player;
			if (!s.enabled || me == null || mc.level == null) {
				return;
			}
			Entity cause = source.getEntity();
			if (cause == null) {
				return;
			}
			if (cause == me && victim != me) {
				HITS.hit(victim.getId(), mc.level.getGameTime(), Math.max(1, s.timeAfterHit) * 20);
			} else if (victim == me && cause != me && cause instanceof LivingEntity && s.arc) {
				attacker = cause;
				DamageArc.mark(cause.getX(), cause.getZ(), System.nanoTime());
			}
		} catch (Throwable t) {
			fail("damage", t);
		}
	}
}
