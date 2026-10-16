package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import xyz.nativelaunch.ui.mod.BlockOverlay;

/**
 * Minecraft 1.16 - 1.21.11 (intermediary names): the Block Overlay module recolours the outline around the targeted block.
 * WorldRenderer.drawBlockOutline hands the colour to the shape-outline drawer as four floats up to 1.21.1, as an ARGB int
 * from 1.21.2 (both listed). The module only replaces the game's default outline (black at 40 %).
 */
@Pseudo
@Mixin(targets = "net.minecraft.class_761", remap = false)
public abstract class BlockOverlayMixin {
	@ModifyArgs(method = "method_22712", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_761;method_3291(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;Lnet/minecraft/class_265;DDDFFFF)V"), remap = false, require = 0)
	private static void nativeClient$outlineFloats(Args args) {
		float[] rgba = {(Float) args.get(6), (Float) args.get(7), (Float) args.get(8), (Float) args.get(9)};
		if (BlockOverlay.outline(rgba)) {
			args.set(6, rgba[0]);
			args.set(7, rgba[1]);
			args.set(8, rgba[2]);
			args.set(9, rgba[3]);
		}
	}

	@ModifyArg(method = "method_22712", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_9974;method_62296(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;Lnet/minecraft/class_265;DDDI)V"), index = 6, remap = false, require = 0)
	private static int nativeClient$outlineInt(int color) {
		return BlockOverlay.outline(color);
	}
}
