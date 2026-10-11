package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nativelaunch.ui.mod.Overlays;

/**
 * The vanilla crosshair and potion effect icons on 26.x (Gui on 26.1, Hud from 26.2), skipped while a Native module draws its own.
 * Every hook names the full descriptor, so only the one that exists on the running version is applied.
 */
@Pseudo
@Mixin(targets = {"net.minecraft.client.gui.Gui", "net.minecraft.client.gui.Hud"}, remap = false)
public abstract class HudHideMixin26 {
	@Inject(method = {
			"extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
			"renderCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
			"renderCrosshair(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"},
			at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$crosshair(@Coerce Object ctx, @Coerce Object tick, CallbackInfo ci) {
		if (Overlays.hideCrosshair()) {
			ci.cancel();
		}
	}

	@Inject(method = {
			"extractEffects(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
			"renderEffects(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
			"renderEffects(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"},
			at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$effects(@Coerce Object ctx, @Coerce Object tick, CallbackInfo ci) {
		if (Overlays.hideEffects()) {
			ci.cancel();
		}
	}
}
