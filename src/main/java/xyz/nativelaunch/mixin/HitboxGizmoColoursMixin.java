package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xyz.nativelaunch.ui.mod.CombatColours;

@Pseudo
@Mixin(targets = "net.minecraft.class_12155", remap = false)
public abstract class HitboxGizmoColoursMixin {
    @ModifyArg(method = "method_75432", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_12179;method_75535(I)Lnet/minecraft/class_12179;"), index = 0, remap = false, require = 0)
    private int nativeClient$colour(int vanilla) { return CombatColours.hitbox(vanilla); }
}
