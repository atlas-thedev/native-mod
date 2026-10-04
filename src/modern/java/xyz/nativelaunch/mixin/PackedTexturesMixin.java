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
 * Minecraft 1.20.3+: the skin manager reads a profile's packed "textures"
 * Property and unpacks it. Swap in a Native-built Property for players who have
 * a Native wardrobe, then unpack it ourselves so Mojang's texture-domain
 * whitelist does not reject our URLs.
 */
@Pseudo
@Mixin(targets = "com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService")
public abstract class PackedTexturesMixin {
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
