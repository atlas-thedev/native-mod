package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nativelaunch.cosmetic.HeadCover;

/**
 * Minecraft 1.16 - 1.21.x: the head slot reads empty while a hooded player is rendered (no helmet or skull
 * through the hood). LivingEntity declares it from 1.21.5, PlayerEntity before that.
 */
@Pseudo
@Mixin(targets = {"net.minecraft.class_1309", "net.minecraft.class_1657"})
public abstract class HoodSlotMixin {
	@Inject(method = "method_6118", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
	private void nativeClient$equipped(@Coerce Object slot, CallbackInfoReturnable<Object> cir) {
		Object stack = cir.getReturnValue();
		Object out = HeadCover.equipped(this, slot, stack);
		if (out != stack) {
			cir.setReturnValue(out);
		}
	}
}
