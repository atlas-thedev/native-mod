package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nativelaunch.ui.mod.ShulkerTooltip;

/**
 * Minecraft 26.x (official names): AbstractContainerScreen.extractTooltip (renderTooltip on older 26 builds) is skipped
 * while the Shulker Tooltip panel shows the hovered box.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.AbstractContainerScreen", remap = false)
public abstract class ShulkerTooltipMixin26 {
	@Inject(method = {"extractTooltip", "renderTooltip"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$hideShulkerTooltip(CallbackInfo ci) {
		if (ShulkerTooltip.hidesVanilla()) {
			ci.cancel();
		}
	}
}
