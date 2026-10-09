package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nativelaunch.ui.mod.Zoom;

/**
 * Native Zoom, Minecraft 1.16 - 1.21.11: divides GameRenderer.getFov. It returns a double up to 1.21.1 and a float
 * from 1.21.2 on, so the boxed type of the new value must match the one the game produced.
 */
@Pseudo
@Mixin(targets = "net.minecraft.class_757", remap = false)
public abstract class ZoomMixin {
	@Inject(method = "method_3196", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
	private void nativeClient$zoom(CallbackInfoReturnable<Object> cir) {
		float factor = Zoom.factor;
		if (factor <= 1.001f) {
			return;
		}
		Object fov = cir.getReturnValue();
		if (fov instanceof Double) {
			cir.setReturnValue(Double.valueOf((Double) fov / factor));
		} else if (fov instanceof Float) {
			cir.setReturnValue(Float.valueOf((Float) fov / factor));
		}
	}
}
