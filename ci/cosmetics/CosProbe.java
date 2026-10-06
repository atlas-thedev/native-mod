package cosprobe;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.Executor;

/**
 * Test-only: once the player is in the world, switches to the third-person front camera and hides the HUD so
 * run.sh can screenshot the player and look for the test cosmetics' colours. Names are found by type (works on
 * intermediary 1.16 - 1.21.x and on the unobfuscated 26.x).
 */
public class CosProbe implements ClientModInitializer {
	public void onInitializeClient() {
		Thread t = new Thread(CosProbe::go, "cosprobe");
		t.setDaemon(true);
		t.start();
	}

	static Class<?> cls(String intermediary, String official) {
		MappingResolver m = FabricLoader.getInstance().getMappingResolver();
		for (String n : new String[] {m.mapClassName("intermediary", intermediary), official}) {
			try {
				return Class.forName(n);
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	static Field fieldOfType(Class<?> owner, Class<?> type) {
		for (Class<?> c = owner; c != null; c = c.getSuperclass())
			for (Field f : c.getDeclaredFields())
				if (type.isAssignableFrom(f.getType()) && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); return f; }
		return null;
	}

	static void go() {
		try {
			Object mc;
			while ((mc = FabricLoader.getInstance().getGameInstance()) == null) Thread.sleep(200);
			Class<?> player = cls("net.minecraft.class_746", "net.minecraft.client.player.LocalPlayer");
			Class<?> options = cls("net.minecraft.class_315", "net.minecraft.client.Options");
			Class<?> perspective = cls("net.minecraft.class_5498", "net.minecraft.client.CameraType");
			Field playerField = fieldOfType(mc.getClass(), player);
			Field optionsField = fieldOfType(mc.getClass(), options);
			long end = System.currentTimeMillis() + 240_000;
			while (playerField.get(mc) == null) {
				if (System.currentTimeMillis() > end) { System.out.println("COSPROBE FATAL never joined the world"); halt(); }
				Thread.sleep(500);
			}
			System.out.println("COSPROBE joined");
			Thread.sleep(8000); // chunks + cosmetics download
			final Object o = optionsField.get(mc);
			final Field persp = fieldOfType(options, perspective);
			((Executor) mc).execute(() -> {
				try {
					persp.set(o, perspective.getEnumConstants()[2]); // THIRD_PERSON_FRONT
					MappingResolver m = FabricLoader.getInstance().getMappingResolver();
					for (String name : new String[] {m.mapFieldName("intermediary", "net.minecraft.class_315", "field_1842", "Z"), "hideGui"}) {
						try {
							Field hud = options.getField(name);
							hud.setBoolean(o, true);
						} catch (Throwable ignored) {
						}
					}
				} catch (Throwable e) {
					System.out.println("COSPROBE FATAL " + e);
				}
			});
			Thread.sleep(6000);
			System.out.println("COSPROBE ready");
			File done = new File("shot.done");
			end = System.currentTimeMillis() + 120_000;
			while (!done.exists() && System.currentTimeMillis() < end) Thread.sleep(250);
		} catch (Throwable e) {
			System.out.println("COSPROBE FATAL " + e);
			e.printStackTrace(System.out);
		}
		halt();
	}

	static void halt() {
		System.out.println("COSPROBE DONE");
		System.out.flush();
		Runtime.getRuntime().halt(0);
	}
}
