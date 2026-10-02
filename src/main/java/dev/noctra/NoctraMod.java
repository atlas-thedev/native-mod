package dev.noctra;

import dev.noctra.core.NoctraState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/** Client entrypoint: start the Noctra skin sync and pick up the launcher's account hand-off. */
public final class NoctraMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		NoctraState.get().start(FabricLoader.getInstance().getGameDir());
	}
}
