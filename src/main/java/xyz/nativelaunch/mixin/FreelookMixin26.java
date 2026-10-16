package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import xyz.nativelaunch.ui.mod.Freelook;

/** Minecraft 26.x (official names): Entity.turn is what the mouse calls on your player. */
@Pseudo
@Mixin(targets = "net.minecraft.world.entity.Entity", remap = false)
public abstract class FreelookMixin26 {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void nativeClient$look(double dx, double dy, CallbackInfo ci) {
		if (Freelook.turn(this, dx, dy)) {
			ci.cancel();
		}
	}
}
