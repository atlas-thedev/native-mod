package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import xyz.nativelaunch.ui.mod.LowFire;

/**
 * Minecraft 26.x (official names): the fire quads are built in the lambda behind ScreenEffectRenderer.submitFire, each
 * moved by Matrix4f.translate(+-0.24, -0.3, 0): the Low Fire module adds to that downward offset.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.ScreenEffectRenderer", remap = false)
public abstract class LowFireMixin26 {
	@ModifyArgs(method = "lambda$submitFire$0", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;translate(FFF)Lorg/joml/Matrix4f;"), remap = false, require = 0)
	private static void nativeClient$lowFire(Args args) {
		float drop = LowFire.drop();
		if (drop != 0f) {
			args.set(1, (Float) args.get(1) - drop);
		}
	}
}
