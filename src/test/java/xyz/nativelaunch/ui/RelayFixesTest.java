package xyz.nativelaunch.ui;

import org.junit.jupiter.api.Test;
import xyz.nativelaunch.ui.gfx.TextureBudget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayFixesTest {
	private static final ToLongFunction<long[]> AT = new ToLongFunction<long[]>() {
		@Override
		public long applyAsLong(long[] v) {
			return v[0];
		}
	};

	@Test
	void idleTexturesAreFreed() {
		long now = 100_000;
		long[] fresh = {now - 100}, idle = {now - TextureBudget.IDLE_MS - 1};
		List<long[]> all = new ArrayList<long[]>();
		all.add(fresh);
		all.add(idle);
		List<long[]> stale = TextureBudget.stale(all, now, AT);
		assertTrue(stale.contains(idle));
		assertFalse(stale.contains(fresh));
	}

	@Test
	void overTheCapOldestGoFirstButNeverInFlight() {
		long now = 100_000;
		List<long[]> all = new ArrayList<long[]>();
		for (int i = 0; i < TextureBudget.MAX + 10; i++) {
			all.add(new long[] {now - 10_000 + i});
		}
		long[] drawing = {now};
		all.add(drawing);
		List<long[]> stale = TextureBudget.stale(all, now, AT);
		assertEquals(11, stale.size());
		assertFalse(stale.contains(drawing));
		assertTrue(stale.contains(all.get(0)));
	}

	@Test
	void gifHeaderGuards() {
		byte[] gif = new byte[] {'G', 'I', 'F', '8', '9', 'a', (byte) 0x2C, 0x01, (byte) 0xC8, 0x00, 0, 0, 0,
				0x21, (byte) 0xF9, 0x04, 0, 0, 0, 0, 0, 0x2C, 0x21, (byte) 0xF9, 0x04, 0, 0, 0, 0, 0, 0x2C};
		assertEquals(300L * 200L, GifHeader.area(gif));
		assertEquals(2, GifHeader.frames(gif));
		assertEquals(-1, GifHeader.area(new byte[] {'G', 'I', 'F'}));
	}
}
