package xyz.nativelaunch.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import xyz.nativelaunch.NativeBoot;
import xyz.nativelaunch.core.Log;
import xyz.nativelaunch.core.SkinOverride;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Minecraft 1.16 - 1.20.2: the skin manager asks the session service for a
 * player's textures. Lay Native's skin and cape over whatever it found.
 */
@Pseudo
@Mixin(targets = "com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService")
public abstract class LegacyTexturesMixin {
	@Inject(
			method = "getTextures(Lcom/mojang/authlib/GameProfile;Z)Ljava/util/Map;",
			at = @At("RETURN"),
			cancellable = true,
			remap = false,
			require = 0)
	private void nativeClient$getTextures(GameProfile profile, boolean requireSecure,
			CallbackInfoReturnable<Map<Type, MinecraftProfileTexture>> cir) {
		try {
			if (profile == null) {
				return;
			}
			SkinOverride override = NativeBoot.ensure().lookup(profile.getName(), profile.getId());
			if (override == null) {
				return;
			}
			Map<Type, MinecraftProfileTexture> merged = new EnumMap<Type, MinecraftProfileTexture>(Type.class);
			Map<Type, MinecraftProfileTexture> original = cir.getReturnValue();
			if (original != null) {
				merged.putAll(original);
			}
			if (override.skinUrl != null) {
				Map<String, String> metadata = override.slim ? Collections.singletonMap("model", "slim") : Collections.<String, String>emptyMap();
				merged.put(Type.SKIN, new MinecraftProfileTexture(override.skinUrl, metadata));
			}
			if (override.capeUrl != null) {
				merged.put(Type.CAPE, new MinecraftProfileTexture(override.capeUrl, Collections.<String, String>emptyMap()));
			}
			cir.setReturnValue(merged);
		} catch (Throwable t) {
			Log.warn("Skin hook failed: {}", t.toString());
		}
	}
}
