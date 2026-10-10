package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nativelaunch.cosmetic.HeadCover;

/** Minecraft 1.16 - 1.21.x: no skin hat layer under a hood (PlayerEntity / PlayerLikeEntity.isPartVisible). */
@Pseudo
@Mixin(targets = {"net.minecraft.class_1657", "net.minecraft.class_11890"})
public abstract class HoodHatMixin {
	@Inject(method = {"method_7348", "method_74091"}, at = @At("RETURN"), cancellable = true, remap = false, require = 0)
	private void nativeClient$partShown(@Coerce Object part, CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue()) && HeadCover.hidesPart(this, part)) {
			cir.setReturnValue(Boolean.FALSE);
		}
	}
}
