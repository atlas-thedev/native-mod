package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nativelaunch.health.HealthEvents;

/**
 * Health indicators, Minecraft 1.16 - 1.21.11 (intermediary names): remembers what the player attacks, for "only after I
 * hit it". ClientPlayerInteractionManager.attackEntity(PlayerEntity, Entity) has had this name and shape in all of them.
 */
@Pseudo
@Mixin(targets = "net.minecraft.class_636")
public abstract class HealthAttackMixin {
	@Inject(method = "method_2918", at = @At("HEAD"), remap = false, require = 0)
	private void nativeClient$attack(@Coerce Object player, @Coerce Object target, CallbackInfo ci) {
		try {
			HealthEvents.attacked(target);
		} catch (Throwable ignored) {
			// never let a health indicator break attacking
		}
	}
}
