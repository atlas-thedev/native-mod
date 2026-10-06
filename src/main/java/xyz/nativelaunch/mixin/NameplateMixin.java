package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nativelaunch.cosmetic.NameTags;

/**
 * Minecraft 1.16 - 1.21.x (intermediary names): puts the Native logo in front of Native players' name tags.
 * Both Entity and PlayerEntity are listed because either may be the one that declares getDisplayName.
 */
@Pseudo
@Mixin(targets = {"net.minecraft.class_1297", "net.minecraft.class_1657"})
public abstract class NameplateMixin {
	@Inject(method = "method_5476", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
	private void nativeClient$displayName(CallbackInfoReturnable<Object> cir) {
		Object text = cir.getReturnValue();
		Object out = NameTags.nameplate(this, text);
		if (out != text) {
			cir.setReturnValue(out);
		}
	}
}
