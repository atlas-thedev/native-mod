package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import xyz.nativelaunch.ui.mod.WeatherChanger;

/** Minecraft 26.x (official names): Level.getRainLevel / getThunderLevel. */
@Pseudo
@Mixin(targets = "net.minecraft.world.level.Level", remap = false)
public abstract class WeatherMixin26 {
	@Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$rain(float delta, CallbackInfoReturnable<Float> cir) {
		Float v = WeatherChanger.rain(this);
		if (v != null) {
			cir.setReturnValue(v);
		}
	}

	@Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$thunder(float delta, CallbackInfoReturnable<Float> cir) {
		Float v = WeatherChanger.thunder(this);
		if (v != null) {
			cir.setReturnValue(v);
		}
	}
}
