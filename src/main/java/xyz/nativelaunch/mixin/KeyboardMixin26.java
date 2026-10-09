package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nativelaunch.ui.Input26;

/** Native UI, Minecraft 26.x: keys and typed text reach the Native UI before the game (GLFW on 26.1/26.2, SDL on 26.3). */
@Pseudo
@Mixin(targets = "net.minecraft.client.KeyboardHandler", remap = false)
public abstract class KeyboardMixin26 {
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$key(long window, int action, @Coerce Object event, CallbackInfo ci) {
		if (Input26.key(action, event)) {
			ci.cancel();
		}
	}

	@Inject(method = "charTyped", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$char(long window, @Coerce Object event, CallbackInfo ci) {
		if (Input26.character(event)) {
			ci.cancel();
		}
	}
}
