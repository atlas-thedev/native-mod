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

/** Minecraft 26.x (official names): Camera.alignWithEntity points the camera with setRotation(yRot, xRot). */
@Pseudo
@Mixin(targets = "net.minecraft.client.Camera", remap = false)
public abstract class FreelookCameraMixin26 {
	@ModifyArgs(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"), remap = false, require = 0)
	private void nativeClient$rotate(Args args) {
		if (Freelook.applying()) {
			args.set(0, Freelook.yaw((Float) args.get(0)));
			args.set(1, Freelook.pitch((Float) args.get(1)));
		}
	}
}
