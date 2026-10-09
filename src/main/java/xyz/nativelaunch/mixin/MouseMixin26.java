package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nativelaunch.ui.Input26;

/** Native UI, Minecraft 26.x: mouse buttons and the wheel reach the Native UI before the game. */
@Pseudo
@Mixin(targets = "net.minecraft.client.MouseHandler", remap = false)
public abstract class MouseMixin26 {
	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$button(long window, @Coerce Object info, int action, CallbackInfo ci) {
		if (Input26.button(info, action)) {
			ci.cancel();
		}
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$scroll(long window, double dx, double dy, CallbackInfo ci) {
		if (Input26.scroll(dx, dy)) {
			ci.cancel();
		}
	}
}
