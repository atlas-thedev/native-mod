package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nativelaunch.ui.mod.Overlays;

/**
 * The server's scoreboard sidebar (InGameHud.renderScoreboardSidebar, 1.16 - 1.21.11): moved / scaled where the player put it
 * (vanilla look), or skipped while Native draws its own (Native look). The first argument is a MatrixStack or a DrawContext.
 */
@Pseudo
@Mixin(targets = "net.minecraft.class_329", remap = false)
public abstract class SidebarMixin {
	@Unique
	private boolean nativeClient$moved;

	@Inject(method = "method_1757", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$sidebar(@Coerce Object ctx, @Coerce Object objective, CallbackInfo ci) {
		if (Overlays.hideSidebar) {
			ci.cancel();
			return;
		}
		nativeClient$moved = Overlays.moveSidebar && Overlays.push(ctx, Overlays.sidebarX, Overlays.sidebarY, Overlays.sidebarS);
	}

	@Inject(method = "method_1757", at = @At("RETURN"), remap = false, require = 0)
	private void nativeClient$sidebarEnd(@Coerce Object ctx, @Coerce Object objective, CallbackInfo ci) {
		if (nativeClient$moved) {
			nativeClient$moved = false;
			Overlays.pop(ctx);
		}
	}
}
