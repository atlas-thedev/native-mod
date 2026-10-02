package dev.noctra;

import dev.noctra.core.NoctraState;
import net.fabricmc.loader.api.FabricLoader;

/** Lets the session-service hooks start the sync even if they run before the mod entrypoint. */
public final class NoctraBoot {
	private NoctraBoot() {
	}

	public static NoctraState ensure() {
		NoctraState state = NoctraState.get();
		try {
			state.start(FabricLoader.getInstance().getGameDir());
		} catch (Throwable ignored) {
			// loader not ready; the entrypoint will start it
		}
		return state;
	}
}
