package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nativelaunch.ui.mod.Overlays;

/** The scoreboard sidebar, 26.x (Gui on 26.1, Hud from 26.2): moved / scaled (vanilla look) or skipped (Native look). */
@Pseudo
@Mixin(targets = {"net.minecraft.client.gui.Gui", "net.minecraft.client.gui.Hud"}, remap = false)
public abstract class SidebarMixin26 {
	@Unique
	private boolean nativeClient$moved;

	@Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$sidebar(@Coerce Object ctx, @Coerce Object objective, CallbackInfo ci) {
		if (Overlays.hideSidebar) {
			ci.cancel();
			return;
		}
		nativeClient$moved = Overlays.moveSidebar && Overlays.push(ctx, Overlays.sidebarX, Overlays.sidebarY, Overlays.sidebarS);
	}

	@Inject(method = "displayScoreboardSidebar", at = @At("RETURN"), remap = false, require = 0)
	private void nativeClient$sidebarEnd(@Coerce Object ctx, @Coerce Object objective, CallbackInfo ci) {
		if (nativeClient$moved) {
			nativeClient$moved = false;
			Overlays.pop(ctx);
		}
	}
}
