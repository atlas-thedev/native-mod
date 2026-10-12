package xyz.nativelaunch.health;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Entities the player hit recently ("after attack" mode): entity id -> game tick when the indicator expires. */
public final class HitMemory {
	private static final int CAP = 2048;
	private final Map<Integer, Long> until = new HashMap<Integer, Long>();

	public void hit(int id, long nowTick, int durationTicks) {
		if (until.size() >= CAP) {
			prune(nowTick);
			if (until.size() >= CAP) {
				until.clear();
			}
		}
		until.put(id, nowTick + Math.max(1, durationTicks));
	}

	public boolean active(int id, long nowTick) {
		Long t = until.get(id);
		return t != null && t > nowTick;
	}

	public void forget(int id) {
		until.remove(id);
	}

	public void clear() {
		until.clear();
	}

	public int size() {
		return until.size();
	}

	public void prune(long nowTick) {
		Iterator<Map.Entry<Integer, Long>> it = until.entrySet().iterator();
		while (it.hasNext()) {
			if (it.next().getValue() <= nowTick) {
				it.remove();
			}
		}
	}
}
