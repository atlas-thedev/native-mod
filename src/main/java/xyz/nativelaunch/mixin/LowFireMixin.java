package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import xyz.nativelaunch.ui.mod.LowFire;

/**
 * Minecraft 1.16 - 1.21.11 (intermediary names): the Low Fire module lowers the fire drawn on the screen while you burn.
 * InGameOverlayRenderer.renderFireOverlay moves the flames by (+-0.24, -0.3, 0): the module adds to that downward offset.
 * The translate call is a double one up to 1.19 and a float one after, so both are listed.
 */
@Pseudo
@Mixin(targets = "net.minecraft.class_4603", remap = false)
public abstract class LowFireMixin {
	@ModifyArgs(method = "method_23070", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_4587;method_22904(DDD)V"), remap = false, require = 0)
	private static void nativeClient$lowFireD(Args args) {
		float drop = LowFire.drop();
		if (drop != 0f) {
			args.set(1, (Double) args.get(1) - drop);
		}
	}

	@ModifyArgs(method = "method_23070", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_4587;method_46416(FFF)V"), remap = false, require = 0)
	private static void nativeClient$lowFireF(Args args) {
		float drop = LowFire.drop();
		if (drop != 0f) {
			args.set(1, (Float) args.get(1) - drop);
		}
	}
}
