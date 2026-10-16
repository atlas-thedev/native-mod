package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import xyz.nativelaunch.ui.mod.NoHurtCam;

/** Minecraft 26.x (official names): GameRenderer.bobHurt is the camera jolt when you are hurt. */
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.GameRenderer", remap = false)
public abstract class NoHurtCamMixin26 {
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$noJolt(CallbackInfo ci) {
		if (NoHurtCam.active()) {
			ci.cancel();
		}
	}
}
