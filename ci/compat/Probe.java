package probe;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Test-only: finds the game's authlib session service and asks it for textures. */
public class Probe implements ModInitializer {
	public void onInitialize() {
		Thread t = new Thread(new Runnable() { public void run() { go(); } }, "probe");
		t.setDaemon(false);
		t.start();
	}

	static Object findService(Object root) throws Exception {
		Class<?> iface1 = null, iface2 = null;
		try { iface1 = Class.forName("com.mojang.authlib.minecraft.MinecraftSessionService"); } catch (Throwable e) {}
		try { iface2 = Class.forName("com.mojang.authlib.minecraft.SessionService"); } catch (Throwable e) {}
		ArrayDeque<Object[]> queue = new ArrayDeque<Object[]>();
		Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
		queue.add(new Object[] {root, 0});
		while (!queue.isEmpty()) {
			Object[] item = queue.poll();
			Object o = item[0];
			int depth = (Integer) item[1];
			if (o == null || !seen.add(o)) continue;
			if ((iface1 != null && iface1.isInstance(o)) || (iface2 != null && iface2.isInstance(o))) return o;
			if (depth >= 3) continue;
			Class<?> c = o.getClass();
			String cn = c.getName();
			if (cn.startsWith("java.") || cn.startsWith("javax.") || cn.startsWith("sun.") || cn.startsWith("jdk.") || cn.startsWith("io.netty") || cn.startsWith("org.apache") || cn.startsWith("com.google") || cn.startsWith("it.unimi")) continue;
			for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
				for (Field f : k.getDeclaredFields()) {
					if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
					try { f.setAccessible(true); queue.add(new Object[] {f.get(o), depth + 1}); } catch (Throwable e) {}
				}
			}
		}
		return null;
	}

	static void go() {
		try {
			Object server = null;
			for (int i = 0; i < 600 && server == null; i++) {
				server = FabricLoader.getInstance().getGameInstance();
				if (server == null) Thread.sleep(250);
			}
			System.out.println("PROBE server=" + (server == null ? null : server.getClass().getName()));
			Object svc = findService(server);
			System.out.println("PROBE service=" + (svc == null ? null : svc.getClass().getName()));
			Class<?> gp = Class.forName("com.mojang.authlib.GameProfile");
			Object alice = gp.getConstructor(UUID.class, String.class).newInstance(UUID.nameUUIDFromBytes("OfflinePlayer:TestAlice".getBytes(StandardCharsets.UTF_8)), "TestAlice");
			Object nobody = gp.getConstructor(UUID.class, String.class).newInstance(UUID.nameUUIDFromBytes("OfflinePlayer:Nobody".getBytes(StandardCharsets.UTF_8)), "Nobody");
			Thread.sleep(2500); // let the first directory snapshot arrive
			String[] outcome = new String[2];
			for (int round = 0; round < 2; round++) {
				Object profile = round == 0 ? alice : nobody;
				String label = round == 0 ? "TestAlice" : "Nobody";
				try {
					Method packed = null;
					try { packed = svc.getClass().getMethod("getPackedTextures", gp); } catch (NoSuchMethodException e) {}
					if (packed != null) {
						Object prop = packed.invoke(svc, profile);
						if (prop == null) { System.out.println("PROBE " + label + " shape=B packed=null"); continue; }
						Object tex = svc.getClass().getMethod("unpackTextures", prop.getClass()).invoke(svc, prop);
						Object skin = tex.getClass().getMethod("skin").invoke(tex);
						Object cape = tex.getClass().getMethod("cape").invoke(tex);
						String su = skin == null ? null : (String) skin.getClass().getMethod("getUrl").invoke(skin);
						String sm = skin == null ? null : (String) skin.getClass().getMethod("getMetadata", String.class).invoke(skin, "model");
						String cu = cape == null ? null : (String) cape.getClass().getMethod("getUrl").invoke(cape);
						System.out.println("PROBE " + label + " shape=B skin=" + su + " model=" + sm + " cape=" + cu);
					} else {
						Method legacy = svc.getClass().getMethod("getTextures", gp, boolean.class);
						Map<?, ?> map = (Map<?, ?>) legacy.invoke(svc, profile, Boolean.FALSE);
						StringBuilder sb = new StringBuilder();
						for (Map.Entry<?, ?> e : map.entrySet()) {
							Object v = e.getValue();
							sb.append(e.getKey()).append('=').append(v.getClass().getMethod("getUrl").invoke(v))
								.append(" model=").append(v.getClass().getMethod("getMetadata", String.class).invoke(v, "model")).append(' ');
						}
						System.out.println("PROBE " + label + " shape=A " + sb);
					}
				} catch (Throwable e) {
					Throwable c = e.getCause() != null ? e.getCause() : e;
					System.out.println("PROBE " + label + " ERROR " + c);
				}
			}
		} catch (Throwable e) {
			System.out.println("PROBE FATAL " + e);
			e.printStackTrace(System.out);
		}
		System.out.println("PROBE DONE");
		System.out.flush();
		Runtime.getRuntime().halt(0);
	}
}
