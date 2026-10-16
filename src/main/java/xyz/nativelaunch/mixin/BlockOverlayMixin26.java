package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xyz.nativelaunch.ui.mod.BlockOverlay;

/**
 * Minecraft 26.x (official names): LevelRenderer.submitBlockOutline passes the outline colour to submitHitOutline: the Block
 * Overlay module swaps the game's default one (black at 40 %) for its own.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.LevelRenderer", remap = false)
public abstract class BlockOverlayMixin26 {
	@ModifyArg(method = "submitBlockOutline", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;submitHitOutline(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/state/level/BlockOutlineRenderState;IFZ)V"), index = 4, remap = false, require = 0)
	private int nativeClient$outline(int color) {
		return BlockOverlay.outline(color);
	}
}
