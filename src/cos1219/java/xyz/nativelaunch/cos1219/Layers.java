package xyz.nativelaunch.cos1219;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;

/**
 * Entity render layers moved in 1.21.11 (RenderLayer.getEntityCutoutNoCull -> RenderLayers.entityCutoutNoCull),
 * so they are looked up by their stable intermediary names.
 */
final class Layers {
	private static final String ID = "Lnet/minecraft/class_2960;";
	private static final String LAYER = "Lnet/minecraft/class_1921;";
	private static Method cutout;
	private static Method translucent;
	private static Method eyes;

	private Layers() {
	}

	static RenderLayer cutout(Identifier id) throws ReflectiveOperationException {
		resolve();
		return (RenderLayer) cutout.invoke(null, id);
	}

	static RenderLayer translucent(Identifier id) throws ReflectiveOperationException {
		resolve();
		return (RenderLayer) translucent.invoke(null, id);
	}

	static RenderLayer eyes(Identifier id) throws ReflectiveOperationException {
		resolve();
		return (RenderLayer) eyes.invoke(null, id);
	}

	private static synchronized void resolve() throws ReflectiveOperationException {
		if (cutout != null) {
			return;
		}
		MappingResolver mappings = FabricLoader.getInstance().getMappingResolver();
		String desc = "(" + ID + ")" + LAYER;
		try {
			// 1.21.9 - 1.21.10: static factories on RenderLayer
			cutout = find(mappings, "net.minecraft.class_1921", "method_23578", desc);
			translucent = find(mappings, "net.minecraft.class_1921", "method_23580", desc);
			eyes = find(mappings, "net.minecraft.class_1921", "method_23026", desc);
		} catch (ReflectiveOperationException e) {
			// 1.21.11: RenderLayers
			cutout = find(mappings, "net.minecraft.class_12249", "method_75994", desc);
			translucent = find(mappings, "net.minecraft.class_12249", "method_76000", desc);
			eyes = find(mappings, "net.minecraft.class_12249", "method_76014", desc);
		}
	}

	private static Method find(MappingResolver mappings, String owner, String method, String desc) throws ReflectiveOperationException {
		Class<?> type = Class.forName(mappings.mapClassName("intermediary", owner));
		Method m = type.getMethod(mappings.mapMethodName("intermediary", owner, method, desc), Identifier.class);
		return m;
	}
}
