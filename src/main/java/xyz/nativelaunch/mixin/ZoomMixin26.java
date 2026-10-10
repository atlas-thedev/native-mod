package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nativelaunch.ui.mod.Zoom;

/** Native Zoom, Minecraft 26.x: the camera works out its field of view in Camera.calculateFov. */
@Pseudo
@Mixin(targets = "net.minecraft.client.Camera", remap = false)
public abstract class ZoomMixin26 {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
	private void nativeClient$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
		float factor = Zoom.factor;
		if (factor > 1.001f) {
			cir.setReturnValue(cir.getReturnValue() / factor);
		}
	}
}
