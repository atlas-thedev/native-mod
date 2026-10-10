package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nativelaunch.cosmetic.HeadCover;

/** Minecraft 26.x: marks the player whose render state is being built, for {@link HoodSlotMixin26}. */
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.entity.player.AvatarRenderer")
public abstract class HoodScopeMixin26 {
	private static final String EXTRACT =
			"extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V";

	@Inject(method = EXTRACT, at = @At("HEAD"), remap = false, require = 0)
	private void nativeClient$stateStart(@Coerce Object entity, @Coerce Object state, float delta, CallbackInfo ci) {
		HeadCover.enter(entity);
	}

	@Inject(method = EXTRACT, at = @At("RETURN"), remap = false, require = 0)
	private void nativeClient$stateEnd(@Coerce Object entity, @Coerce Object state, float delta, CallbackInfo ci) {
		HeadCover.exit();
	}
}
