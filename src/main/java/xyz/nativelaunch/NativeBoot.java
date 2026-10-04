package xyz.nativelaunch;

import xyz.nativelaunch.core.NativeState;
import net.fabricmc.loader.api.FabricLoader;

/** Lets the session-service hooks start the sync even if they run before the mod entrypoint. */
public final class NativeBoot {
	private NativeBoot() {
	}

	public static NativeState ensure() {
		NativeState state = NativeState.get();
		try {
			state.start(FabricLoader.getInstance().getGameDir());
		} catch (Throwable ignored) {
			// loader not ready; the entrypoint will start it
		}
		return state;
	}
}
