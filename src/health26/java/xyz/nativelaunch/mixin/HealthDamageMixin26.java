package xyz.nativelaunch.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nativelaunch.health26.Hooks;

/** Health indicators (26.x): who hit what (for "only after I hit it") and who hit me (for the damage arc). */
@Pseudo
@Mixin(targets = "net.minecraft.world.entity.LivingEntity", remap = false)
public abstract class HealthDamageMixin26 {
	@Inject(method = "handleDamageEvent(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("TAIL"), remap = false, require = 0)
	private void nativeHealth$damage(DamageSource source, CallbackInfo ci) {
		Hooks.onDamage((LivingEntity) (Object) this, source);
	}
}
