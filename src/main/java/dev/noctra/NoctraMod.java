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

	/** Animated capes play on Minecraft 26.x; older versions show the cape's first frame. */
	private static void startCapeAnimator() {
		String version = null;
		try {
			ModContainer minecraft = FabricLoader.getInstance().getModContainer("minecraft").orElse(null);
			version = minecraft == null ? null : minecraft.getMetadata().getVersion().getFriendlyString();
		} catch (Throwable ignored) {
			// unknown version: stay on still capes
		}
		if (!AnimGate.supported(version)) {
			Log.info("Animated capes need Minecraft {}+ (this is {}): showing still capes.", AnimGate.MIN_MAJOR, version);
			return;
		}
		try {
			Class.forName("dev.noctra.anim.CapeAnimator").getMethod("start").invoke(null);
		} catch (Throwable t) {
			Log.warn("Animated capes are unavailable ({}): showing still capes.", t.toString());
		}
	}
}
