package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nativelaunch.ui.UiRuntime;

/** Native UI: paint our layer right before every frame is presented (all versions name this method flipFrame). */
@Pseudo
@Mixin(targets = "com.mojang.blaze3d.systems.RenderSystem", remap = false)
public abstract class UiFrameMixin {
	@Inject(method = "flipFrame", at = @At("HEAD"), remap = false, require = 0)
	private static void nativeClient$frame(CallbackInfo ci) {
		UiRuntime.onFrame();
	}
}
