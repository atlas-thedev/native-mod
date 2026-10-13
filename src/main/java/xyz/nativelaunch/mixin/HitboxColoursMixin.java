package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import xyz.nativelaunch.ui.mod.CombatColours;

@Pseudo
@Mixin(targets = "net.minecraft.class_898", remap = false)
public abstract class HitboxColoursMixin {
    @ModifyArgs(method = "method_3956", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_761;method_22982(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;Lnet/minecraft/class_238;FFFF)V"), remap = false, require = 0)
    private static void nativeClient$box(Args args) { tint(args); }

    @ModifyArgs(method = "method_3956", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_761;method_22980(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;DDDDDDFFFF)V"), remap = false, require = 0)
    private static void nativeClient$coords(Args args) { tint(args); }

    @ModifyArgs(method = "method_3956", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_9974;method_62295(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;Lnet/minecraft/class_238;FFFF)V"), remap = false, require = 0)
    private static void nativeClient$newBox(Args args) { tint(args); }

    @ModifyArgs(method = "method_3956", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_9974;method_62292(Lnet/minecraft/class_4587;Lnet/minecraft/class_4588;DDDDDDFFFF)V"), remap = false, require = 0)
    private static void nativeClient$newCoords(Args args) { tint(args); }

    @Unique
    private static void tint(Args args) {
        int n = args.size() - 4;
        int col = CombatColours.hitbox((Float)args.get(n), (Float)args.get(n + 1), (Float)args.get(n + 2));
        if (col == 0) return;
        args.set(n, CombatColours.channel(col, 16));
        args.set(n + 1, CombatColours.channel(col, 8));
        args.set(n + 2, CombatColours.channel(col, 0));
    }
}
