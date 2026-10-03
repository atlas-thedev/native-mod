package cprobe;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Test-only: registers a 64x32 cape texture under the id the game would use, then reads it back over time. */
public class ClientProbe implements ClientModInitializer {
	static Object mc;
	public void onInitializeClient() {
		Thread t = new Thread(ClientProbe::go, "cprobe");
		t.setDaemon(true);
		t.start();
	}
	static String sha1Chars(String v) throws Exception {
		byte[] b = new byte[v.length() * 2];
		for (int i = 0; i < v.length(); i++) { b[i * 2] = (byte) v.charAt(i); b[i * 2 + 1] = (byte) (v.charAt(i) >>> 8); }
		StringBuilder s = new StringBuilder();
		for (byte x : MessageDigest.getInstance("SHA-1").digest(b)) s.append(String.format("%02x", x));
		return s.toString();
	}
	static Object onRender(Callable0 c) throws Exception {
		final Object[] out = new Object[1];
		final Throwable[] err = new Throwable[1];
		final java.util.concurrent.CountDownLatch l = new java.util.concurrent.CountDownLatch(1);
		((Executor) mc).execute(() -> { try { out[0] = c.call(); } catch (Throwable e) { err[0] = e; } l.countDown(); });
		l.await();
		if (err[0] != null) throw new RuntimeException(err[0]);
		return out[0];
	}
	interface Callable0 { Object call() throws Exception; }
	static Method findMethod(Class<?> c, Class<?> ret, Class<?>... params) {
		for (Method m : c.getMethods()) if (Arrays.equals(m.getParameterTypes(), params) && (ret == null || m.getReturnType() == ret)) return m;
		return null;
	}
	static int glId(Object tex) throws Exception {
		Class<?> at = Class.forName("net.minecraft.class_1044");
		try { return (Integer) at.getMethod("method_4624").invoke(tex); } catch (NoSuchMethodException e) {}
		Object gpu = at.getMethod("method_68004").invoke(tex);
		return (Integer) gpu.getClass().getMethod("method_68427").invoke(gpu);
	}
	static void go() {
		try {
			while ((mc = FabricLoader.getInstance().getGameInstance()) == null) Thread.sleep(200);
			Class<?> mcc = Class.forName("net.minecraft.class_310");
			Thread.sleep(15000); // let the game reach the title screen
			System.out.println("CPROBE game ready");
			StringBuilder bb = new StringBuilder(); for (int i = 0; i < 64; i++) bb.append("b"); String sha = sha1Chars(bb.toString());
			Object tex = onRender(() -> {
				Class<?> idc = Class.forName("net.minecraft.class_2960");
				Object id;
				try { id = idc.getConstructor(String.class).newInstance("capes/" + sha); }
				catch (NoSuchMethodException e) { id = idc.getMethod("method_60654", String.class).invoke(null, "minecraft:capes/" + sha); }
				Class<?> ni = Class.forName("net.minecraft.class_1011");
				Object image = ni.getConstructor(int.class, int.class, boolean.class).newInstance(64, 32, true);
				Class<?> dt = Class.forName("net.minecraft.class_1043");
				Object t;
				try { t = dt.getConstructor(ni).newInstance(image); }
				catch (NoSuchMethodException e) { t = dt.getConstructor(Supplier.class, ni).newInstance((Supplier<String>) () -> "probe-cape", image); }
				Object tm = mcc.getMethod("method_1531").invoke(mc);
				Method reg = findMethod(tm.getClass(), void.class, idc, Class.forName("net.minecraft.class_1044"));
				reg.invoke(tm, id, t);
				System.out.println("CPROBE registered " + id + " via " + reg.getName());
				return t;
			});
			Set<String> seen = new LinkedHashSet<>();
			for (int i = 0; i < 24; i++) {
				Thread.sleep(130);
				String px = (String) onRender(() -> {
					int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
					GL11.glBindTexture(GL11.GL_TEXTURE_2D, glId(tex));
					ByteBuffer buf = ByteBuffer.allocateDirect(64 * 32 * 4);
					GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
					GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
					return String.format("%02x%02x%02x%02x", buf.get(0), buf.get(1), buf.get(2), buf.get(3)) + "/" + String.format("%02x%02x%02x", buf.get(64*32*4-4), buf.get(64*32*4-3), buf.get(64*32*4-2));
				});
				seen.add(px);
				System.out.println("CPROBE sample " + i + " " + px);
			}
			System.out.println("CPROBE distinct=" + seen.size() + " " + seen);
		} catch (Throwable e) {
			System.out.println("CPROBE FATAL " + e);
			e.printStackTrace(System.out);
		}
		System.out.println("CPROBE DONE");
		System.out.flush();
		Runtime.getRuntime().halt(0);
	}
}
