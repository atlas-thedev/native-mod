package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nativelaunch.ui.UiRuntime;

/** Native UI, Minecraft 26.x: toasts are the last thing every GUI pass draws, so the Native layer goes right after them. */
@Pseudo
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastManager", remap = false)
public abstract class UiFrameMixin26 {
	@Inject(method = "extractRenderState", at = @At("TAIL"), remap = false, require = 0)
	private void nativeClient$frame(@Coerce Object ctx, CallbackInfo ci) {
		UiRuntime.onGuiFrame(ctx);
	}
}
