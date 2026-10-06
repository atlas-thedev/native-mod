package xyz.nativelaunch.cos1219;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import xyz.nativelaunch.cosmetic.Reflect;

/** Minecraft 1.21.9 - 1.21.11: keeps {@link CosmeticFeature} in every player renderer's feature list. */
public final class Install implements Runnable {
	@Override
	public void run() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getEntityRenderDispatcher() == null) {
			return;
		}
		for (Object renderer : Reflect.fieldValues(client.getEntityRenderDispatcher())) {
			if (renderer instanceof PlayerEntityRenderer) {
				final PlayerEntityRenderer player = (PlayerEntityRenderer) renderer;
				Reflect.ensure(Reflect.listField(player, LivingEntityRenderer.class), CosmeticFeature.class, () -> new CosmeticFeature(player));
			}
		}
	}
}
