package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nativelaunch.cosmetic.HeadCover;

/** Minecraft 26.x: no skin hat layer under a hood (Avatar.isModelPartShown). */
@Pseudo
@Mixin(targets = "net.minecraft.world.entity.Avatar")
public abstract class HoodHatMixin26 {
	@Inject(method = "isModelPartShown", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
	private void nativeClient$partShown(@Coerce Object part, CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue()) && HeadCover.hidesPart(this, part)) {
			cir.setReturnValue(Boolean.FALSE);
		}
	}
}
