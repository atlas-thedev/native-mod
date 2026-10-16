package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nativelaunch.ui.mod.ShulkerTooltip;

/**
 * Minecraft 1.16 - 1.21.11 (intermediary names): HandledScreen.drawMouseoverTooltip is skipped while the Shulker Tooltip
 * panel shows the hovered box, so the game's own tooltip never sits on top of it.
 */
@Pseudo
@Mixin(targets = "net.minecraft.class_465", remap = false)
public abstract class ShulkerTooltipMixin {
	@Inject(method = "method_2380", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$hideShulkerTooltip(CallbackInfo ci) {
		if (ShulkerTooltip.hidesVanilla()) {
			ci.cancel();
		}
	}
}
