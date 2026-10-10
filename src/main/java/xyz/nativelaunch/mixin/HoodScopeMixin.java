package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nativelaunch.cosmetic.HeadCover;

/**
 * Minecraft 1.16 - 1.21.x: marks the player being rendered (LivingEntityRenderer.render up to 1.21.1) or having
 * its render state built (PlayerEntityRenderer.updateRenderState from 1.21.2), for {@link HoodSlotMixin}.
 */
@Pseudo
@Mixin(targets = {"net.minecraft.class_922", "net.minecraft.class_1007"})
public abstract class HoodScopeMixin {
	@Inject(method = "method_4054(Lnet/minecraft/class_1309;FFLnet/minecraft/class_4587;Lnet/minecraft/class_4597;I)V",
			at = @At("HEAD"), remap = false, require = 0)
	private void nativeClient$renderStart(@Coerce Object entity, float yaw, float delta, @Coerce Object poses, @Coerce Object buffers, int light,
			CallbackInfo ci) {
		HeadCover.enter(entity);
	}

	@Inject(method = "method_4054(Lnet/minecraft/class_1309;FFLnet/minecraft/class_4587;Lnet/minecraft/class_4597;I)V",
			at = @At("RETURN"), remap = false, require = 0)
	private void nativeClient$renderEnd(@Coerce Object entity, float yaw, float delta, @Coerce Object poses, @Coerce Object buffers, int light,
			CallbackInfo ci) {
		HeadCover.exit();
	}

	@Inject(method = "method_62604", at = @At("HEAD"), remap = false, require = 0)
	private void nativeClient$stateStart(@Coerce Object entity, @Coerce Object state, float delta, CallbackInfo ci) {
		HeadCover.enter(entity);
	}

	@Inject(method = "method_62604", at = @At("RETURN"), remap = false, require = 0)
	private void nativeClient$stateEnd(@Coerce Object entity, @Coerce Object state, float delta, CallbackInfo ci) {
		HeadCover.exit();
	}
}
