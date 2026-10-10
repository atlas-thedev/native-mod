package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nativelaunch.cosmetic.HeadCover;

/** Minecraft 26.x: the head slot reads empty while a hooded player's render state is built. */
@Pseudo
@Mixin(targets = "net.minecraft.world.entity.LivingEntity")
public abstract class HoodSlotMixin26 {
	@Inject(method = "getItemBySlot", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
	private void nativeClient$equipped(@Coerce Object slot, CallbackInfoReturnable<Object> cir) {
		Object stack = cir.getReturnValue();
		Object out = HeadCover.equipped(this, slot, stack);
		if (out != stack) {
			cir.setReturnValue(out);
		}
	}
}
