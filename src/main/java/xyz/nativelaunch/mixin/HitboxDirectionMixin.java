package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xyz.nativelaunch.ui.mod.CombatColours;

@Pseudo
@Mixin(targets = "net.minecraft.class_11686", remap = false)
public abstract class HitboxDirectionMixin {
    @ModifyArg(method = "method_73008", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_9974;method_62298(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;Lorg/joml/Vector3f;Lnet/minecraft/class_243;I)V"), index = 4, remap = false, require = 0)
    private static int nativeClient$direction(int vanilla) { return CombatColours.hitbox(vanilla); }
}
