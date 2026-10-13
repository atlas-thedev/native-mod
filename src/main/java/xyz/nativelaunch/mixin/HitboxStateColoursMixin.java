package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nativelaunch.ui.mod.CombatColours;

@Pseudo
@Mixin(targets = "net.minecraft.class_10932", remap = false)
public abstract class HitboxStateColoursMixin {
    @Shadow @Final @Mutable private float comp_3858;
    @Shadow @Final @Mutable private float comp_3859;
    @Shadow @Final @Mutable private float comp_3860;

    @Inject(method = "<init>(DDDDDDFFFFFF)V", at = @At("RETURN"), remap = false, require = 0)
    private void nativeClient$colour(CallbackInfo ci) {
        int col = CombatColours.hitbox(comp_3858, comp_3859, comp_3860);
        if (col == 0) return;
        comp_3858 = CombatColours.channel(col, 16);
        comp_3859 = CombatColours.channel(col, 8);
        comp_3860 = CombatColours.channel(col, 0);
    }
}
