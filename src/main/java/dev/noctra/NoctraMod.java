package dev.noctra;

import dev.noctra.core.AnimGate;
import dev.noctra.core.Log;
import dev.noctra.core.NoctraState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/** Client entrypoint: start the Noctra skin sync and pick up the launcher's account hand-off. */
public final class NoctraMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		NoctraState.get().start(FabricLoader.getInstance().getGameDir());
		startCapeAnimator();
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
		String animator = mode == AnimGate.Mode.MODERN ? "dev.noctra.anim.CapeAnimator" : "dev.noctra.glanim.GlCapeAnimator";
		try {
			Class.forName(animator).getMethod("start").invoke(null);
		} catch (Throwable t) {
			Log.warn("Animated capes are unavailable ({}): showing still capes.", t.toString());
		}
	}
}
