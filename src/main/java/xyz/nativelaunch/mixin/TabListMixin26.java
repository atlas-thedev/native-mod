package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nativelaunch.cosmetic.NameTags;

/** Minecraft 26.x (official names): the Native logo in front of Native players' names in the tab list. */
@Pseudo
@Mixin(targets = "net.minecraft.client.gui.components.PlayerTabOverlay")
public abstract class TabListMixin26 {
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
	private void nativeClient$tabName(@Coerce Object entry, CallbackInfoReturnable<Object> cir) {
		Object text = cir.getReturnValue();
		Object out = NameTags.tab(entry, text);
		if (out != text) {
			cir.setReturnValue(out);
		}
	}
}
