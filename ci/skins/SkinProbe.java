package sprobe;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Test-only mod: asks the REAL game for the skin it would draw for
 *   - TestAlice, the local player (same UUID as the session), and
 *   - TestBob, another player on an offline-mode server (name-based UUID) whose profile carries an
 *     UNSIGNED textures property, like servers with skin plugins send,
 * through the exact path the game uses (PlayerInfo's skin lookup on 1.20.2+, SkinManager.registerSkins
 * with requireSecure=true before). Prints "SPROBE <name> <texture>" lines; run.sh checks them.
 * Everything is found by type, so the same probe runs on intermediary (1.16 - 1.21.x) and named (26.x).
 */
public class SkinProbe implements ClientModInitializer {
	static Object mc;
	static Class<?> gp;

	public void onInitializeClient() {
		Thread t = new Thread(SkinProbe::go, "sprobe");
		t.setDaemon(true);
		t.start();
	}

	interface Call { Object call() throws Exception; }

	static Object onMain(Call c) throws Exception {
		final Object[] out = new Object[1];
		final Throwable[] err = new Throwable[1];
		final CountDownLatch l = new CountDownLatch(1);
		((Executor) mc).execute(() -> { try { out[0] = c.call(); } catch (Throwable e) { err[0] = e; } l.countDown(); });
		if (!l.await(120, TimeUnit.SECONDS)) throw new RuntimeException("main thread timeout");
		if (err[0] != null) throw new RuntimeException(err[0]);
		return out[0];
	}

	static List<Field> fields(Class<?> c) {
		List<Field> out = new ArrayList<>();
		for (; c != null && c != Object.class; c = c.getSuperclass()) out.addAll(Arrays.asList(c.getDeclaredFields()));
		return out;
	}

	/** PlayerInfo = value type of the Map<UUID, X> in the class Minecraft#getConnection() returns. */
	static Class<?> playerInfoClass() {
		for (Method m : mc.getClass().getMethods()) {
			if (m.getParameterCount() != 0 || m.getReturnType().isPrimitive()) continue;
			for (Field f : fields(m.getReturnType())) {
				if (!(f.getGenericType() instanceof ParameterizedType)) continue;
				ParameterizedType p = (ParameterizedType) f.getGenericType();
				Type[] a = p.getActualTypeArguments();
				if (Map.class.isAssignableFrom(f.getType()) && a.length == 2 && a[0] == UUID.class && a[1] instanceof Class) {
					for (Field g : fields((Class<?>) a[1])) if (g.getType() == gp) return (Class<?>) a[1];
				}
			}
		}
		return null;
	}

	static Object profile(UUID id, String name, String unsignedTextures) throws Exception {
		Class<?> prop = Class.forName("com.mojang.authlib.properties.Property");
		Object textures = unsignedTextures == null ? null : prop.getConstructor(String.class, String.class).newInstance("textures", unsignedTextures);
		try {
			Class<?> pm = Class.forName("com.mojang.authlib.properties.PropertyMap");
			Constructor<?> c3 = gp.getConstructor(UUID.class, String.class, pm);
			com.google.common.collect.Multimap<String, Object> mm = com.google.common.collect.LinkedHashMultimap.create();
			if (textures != null) mm.put("textures", textures);
			return c3.newInstance(id, name, pm.getConstructor(com.google.common.collect.Multimap.class).newInstance(mm));
		} catch (NoSuchMethodException e) {
			Object p = gp.getConstructor(UUID.class, String.class).newInstance(id, name);
			if (textures != null) {
				Object map = gp.getMethod("getProperties").invoke(p);
				map.getClass().getMethod("put", Object.class, Object.class).invoke(map, "textures", textures);
			}
			return p;
		}
	}

	static String modern(Class<?> pi, Object profile) throws Exception {
		Method lookup = null;
		for (Method m : pi.getDeclaredMethods())
			if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == Supplier.class && m.getParameterCount() == 1 && m.getParameterTypes()[0] == gp) lookup = m;
		if (lookup == null) return null;
		lookup.setAccessible(true);
		final Method fl = lookup;
		Supplier<?> s = (Supplier<?>) onMain(() -> fl.invoke(null, profile));
		String last = "?";
		for (int i = 0; i < 100; i++) {
			last = String.valueOf(onMain(s::get));
			if (last.contains("skins/")) return last;
			Thread.sleep(200);
		}
		return last;
	}

	static String legacy(Object profile) throws Exception {
		for (Field f : fields(mc.getClass())) {
			for (Method m : f.getType().getDeclaredMethods()) {
				Class<?>[] p = m.getParameterTypes();
				if (p.length == 3 && p[0] == gp && p[1].isInterface() && p[2] == boolean.class && m.getReturnType() == void.class) {
					f.setAccessible(true);
					Object sm = f.get(mc);
					BlockingQueue<String> got = new LinkedBlockingQueue<>();
					Object cb = Proxy.newProxyInstance(p[1].getClassLoader(), new Class<?>[] {p[1]}, (proxy, method, args) -> {
						if (args != null && args.length == 3) got.add(args[0] + "=" + args[1]);
						return null;
					});
					m.setAccessible(true);
					onMain(() -> m.invoke(sm, profile, cb, true));
					StringBuilder all = new StringBuilder();
					long end = System.currentTimeMillis() + 20000;
					while (System.currentTimeMillis() < end) {
						String x = got.poll(500, TimeUnit.MILLISECONDS);
						if (x != null) all.append(x).append(' ');
						else if (all.length() > 0) break;
					}
					return all.length() == 0 ? "nothing" : all.toString();
				}
			}
		}
		return "no-registerSkins";
	}

	static void go() {
		try {
			while ((mc = FabricLoader.getInstance().getGameInstance()) == null) Thread.sleep(200);
			gp = Class.forName("com.mojang.authlib.GameProfile");
			Thread.sleep(20000);
			System.out.println("SPROBE ready");
			// an unsigned textures property pointing at a Mojang skin, like an offline server's skin plugin sends
			String unsigned = Base64.getEncoder().encodeToString("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/1a4af718455d4aab528e7a61f86fa25e6a369d1768dcb13f7df319a713eb810b\"}}}".getBytes("UTF-8"));
			Object alice = profile(UUID.nameUUIDFromBytes("OfflinePlayer:TestAlice".getBytes("UTF-8")), "TestAlice", null);
			Object bob = profile(UUID.nameUUIDFromBytes("OfflinePlayer:TestBob".getBytes("UTF-8")), "TestBob", unsigned);
			Object carol = profile(UUID.nameUUIDFromBytes("OfflinePlayer:TestCarol".getBytes("UTF-8")), "TestCarol", null);
			Class<?> pi = playerInfoClass();
			boolean useModern = false;
			if (pi != null) for (Method m : pi.getDeclaredMethods()) if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == Supplier.class) useModern = true;
			System.out.println("SPROBE path " + (useModern ? "modern" : "legacy") + " playerInfo=" + (pi == null ? null : pi.getName()));
			for (Object[] who : new Object[][] {{"alice", alice}, {"bob", bob}, {"carol", carol}}) {
				String r = useModern ? modern(pi, who[1]) : legacy(who[1]);
				System.out.println("SPROBE " + who[0] + " " + r);
			}
		} catch (Throwable e) {
			System.out.println("SPROBE FATAL " + e);
			e.printStackTrace(System.out);
		}
		System.out.println("SPROBE DONE");
		System.out.flush();
		Runtime.getRuntime().halt(0);
	}
}
