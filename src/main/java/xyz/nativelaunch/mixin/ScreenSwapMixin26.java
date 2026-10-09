package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nativelaunch.ui.UiRuntime;

/**
 * Native UI, Minecraft 26.x (official names): setScreen(TitleScreen) opens the Native title screen instead.
 * 26.1 keeps setScreen on Minecraft; from 26.2 it lives on Gui.
 */
@Pseudo
@Mixin(targets = {"net.minecraft.client.Minecraft", "net.minecraft.client.gui.Gui"}, remap = false)
public abstract class ScreenSwapMixin26 {
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$swap(@Coerce Object screen, CallbackInfo ci) {
		if (UiRuntime.swapScreen(screen)) {
			ci.cancel();
		}
	}
}
