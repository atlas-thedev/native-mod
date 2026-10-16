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

/**
 * Minecraft 1.16 - 1.21.11 (intermediary names): the time of day the client world reports comes from ClientWorld.Properties
 * (the server's own world, in singleplayer, has other properties and is not touched). Time Changer swaps the time of day, keeping the day.
 */
@Pseudo
@Mixin(targets = "net.minecraft.class_638$class_5271", remap = false)
public abstract class TimeChangerMixin {
	@Inject(method = "method_217", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
	private void nativeClient$time(CallbackInfoReturnable<Long> cir) {
		cir.setReturnValue(TimeChanger.map(cir.getReturnValue()));
	}
}
