package xyz.nativelaunch.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nativelaunch.health26.Hooks;

/** Health indicators (26.x): fill in the indicator while the state is extracted, draw it when the state is submitted. */
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.entity.LivingEntityRenderer", remap = false)
public abstract class HealthRendererMixin26 {
	private static final String EXTRACT =
			"extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V";
	private static final String SUBMIT =
			"submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
					+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

	@Inject(method = EXTRACT, at = @At("TAIL"), remap = false, require = 0)
	private void nativeHealth$extract(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
		Hooks.extract(entity, state);
	}

	@Inject(method = SUBMIT, at = @At("TAIL"), remap = false, require = 0)
	private void nativeHealth$submit(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera, CallbackInfo ci) {
		Hooks.submit(state, poseStack, collector, camera);
	}
}
