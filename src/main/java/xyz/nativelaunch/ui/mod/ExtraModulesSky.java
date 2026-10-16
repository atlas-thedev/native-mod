package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.gfx.Canvas;

/** Sky colours for a time of day (ticks, 0 = sunrise), used by the Time Changer preview. */
final class ExtraModulesSky {
	private ExtraModulesSky() {
	}

	private static final int[] TOP = {0xFF3B3F7A, 0xFF4F8FE0, 0xFF4F8FE0, 0xFF5A66A8, 0xFF0B1030, 0xFF0B1030, 0xFF3B3F7A};
	private static final int[] BOTTOM = {0xFFF2A765, 0xFFB9DAF5, 0xFFB9DAF5, 0xFFF08A5D, 0xFF1B2347, 0xFF1B2347, 0xFFF2A765};
	private static final long[] AT = {0, 1500, 11000, 12500, 14000, 22000, 24000};

	static int sky(long t, boolean top) {
		int[] cols = top ? TOP : BOTTOM;
		t = Math.floorMod(t, 24000L);
		for (int i = 0; i + 1 < AT.length; i++) {
			if (t <= AT[i + 1]) {
				float f = (t - AT[i]) / (float) (AT[i + 1] - AT[i]);
				return Canvas.lerpColor(cols[i], cols[i + 1], f);
			}
		}
		return cols[cols.length - 1];
	}

	static int ground(long t) {
		return Canvas.lerpColor(0xFF1A2A18, 0xFF5E8F3F, Math.min(1f, Math.max(0f, brightness(t))));
	}

	private static float brightness(long t) {
		t = Math.floorMod(t, 24000L);
		if (t < 1500) {
			return 0.4f + 0.6f * t / 1500f;
		}
		if (t < 11000) {
			return 1f;
		}
		if (t < 14000) {
			return 1f - 0.75f * (t - 11000) / 3000f;
		}
		return t < 22000 ? 0.25f : 0.25f + 0.15f * (t - 22000) / 2000f;
	}
}
