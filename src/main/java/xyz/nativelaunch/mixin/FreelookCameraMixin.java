package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import xyz.nativelaunch.ui.mod.Freelook;

/** Minecraft 1.16 - 1.21.11 (intermediary names): Camera.update points the camera with setRotation(yaw, pitch); Freelook turns it by its offset. */
@Pseudo
@Mixin(targets = "net.minecraft.class_4184", remap = false)
public abstract class FreelookCameraMixin {
	@ModifyArgs(method = "method_19321", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_4184;method_19325(FF)V"), remap = false, require = 0)
	private void nativeClient$rotate(Args args) {
		if (Freelook.applying()) {
			args.set(0, Freelook.yaw((Float) args.get(0)));
			args.set(1, Freelook.pitch((Float) args.get(1)));
		}
	}
}
