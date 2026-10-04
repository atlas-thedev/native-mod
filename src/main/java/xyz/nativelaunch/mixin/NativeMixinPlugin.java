package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.objectweb.asm.tree.ClassNode;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Picks the hook that matches the authlib shipped with this Minecraft version:
 * up to 1.20.2 skins are looked up with getTextures(GameProfile, boolean); from
 * 1.20.3 on the game packs and unpacks a "textures" Property instead.
 */
public final class NativeMixinPlugin implements IMixinConfigPlugin {
	private static final String MODERN_MARKER = "com/mojang/authlib/minecraft/MinecraftProfileTextures.class";
	private static final String SERVICES_MARKER = "com/mojang/authlib/services/MinecraftServicesSessionService.class";

	private boolean modern;
	private boolean services;
	private boolean known;

	@Override
	public void onLoad(String mixinPackage) {
		try {
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			if (loader == null) {
				loader = NativeMixinPlugin.class.getClassLoader();
			}
			modern = loader.getResource(MODERN_MARKER) != null;
			services = loader.getResource(SERVICES_MARKER) != null;
			known = true;
		} catch (Throwable t) {
			known = false;
		}
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (!known) {
			return true; // both hooks are no-ops where their target method does not exist
		}
		if (mixinClassName.endsWith("LegacyTexturesMixin")) {
			return !modern;
		}
		if (mixinClassName.endsWith("PackedTexturesMixin")) {
			return modern;
		}
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		// 26.3+ renamed the session service; only register that hook where the class exists
		return known && services ? Collections.singletonList("ServicesPackedTexturesMixin") : null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
