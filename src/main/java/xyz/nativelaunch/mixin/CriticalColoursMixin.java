package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nativelaunch.ui.mod.CombatColours;

@Pseudo
@Mixin(targets = "net.minecraft.class_657$class_658", remap = false)
public abstract class CriticalColoursMixin {
    @Inject(method = "method_3013", at = @At("RETURN"), remap = false, require = 0)
    private void nativeClient$colour(CallbackInfoReturnable<Object> cir) {
        CombatColours.particle(cir.getReturnValue(), false);
    }
}
