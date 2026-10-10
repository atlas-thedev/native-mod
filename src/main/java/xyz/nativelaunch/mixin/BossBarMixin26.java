package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nativelaunch.ui.mod.Overlays;

/** The server's boss bars (BossHealthOverlay.extractRenderState, 26.x): moved / scaled (vanilla look) or skipped (Native look). */
@Pseudo
@Mixin(targets = "net.minecraft.client.gui.components.BossHealthOverlay", remap = false)
public abstract class BossBarMixin26 {
	@Unique
	private boolean nativeClient$moved;

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$bossBars(@Coerce Object ctx, CallbackInfo ci) {
		if (Overlays.hideBossBars) {
			ci.cancel();
			return;
		}
		nativeClient$moved = Overlays.moveBossBars && Overlays.push(ctx, Overlays.bossX, Overlays.bossY, Overlays.bossS);
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"), remap = false, require = 0)
	private void nativeClient$bossBarsEnd(@Coerce Object ctx, CallbackInfo ci) {
		if (nativeClient$moved) {
			nativeClient$moved = false;
			Overlays.pop(ctx);
		}
	}
}
