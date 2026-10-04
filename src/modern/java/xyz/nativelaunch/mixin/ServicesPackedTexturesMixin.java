package xyz.nativelaunch.mixin;

import xyz.nativelaunch.hook.ModernHook;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import com.mojang.authlib.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Same hook for Minecraft 26.3+, where authlib renamed the session service.
 * Only loaded when that class exists (see {@link NativeMixinPlugin#getMixins()}).
 */
@Pseudo
@Mixin(targets = "com.mojang.authlib.services.MinecraftServicesSessionService")
public abstract class ServicesPackedTexturesMixin {
	@Inject(
			method = "getPackedTextures(Lcom/mojang/authlib/GameProfile;)Lcom/mojang/authlib/properties/Property;",
			at = @At("RETURN"),
			cancellable = true,
			remap = false,
			require = 0)
	private void nativeClient$packed(GameProfile profile, CallbackInfoReturnable<Property> cir) {
		ModernHook.packed(profile, cir);
	}

	@Inject(
			method = "unpackTextures(Lcom/mojang/authlib/properties/Property;)Lcom/mojang/authlib/minecraft/MinecraftProfileTextures;",
			at = @At("HEAD"),
			cancellable = true,
			remap = false,
			require = 0)
	private void nativeClient$unpack(Property property, CallbackInfoReturnable<MinecraftProfileTextures> cir) {
		ModernHook.unpack(property, cir);
	}
}
