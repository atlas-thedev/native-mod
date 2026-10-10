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
	private static final String YGGDRASIL_MARKER = "com/mojang/authlib/yggdrasil/YggdrasilMinecraftSessionService.class";
	// 26.x ships unobfuscated (official names); older versions run on intermediary names
	private static final String OFFICIAL_MARKER = "net/minecraft/client/Minecraft.class";
	private static final String INTERMEDIARY_MARKER = "net/minecraft/class_310.class";

	private boolean modern;
	private boolean services;
	private boolean yggdrasil;
	private boolean known;
	/** 1 = official names (26.x), -1 = intermediary (older), 0 = unknown. */
	private int naming;
	private ClassLoader loader;

	@Override
	public void onLoad(String mixinPackage) {
		try {
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			if (loader == null) {
				loader = NativeMixinPlugin.class.getClassLoader();
			}
			modern = loader.getResource(MODERN_MARKER) != null;
			services = loader.getResource(SERVICES_MARKER) != null;
			yggdrasil = loader.getResource(YGGDRASIL_MARKER) != null;
			this.loader = loader;
			boolean official = loader.getResource(OFFICIAL_MARKER) != null;
			boolean intermediary = loader.getResource(INTERMEDIARY_MARKER) != null;
			naming = official == intermediary ? 0 : (official ? 1 : -1);
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
			return !modern && yggdrasil;
		}
		if (mixinClassName.endsWith("ServicesPackedTexturesMixin")) {
			return services;
		}
		if (mixinClassName.endsWith("PackedTexturesMixin")) {
			return modern && yggdrasil;
		}
		// Game hooks come in two flavours: "*26" for the unobfuscated 26.x names and
		// the plain ones for intermediary names. Loading the wrong set only produces
		// "Error loading class" noise (and confuses crash analysis), so skip it.
		if (naming != 0) {
			boolean for26 = mixinClassName.endsWith("26");
			if (naming > 0 ? !for26 : for26) {
				return false;
			}
		}
		// hooks list classes from several versions (e.g. PlayerLikeEntity only exists from 1.21.9): skip the
		// ones this version doesn't have instead of logging "Error loading class"
		return loader == null || loader.getResource(targetClassName.replace('.', '/') + ".class") != null;
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
