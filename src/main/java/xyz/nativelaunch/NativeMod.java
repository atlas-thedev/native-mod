package xyz.nativelaunch;

import xyz.nativelaunch.core.AnimGate;
import xyz.nativelaunch.core.Handoff;
import xyz.nativelaunch.presence.PresenceService;
import xyz.nativelaunch.core.Log;
import xyz.nativelaunch.core.NativeState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/** Client entrypoint: start the Native skin sync and pick up the launcher's account hand-off. */
public final class NativeMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		java.nio.file.Path gameDir = FabricLoader.getInstance().getGameDir();
		NativeState.get().start(gameDir);
		startCapeAnimator();
		startCosmetics();
		startPresence(gameDir);
	}

	/** Discord Rich Presence + the launcher's Relay status (server, world, player count). */
	private static void startPresence(java.nio.file.Path gameDir) {
		try {
			Handoff handoff = Handoff.read(gameDir);
			PresenceService.start(gameDir, NativeState.get().api(), handoff == null ? null : handoff.ticket);
		} catch (Throwable t) {
			Log.warn("Presence is unavailable ({}).", t.toString());
		}
	}

	/** Hats, glasses, back items and shoes: a renderer per Minecraft era. */
	private static void startCosmetics() {
		try {
			xyz.nativelaunch.cosmetic.CosmeticsBoot.start(minecraftVersion());
		} catch (Throwable t) {
			Log.warn("3D cosmetics are unavailable ({}).", t.toString());
		}
	}

	private static String minecraftVersion() {
		try {
			ModContainer minecraft = FabricLoader.getInstance().getModContainer("minecraft").orElse(null);
			return minecraft == null ? null : minecraft.getMetadata().getVersion().getFriendlyString();
		} catch (Throwable ignored) {
			return null;
		}
	}

	/**
	 * Animated capes play on every supported release: Minecraft 26.x uses the animator built
	 * against its own classes, 1.16 - 1.21.x the OpenGL one. Anything else shows the first frame.
	 */
	private static void startCapeAnimator() {
		String version = null;
		try {
			ModContainer minecraft = FabricLoader.getInstance().getModContainer("minecraft").orElse(null);
			version = minecraft == null ? null : minecraft.getMetadata().getVersion().getFriendlyString();
		} catch (Throwable ignored) {
			// unknown version: stay on still capes
		}
		AnimGate.Mode mode = AnimGate.mode(version);
		if (mode == AnimGate.Mode.NONE) {
			Log.info("Animated capes are not available on Minecraft {}: showing still capes.", version);
			return;
		}
		String animator = mode == AnimGate.Mode.MODERN ? "xyz.nativelaunch.anim.CapeAnimator" : "xyz.nativelaunch.glanim.GlCapeAnimator";
		try {
			Class.forName(animator).getMethod("start").invoke(null);
		} catch (Throwable t) {
			Log.warn("Animated capes are unavailable ({}): showing still capes.", t.toString());
		}
	}
}
