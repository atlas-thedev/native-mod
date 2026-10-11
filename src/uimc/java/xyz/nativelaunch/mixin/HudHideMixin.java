package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nativelaunch.ui.mod.Overlays;

/**
 * The vanilla crosshair and potion effect icons (InGameHud.renderCrosshair / renderStatusEffectOverlay, 1.16 - 1.21.11), skipped
 * while a Native module draws its own. The methods take a MatrixStack (1.16 - 1.19), a DrawContext (1.20) or a DrawContext and a
 * RenderTickCounter (1.21+); each shape has its own hook so a version only ever matches the one that exists.
 */
@Pseudo
@Mixin(targets = "net.minecraft.class_329", remap = false)
public abstract class HudHideMixin {
	@Inject(method = {"method_1736(Lnet/minecraft/class_4587;)V", "method_1736(Lnet/minecraft/class_332;)V"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$crosshair(@Coerce Object ctx, CallbackInfo ci) {
		if (Overlays.hideCrosshair()) {
			ci.cancel();
		}
	}

	@Inject(method = "method_1736(Lnet/minecraft/class_332;Lnet/minecraft/class_9779;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$crosshair2(@Coerce Object ctx, @Coerce Object tick, CallbackInfo ci) {
		if (Overlays.hideCrosshair()) {
			ci.cancel();
		}
	}

	@Inject(method = {"method_1765(Lnet/minecraft/class_4587;)V", "method_1765(Lnet/minecraft/class_332;)V"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$effects(@Coerce Object ctx, CallbackInfo ci) {
		if (Overlays.hideEffects()) {
			ci.cancel();
		}
	}

	@Inject(method = "method_1765(Lnet/minecraft/class_332;Lnet/minecraft/class_9779;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$effects2(@Coerce Object ctx, @Coerce Object tick, CallbackInfo ci) {
		if (Overlays.hideEffects()) {
			ci.cancel();
		}
	}
}
