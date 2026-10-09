package xyz.nativelaunch.mixin;

import net.minecraft.class_437;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import xyz.nativelaunch.ui.UiRuntime;

/** Native UI, Minecraft 1.16 - 1.21.11: setScreen(TitleScreen) opens the Native title screen instead. */
@Pseudo
@Mixin(targets = "net.minecraft.class_310", remap = false)
public abstract class ScreenSwapMixin {
	@ModifyVariable(method = "method_1507", at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
	private class_437 nativeClient$swap(class_437 screen) {
		return (class_437) UiRuntime.replaceScreen(screen);
	}
}
