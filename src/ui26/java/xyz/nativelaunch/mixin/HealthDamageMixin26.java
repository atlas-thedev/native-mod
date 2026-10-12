package xyz.nativelaunch.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nativelaunch.health.HealthEvents;

/** Health indicators (26.x): who the player hurt (for "only after I hit it") and who hurt the player (for the damage arc). */
@Pseudo
@Mixin(targets = "net.minecraft.world.entity.LivingEntity", remap = false)
public abstract class HealthDamageMixin26 {
	@Inject(method = "handleDamageEvent(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("TAIL"), remap = false, require = 0)
	private void nativeHealth$damage(DamageSource source, CallbackInfo ci) {
		try {
			Entity me = Minecraft.getInstance().player;
			Entity cause = source.getEntity();
			LivingEntity victim = (LivingEntity) (Object) this;
			if (me == null || cause == null) {
				return;
			}
			if (cause == me && victim != me) {
				HealthEvents.attackedId(victim.getId());
			} else if (victim == me && cause != me && cause instanceof LivingEntity) {
				HealthEvents.hurtBy(cause.getX(), cause.getZ());
			}
		} catch (Throwable ignored) {
			// never let a health indicator break damage handling
		}
	}
}
