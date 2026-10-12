package xyz.nativelaunch.health26;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import xyz.nativelaunch.core.Log;
import xyz.nativelaunch.health.HealthBridge;

/** Started by NativeMod on 26.x: gives the Native menu module its way to talk to the game. */
public final class Boot {
	private Boot() {
	}

	public static void start() {
		HealthBridge.HOLDER.bridge = new HealthBridge() {
			@Override
			public String targetTypeId() {
				try {
					Entity e = Minecraft.getInstance().crosshairPickEntity;
					return e == null ? null : Hooks.typeId(e.getType());
				} catch (Throwable t) {
					return null;
				}
			}

			@Override
			public void message(String text, int rgb, boolean chat) {
				try {
					LocalPlayer me = Minecraft.getInstance().player;
					if (me == null) {
						return;
					}
					MutableComponent c = Component.literal(text);
					if (rgb >= 0) {
						c = c.withColor(rgb);
					}
					if (chat) {
						me.sendSystemMessage(c);
					} else {
						me.sendOverlayMessage(c);
					}
				} catch (Throwable t) {
					Log.warn("Health indicators: could not show a message ({}).", t.toString());
				}
			}
		};
		Log.info("Health indicators ready.");
	}
}
