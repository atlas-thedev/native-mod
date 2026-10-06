package xyz.nativelaunch.cos26;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import xyz.nativelaunch.cosmetic.Reflect;

/** Minecraft 26.x: keeps {@link CosmeticLayer} in every player renderer's layer list. */
public final class Install implements Runnable {
	@Override
	public void run() {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.getEntityRenderDispatcher() == null) {
			return;
		}
		for (Object renderer : Reflect.fieldValues(client.getEntityRenderDispatcher())) {
			if (renderer instanceof AvatarRenderer) {
				@SuppressWarnings("unchecked")
				final AvatarRenderer<?> avatar = (AvatarRenderer<?>) renderer;
				Reflect.ensure(Reflect.listField(avatar, LivingEntityRenderer.class), CosmeticLayer.class, () -> new CosmeticLayer(avatar));
			}
		}
	}
}
