package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nativelaunch.ui.mod.CombatColours;

@Pseudo
@Mixin(targets = "net.minecraft.client.particle.CritParticle$Provider", remap = false)
public abstract class CriticalColoursMixin26 {
    @Inject(method = "createParticle", at = @At("RETURN"), remap = false, require = 0)
    private void nativeClient$colour(CallbackInfoReturnable<Object> cir) {
        CombatColours.particle(cir.getReturnValue(), false);
    }
}
