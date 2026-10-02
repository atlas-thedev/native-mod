package cprobe;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Test-only: proves the Noctra title-screen mixin was applied to the real TitleScreen class. */
public class CProbe implements PreLaunchEntrypoint {
	public void onPreLaunch() {
		new dev.noctra.NoctraPreLaunch().onPreLaunch();
		go();
	}

	static void go() {
		try {
			Class<?> c = Class.forName("net.minecraft.client.gui.screens.TitleScreen", false, CProbe.class.getClassLoader());
			boolean init = false, render = false;
			for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
				if (m.getName().contains("initNoctraTitleScreen")) init = true;
				if (m.getName().contains("renderNoctraTitleScreenOverlay")) render = true;
			}
			System.out.println("CPROBE TitleScreen init=" + init + " render=" + render);
		} catch (Throwable t) {
			System.out.println("CPROBE ERROR " + t);
			t.printStackTrace(System.out);
		}
		System.out.flush();
		Runtime.getRuntime().halt(0);
	}
}
