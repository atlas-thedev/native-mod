package dev.noctra;

import dev.noctra.core.Log;
import dev.noctra.core.UiGate;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.spongepowered.asm.mixin.Mixins;

/**
 * Runs before the game starts. On Minecraft 26.3 it registers the Noctra title-screen mixins;
 * on every other version it does nothing, so those classes (compiled for 26.3) are never loaded.
 */
public final class NoctraPreLaunch implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		try {
			FabricLoader loader = FabricLoader.getInstance();
			if (loader.getEnvironmentType() != EnvType.CLIENT) {
				return;
			}
			ModContainer minecraft = loader.getModContainer("minecraft").orElse(null);
			String version = minecraft == null ? null : minecraft.getMetadata().getVersion().getFriendlyString();
			if (UiGate.supportsTitleScreen(version)) {
				Mixins.addConfiguration("noctra.client.mixins.json");
				Log.info("Noctra title screen enabled for Minecraft " + version);
			}
		} catch (Throwable t) {
			Log.warn("Could not enable the Noctra title screen: " + t);
		}
	}
}
