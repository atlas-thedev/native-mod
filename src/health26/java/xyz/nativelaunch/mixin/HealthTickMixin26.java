package xyz.nativelaunch.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nativelaunch.health26.Hooks;

/** Health indicators (26.x): once per client tick, work out what is under the crosshair and tidy up. */
@Pseudo
@Mixin(targets = "net.minecraft.client.Minecraft", remap = false)
public abstract class HealthTickMixin26 {
	@Inject(method = "tick()V", at = @At("TAIL"), remap = false, require = 0)
	private void nativeHealth$tick(CallbackInfo ci) {
		Hooks.clientTick((Minecraft) (Object) this);
	}
}
