package xyz.nativelaunch.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.properties.Property;
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
 * Minecraft 1.16 - 1.20.2: the skin manager asks the session service for a player's textures, with
 * requireSecure=true for other players. Lay Native's skin and cape over whatever the profile carries.
 *
 * This runs at HEAD and answers the call itself: with requireSecure=true authlib throws for an unsigned
 * textures property (offline servers with skin plugins send those), which skipped a RETURN hook entirely
 * and left the player on Steve/Alex. The profile's own textures are read with requireSecure=false
 * (re-entering this method is guarded), and returning normally also marks the skin secure on 1.20.2.
 */
@Pseudo
@Mixin(targets = "com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService")
public abstract class LegacyTexturesMixin {
	private static final ThreadLocal<Boolean> NATIVE$INSIDE = new ThreadLocal<Boolean>();

	@Inject(
			method = "getTextures(Lcom/mojang/authlib/GameProfile;Z)Ljava/util/Map;",
			at = @At("HEAD"),
			cancellable = true,
			remap = false,
			require = 0)
	private void nativeClient$getTextures(GameProfile profile, boolean requireSecure,
			CallbackInfoReturnable<Map<Type, MinecraftProfileTexture>> cir) {
		if (profile == null || NATIVE$INSIDE.get() != null) {
			return;
		}
		try {
			Property textures = null;
			try {
				for (Property property : profile.getProperties().get("textures")) {
					textures = property;
					break;
				}
			} catch (RuntimeException ignored) {
				// unreadable properties: treat as none
			}
			boolean premium = textures != null && textures.hasSignature();
			SkinOverride override = NativeBoot.ensure().lookup(profile.getName(), profile.getId(), premium);
			if (override == null) {
				return;
			}
			Map<Type, MinecraftProfileTexture> merged = new EnumMap<Type, MinecraftProfileTexture>(Type.class);
			if (textures != null) {
				NATIVE$INSIDE.set(Boolean.TRUE);
				try {
					Map<Type, MinecraftProfileTexture> original = ((MinecraftSessionService) (Object) this).getTextures(profile, false);
					if (original != null) {
						merged.putAll(original);
					}
				} catch (RuntimeException ignored) {
					// tampered or unreadable textures: Native's still apply
				} finally {
					NATIVE$INSIDE.remove();
				}
			}
			if (override.skinUrl != null) {
				Map<String, String> metadata = override.slim ? Collections.singletonMap("model", "slim") : Collections.<String, String>emptyMap();
				merged.put(Type.SKIN, new MinecraftProfileTexture(override.skinUrl, metadata));
			}
			if (override.capeUrl != null) {
				merged.put(Type.CAPE, new MinecraftProfileTexture(override.capeUrl, Collections.<String, String>emptyMap()));
			}
			if (override.hideCape) {
				merged.remove(Type.CAPE); // a back cosmetic (wings, jetpack...) takes the cape's place
			}
			cir.setReturnValue(merged);
		} catch (Throwable t) {
			Log.warn("Skin hook failed: {}", t.toString());
		}
	}
}
