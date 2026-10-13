package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Persist Native branding whenever Minecraft refreshes its window title. */
@Pseudo
@Mixin(targets = "com.mojang.blaze3d.platform.Window", remap = false)
public abstract class WindowTitleMixin26 {
    @ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false, require = 0)
    private String nativeClient$title(String vanilla) {
        return "Native Client";
    }
}
