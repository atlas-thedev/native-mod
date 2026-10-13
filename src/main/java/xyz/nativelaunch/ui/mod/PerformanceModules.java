package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.McBridge;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.UiRuntime;

import java.util.concurrent.locks.LockSupport;

/** Modules that make the game lighter to run. */
final class PerformanceModules {
	private PerformanceModules() {
	}

	/**
	 * Lag Free: caps the frame rate only while nobody is looking (background, minimised, AFK) and can trim particles and
	 * entity distance. The wait is cut into short slices that re-check the window, so full speed is back on the very frame
	 * you click the game or touch a key (the old version slept up to a whole second and polled focus every 200 ms).
	 */
	static final class LagFree extends Module {
		private static final long SLICE_NS = 3_000_000L, SPIN_NS = 1_000_000L;
		final Setting.Num unfocused = add(new Setting.Num("unfocused", "FPS in the background", 30, 5, 120, 5, ""));
		final Setting.Num minimized = add(new Setting.Num("minimized", "FPS when minimised", 5, 1, 30, 1, ""));
		final Setting.Bool afk = add(new Setting.Bool("afk", "Lower FPS while AFK", true));
		final Setting.Num afkAfter = add(new Setting.Num("afkAfter", "AFK after", 90, 15, 600, 15, "s"));
		final Setting.Num afkFps = add(new Setting.Num("afkFps", "FPS while AFK", 20, 5, 60, 5, ""));
		final Setting.Choice particles = add(new Setting.Choice("particles", "Particles", 0, "Game setting", "Decreased", "Minimal"));
		final Setting.Num entities = add(new Setting.Num("entities", "Entity distance", 100, 50, 100, 5, "%"));
		private long lastFrame, lastActive = System.nanoTime(), applyAt;
		private float lastYaw = Float.NaN, lastPitch = Float.NaN;
		private double savedParticles = Double.NaN, savedEntities = Double.NaN;
		private int appliedParticles, appliedEntities = 100;

		LagFree() {
			super("bgfps", "Lag Free", "Smooth FPS while you play, low FPS in the background, minimised or AFK.", PERFORMANCE,
					Theme.I_ZAP, true);
		}

		@Override
		protected boolean hidden(Setting s) {
			return (s == afkAfter || s == afkFps) && !afk.value;
		}

		@Override
		public void frame(Game g) {
			McBridge mc = UiRuntime.mc();
			if (mc == null) {
				return;
			}
			long now = System.nanoTime();
			if (now - applyAt > 500_000_000L) {
				applyAt = now;
				applyOptions(mc);
			}
			if (g.yaw != lastYaw || g.pitch != lastPitch || Keys.lastInput() > lastActive) {
				lastYaw = g.yaw;
				lastPitch = g.pitch;
				lastActive = Math.max(now, Keys.lastInput());
			}
			int cap = cap(mc, now);
			if (cap > 0 && lastFrame != 0) {
				waitUntil(mc, lastFrame + 1_000_000_000L / cap, cap);
			}
			lastFrame = System.nanoTime();
		}

		private int cap(McBridge mc, long now) {
			if (mc.windowMinimized()) {
				return Math.round(minimized.value);
			}
			if (!mc.windowFocused()) {
				return Math.round(unfocused.value);
			}
			if (afk.value && now - lastActive > (long) (afkAfter.value * 1e9)) {
				return Math.round(afkFps.value);
			}
			return 0;
		}

		/** Waits in short slices; leaves early the moment the player is back (focus, restore, any input). */
		private void waitUntil(McBridge mc, long deadline, int cap) {
			long input = Keys.lastInput();
			while (true) {
				long left = deadline - System.nanoTime();
				if (left <= 0) {
					return;
				}
				if (left > SPIN_NS) {
					LockSupport.parkNanos(Math.min(SLICE_NS, left - SPIN_NS / 2));
					if (Keys.lastInput() != input || cap(mc, System.nanoTime()) != cap) {
						return;
					}
				} else {
					Thread.yield();
				}
			}
		}

		private void applyOptions(McBridge mc) {
			int p = particles.value;
			if (p != appliedParticles) {
				if (appliedParticles == 0) {
					savedParticles = mc.videoOption("particles");
				}
				if (p == 0) {
					restoreParticles(mc);
				} else if (!Double.isNaN(savedParticles)) {
					mc.setVideoOption("particles", Math.max(p, savedParticles));
				}
				appliedParticles = p;
			}
			int e = Math.round(entities.value);
			if (e != appliedEntities) {
				if (appliedEntities >= 100) {
					savedEntities = mc.videoOption("entityDistance");
				}
				if (e >= 100) {
					restoreEntities(mc);
				} else if (!Double.isNaN(savedEntities)) {
					mc.setVideoOption("entityDistance", savedEntities * e / 100.0);
				}
				appliedEntities = e;
			}
		}

		private void restoreParticles(McBridge mc) {
			if (!Double.isNaN(savedParticles)) {
				mc.setVideoOption("particles", savedParticles);
			}
			savedParticles = Double.NaN;
		}

		private void restoreEntities(McBridge mc) {
			if (!Double.isNaN(savedEntities)) {
				mc.setVideoOption("entityDistance", savedEntities);
			}
			savedEntities = Double.NaN;
		}

		@Override
		protected void onDisable() {
			McBridge mc = UiRuntime.mc();
			if (mc != null) {
				restoreParticles(mc);
				restoreEntities(mc);
			}
			appliedParticles = 0;
			appliedEntities = 100;
			lastFrame = 0;
		}
	}
}
