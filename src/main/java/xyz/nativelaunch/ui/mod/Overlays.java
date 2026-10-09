package xyz.nativelaunch.ui.mod;

/**
 * Vanilla HUD parts Native redraws (scoreboard sidebar, boss bars). The game bridge fills the data; the mixins skip the vanilla
 * drawing only while our module is on <b>and</b> the last read worked, so a version we can't read keeps the vanilla HUD.
 */
public final class Overlays {
	public static volatile boolean hideSidebar, hideBossBars;
	/** Vanilla look: the game still draws them, moved and scaled with this GUI-px transform (translate, then scale). */
	public static volatile boolean moveSidebar, moveBossBars;
	public static volatile float sidebarX, sidebarY, sidebarS = 1, bossX, bossY, bossS = 1;

	private Overlays() {
	}

	private static Class<?> stackClass, graphicsClass;
	private static java.lang.reflect.Method pose, push, pop, translate, scale, jPush, jPop, jTranslate, jScale;

	/** Pushes the move/scale onto the matrix of the vanilla draw call (PoseStack up to 1.21.5, JOML Matrix3x2fStack after). */
	public static boolean push(Object ctx, float x, float y, float s) {
		try {
			Object m = ctx;
			if (ctx.getClass().getName().equals("net.minecraft.class_332") || graphicsClass != null && graphicsClass.isInstance(ctx)) {
				if (pose == null) {
					graphicsClass = ctx.getClass();
					pose = graphicsClass.getMethod("method_51448");
				}
				m = pose.invoke(ctx);
			}
			if (m == null) {
				return false;
			}
			if (m.getClass().getName().startsWith("org.joml")) {
				if (jPush == null) {
					Class<?> c = m.getClass();
					jPush = c.getMethod("pushMatrix");
					jPop = c.getMethod("popMatrix");
					jTranslate = c.getMethod("translate", float.class, float.class);
					jScale = c.getMethod("scale", float.class, float.class);
				}
				jPush.invoke(m);
				jTranslate.invoke(m, x, y);
				jScale.invoke(m, s, s);
				return true;
			}
			if (push == null) {
				Class<?> c = m.getClass();
				stackClass = c;
				push = c.getMethod("method_22903");
				pop = c.getMethod("method_22909");
				translate = c.getMethod("method_22904", double.class, double.class, double.class);
				scale = c.getMethod("method_22905", float.class, float.class, float.class);
			}
			push.invoke(m);
			translate.invoke(m, (double) x, (double) y, 0d);
			scale.invoke(m, s, s, 1f);
			return true;
		} catch (Throwable t) {
			moveSidebar = moveBossBars = false; // can't move it on this version: leave vanilla alone
			return false;
		}
	}

	public static void pop(Object ctx) {
		try {
			Object m = graphicsClass != null && graphicsClass.isInstance(ctx) ? pose.invoke(ctx) : ctx;
			if (m.getClass().getName().startsWith("org.joml")) {
				jPop.invoke(m);
			} else {
				pop.invoke(m);
			}
		} catch (Throwable ignored) {
			// pushed nothing
		}
	}

	public static final class Sidebar {
		public boolean has;
		public final Rich title = new Rich();
		public Rich[] names = new Rich[15];
		public Rich[] scores = new Rich[15];
		public int count;
		/** Vanilla content width in GUI px (widest of title / name + ": " + score). */
		public float vanillaW;

		public Sidebar() {
			for (int i = 0; i < names.length; i++) {
				names[i] = new Rich();
				scores[i] = new Rich();
			}
		}
	}

	public static final class Bars {
		public final Rich[] names = new Rich[8];
		public final float[] progress = new float[8];
		public final int[] color = new int[8];
		public final Object[] ids = new Object[8];
		public final float[] nameW = new float[8];
		public int count;

		public Bars() {
			for (int i = 0; i < names.length; i++) {
				names[i] = new Rich();
			}
		}
	}
}
