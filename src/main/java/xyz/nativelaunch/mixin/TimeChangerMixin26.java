package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import xyz.nativelaunch.ui.mod.TimeChanger;

/** Minecraft 26.x (official names): the client's world clock (sun, sky, moon) reads ClientClockInstance.totalTicks. */
@Pseudo
@Mixin(targets = "net.minecraft.client.ClientClockManager$ClientClockInstance", remap = false)
public abstract class TimeChangerMixin26 {
	@Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
	private void nativeClient$time(CallbackInfoReturnable<Long> cir) {
		cir.setReturnValue(TimeChanger.map(cir.getReturnValue()));
	}
}
