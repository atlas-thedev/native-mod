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

/** Minecraft 1.16 - 1.21.11 (intermediary names): World.getRainGradient / getThunderGradient; Weather Changer overrides them for the client's world only. */
@Pseudo
@Mixin(targets = "net.minecraft.class_1937", remap = false)
public abstract class WeatherMixin {
	@Inject(method = "method_8430", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$rain(float delta, CallbackInfoReturnable<Float> cir) {
		Float v = WeatherChanger.rain(this);
		if (v != null) {
			cir.setReturnValue(v);
		}
	}

	@Inject(method = "method_8478", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$thunder(float delta, CallbackInfoReturnable<Float> cir) {
		Float v = WeatherChanger.thunder(this);
		if (v != null) {
			cir.setReturnValue(v);
		}
	}
}
