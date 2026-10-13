package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xyz.nativelaunch.ui.mod.CombatColours;

@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer", remap = false)
public abstract class HitboxGizmoColoursMixin26 {
    @ModifyArg(method = "showHitboxes", at = @At(value = "INVOKE", target = "Lnet/minecraft/gizmos/GizmoStyle;stroke(I)Lnet/minecraft/gizmos/GizmoStyle;"), index = 0, remap = false, require = 0)
    private int nativeClient$colour(int vanilla) { return CombatColours.hitbox(vanilla); }

    @ModifyArg(method = "showHitboxes", at = @At(value = "INVOKE", target = "Lnet/minecraft/gizmos/Gizmos;arrow(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;I)Lnet/minecraft/gizmos/GizmoProperties;"), index = 2, remap = false, require = 0)
    private int nativeClient$direction(int vanilla) { return CombatColours.hitbox(vanilla); }
}
