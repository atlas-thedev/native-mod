package io.github.adytech99.healthindicators;

import io.github.adytech99.healthindicators.config.Config;
import io.github.adytech99.healthindicators.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RenderTracker {
    private static final ConcurrentHashMap<UUID, Integer> UUIDS = new ConcurrentHashMap<>();

    public static boolean after_attack = false;

    public static void tick(MinecraftClient client){
        if(client == null || client.player == null || client.world == null) return;
        if(Config.getRenderingEnabled()) {
            for (Entity entity : client.world.getEntities()) {
                if (entity instanceof LivingEntity livingEntity && (satisfiesAdvancedCriteria(client.player, livingEntity) || overridePlayers(livingEntity))) {
                    addToUUIDS(livingEntity);
                } else removeFromUUIDS(entity.getUuid());
            }
        }
        trimEntities(client.world);
        if(ModConfig.HANDLER.instance().after_attack != after_attack) {
            if(ModConfig.HANDLER.instance().after_attack){
                UUIDS.clear();
            }
        }
        after_attack = ModConfig.HANDLER.instance().after_attack;
    }

    public static void onDamage(DamageSource damageSource, LivingEntity livingEntity) {
        if(damageSource != null && damageSource.getAttacker() instanceof PlayerEntity){
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.world == null) return;
            if (ModConfig.HANDLER.instance().after_attack
                    && livingEntity != null
                    && RenderTracker.isEntityTypeAllowed(livingEntity, client.player)) {

                if(!addToUUIDS(livingEntity)){
                    UUIDS.replace(livingEntity.getUuid(), (ModConfig.HANDLER.instance().time_after_hit * 20));
                }
            }
        }
    }


    public static void trimEntities(ClientWorld world) {
        if (world == null) return;
        Iterator<Map.Entry<UUID, Integer>> iterator = UUIDS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            entry.setValue(entry.getValue() - 1);
            if (entry.getValue() <= 0) {
                iterator.remove();
            }
        }

        UUIDS.entrySet().removeIf(entry -> isInvalid(getEntityFromUUID(entry.getKey(), world)) || !Config.getRenderingEnabled());
        if(UUIDS.size() >= 1536) UUIDS.clear();
    }


    public static void removeFromUUIDS(Entity entity){
        if (entity != null) UUIDS.remove(entity.getUuid());
    }
    public static void removeFromUUIDS(UUID uuid){
        if (uuid != null) UUIDS.remove(uuid);
    }

    public static boolean addToUUIDS(LivingEntity livingEntity){
        if (livingEntity == null) return false;
        if(!UUIDS.containsKey(livingEntity.getUuid())){
            UUIDS.put(livingEntity.getUuid(), ModConfig.HANDLER.instance().after_attack ? (ModConfig.HANDLER.instance().time_after_hit * 20) : 2400);
            return true;
        }
        else return false;
    }

    public static boolean isInUUIDS(LivingEntity livingEntity){
        return livingEntity != null && UUIDS.containsKey(livingEntity.getUuid());
    }

    public static boolean overridePlayers(LivingEntity livingEntity){
        MinecraftClient client = MinecraftClient.getInstance();
        return (ModConfig.HANDLER.instance().override_players && livingEntity instanceof PlayerEntity && (client == null || livingEntity != client.player))
                || (client != null && livingEntity == client.player && ModConfig.HANDLER.instance().self);
    }

    public static boolean isEntityTypeAllowed(LivingEntity livingEntity, PlayerEntity self){
        if(!ModConfig.HANDLER.instance().passive_mobs && livingEntity instanceof PassiveEntity) return false;
        if(!ModConfig.HANDLER.instance().hostile_mobs && livingEntity instanceof HostileEntity) return false;
        if(!ModConfig.HANDLER.instance().players && livingEntity instanceof PlayerEntity) return false;
        if(!ModConfig.HANDLER.instance().self && livingEntity == self) return false;
        return !(livingEntity instanceof ArmorStandEntity);
    }

    public static boolean satisfiesAdvancedCriteria(ClientPlayerEntity player, LivingEntity livingEntity){
        if(!isEntityTypeAllowed(livingEntity, player)) return false;
        if(!UUIDS.containsKey(livingEntity.getUuid()) && ModConfig.HANDLER.instance().after_attack) return false;
        if(livingEntity.getHealth() == livingEntity.getMaxHealth() && ModConfig.HANDLER.instance().damaged_only && livingEntity.getAbsorptionAmount() <= 0) return false;
        if(!isTargeted(livingEntity) && ModConfig.HANDLER.instance().looking_at) return false;

        return !isInvalid(livingEntity);
    }

    public static boolean isTargeted(LivingEntity livingEntity){
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return false;
        Entity camera = client.cameraEntity;
        if (camera == null) return false;
        double d = ModConfig.HANDLER.instance().reach;
        double e = MathHelper.square(d);
        Vec3d vec3d = camera.getCameraPosVec(0);
        HitResult hitResult = camera.raycast(d, 0, false);
        double f = hitResult.getPos().squaredDistanceTo(vec3d);
        if (hitResult.getType() != HitResult.Type.MISS) {
            e = f;
            d = Math.sqrt(e);
        }
        Vec3d vec3d2 = camera.getRotationVec(0);
        Vec3d vec3d3 = vec3d.add(vec3d2.x * d, vec3d2.y * d, vec3d2.z * d);
        Box box = camera.getBoundingBox().stretch(vec3d2.multiply(d)).expand(1.0, 1.0, 1.0);
        EntityHitResult entityHitResult = ProjectileUtil.raycast(camera, vec3d, vec3d3, box, entity -> !entity.isSpectator() && entity.canHit(), e);

        if (entityHitResult != null && entityHitResult.getEntity() instanceof LivingEntity livingEntity1){
            return livingEntity1 == livingEntity;
        }
        return false;
    }


    public static boolean isInvalid(Entity entity){
        MinecraftClient client = MinecraftClient.getInstance();
        return (entity == null
                || !entity.isAlive()
                || !entity.isLiving()
                || entity.isRegionUnloaded()
                || !(entity instanceof LivingEntity)
                || client == null
                || client.player == null
                || client.player.getVehicle() == entity
                || entity.isInvisibleTo(client.player));
    }
    private static Entity getEntityFromUUID(UUID uuid, ClientWorld world) {
        if (uuid == null || world == null) return null;
        for (Entity entity : world.getEntities()) {
            if (entity.getUuid().equals(uuid)) {
                return entity;
            }
        }
        return null;
    }
}
