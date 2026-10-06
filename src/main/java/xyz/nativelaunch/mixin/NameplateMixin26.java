package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nativelaunch.cosmetic.NameTags;

/**
 * Minecraft 26.x (official names): puts the Native logo in front of Native players' name tags.
 * Both Entity and PlayerEntity are listed because either may be the one that declares getDisplayName.
 */
@Pseudo
@Mixin(targets = {"net.minecraft.world.entity.Entity", "net.minecraft.world.entity.player.Player"})
public abstract class NameplateMixin26 {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
	private void nativeClient$displayName(CallbackInfoReturnable<Object> cir) {
		Object text = cir.getReturnValue();
		Object out = NameTags.nameplate(this, text);
		if (out != text) {
			cir.setReturnValue(out);
		}
	}
}
