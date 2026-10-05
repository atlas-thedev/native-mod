package xyz.nativelaunch.hook;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.SignatureState;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import com.mojang.authlib.properties.Property;
import xyz.nativelaunch.NativeBoot;
import xyz.nativelaunch.core.Log;
import xyz.nativelaunch.core.ProfileAccess;
import xyz.nativelaunch.core.SkinOverride;
import xyz.nativelaunch.core.Textures;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The logic both session-service hooks share (the class name differs in 26.3+). */
public final class ModernHook {
	private ModernHook() {
	}

	public static void packed(GameProfile profile, CallbackInfoReturnable<Property> cir) {
		try {
			if (profile == null) {
				return;
			}
			String name = ProfileAccess.name(profile);
			UUID id = ProfileAccess.id(profile);
			Property existing = cir.getReturnValue();
			String existingValue = existing == null ? null : ProfileAccess.propertyValue(existing);
			// Mojang-signed textures that are not ours: a real premium session
			boolean premium = existing != null && ProfileAccess.propertySignature(existing) != null && Textures.parse(existingValue) == null;
			SkinOverride override = NativeBoot.ensure().lookup(name, id, premium);
			if (override == null) {
				return;
			}
			String merged = Textures.pack(existingValue, override, id, name);
			cir.setReturnValue(new Property("textures", merged));
		} catch (Throwable t) {
			Log.warn("Skin hook failed: {}", t.toString());
		}
	}

	public static void unpack(Property property, CallbackInfoReturnable<MinecraftProfileTextures> cir) {
		try {
			if (property == null) {
				return;
			}
			Textures.Parsed parsed = Textures.parse(ProfileAccess.propertyValue(property));
			if (parsed == null) {
				return;
			}
			Map<String, String> slim = Collections.singletonMap("model", "slim");
			Map<String, String> none = Collections.<String, String>emptyMap();
			Map<String, String> capeMeta = none;
			if (parsed.capeStripUrl != null) {
				// keep the animation description on the cape texture; vanilla ignores unknown keys
				capeMeta = new HashMap<String, String>();
				capeMeta.put(Textures.ANIM_STRIP, parsed.capeStripUrl);
				capeMeta.put(Textures.ANIM_FRAMES, Integer.toString(parsed.capeFrames));
				capeMeta.put(Textures.ANIM_FPS, Integer.toString(parsed.capeFps));
			}
			cir.setReturnValue(new MinecraftProfileTextures(
					parsed.skinUrl == null ? null : new MinecraftProfileTexture(parsed.skinUrl, parsed.slim ? slim : none),
					parsed.capeUrl == null ? null : new MinecraftProfileTexture(parsed.capeUrl, capeMeta),
					parsed.elytraUrl == null ? null : new MinecraftProfileTexture(parsed.elytraUrl, none),
					// Native textures come from the Native API, not from a game server, so they are trusted like
					// Mojang's. UNSIGNED would make 1.20.2+ draw Steve/Alex for every player except yourself
					// (PlayerInfo / SkinManager.createLookup drop insecure skins of other players).
					SignatureState.SIGNED));
		} catch (Throwable t) {
			Log.warn("Skin hook failed: {}", t.toString());
		}
	}
}
