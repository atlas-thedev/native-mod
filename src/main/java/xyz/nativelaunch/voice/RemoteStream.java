package xyz.nativelaunch.voice;

import xyz.nativelaunch.voice.opus.OpusDecoder;

import java.util.TreeMap;

/** One speaker: a small jitter buffer (40 ms) in front of an Opus decoder, with loss concealment and FEC. */
final class RemoteStream {
	static final int FRAME = 960, END = 1;
	private static final int START_FRAMES = 2, MAX_FRAMES = 12, MAX_MISSES = 5;
	private static final long START_WAIT_MS = 60;

	final int sid;
	final boolean proximity;
	volatile String userId;
	/** World position of the speaker (proximity), updated by the game thread; valid=false when not in sight. */
	volatile double x, y, z;
	volatile boolean placed;
	volatile float level;
	volatile long lastPacket;

	private final TreeMap<Integer, byte[]> buf = new TreeMap<Integer, byte[]>();
	private final OpusDecoder decoder;
	private final short[] pcm = new short[FRAME];
	private int next, misses, end = Integer.MIN_VALUE;
	private boolean playing;
	private long firstAt;

	RemoteStream(int sid, boolean proximity) throws Exception {
		this.sid = sid;
		this.proximity = proximity;
		decoder = new OpusDecoder(48000, 1);
	}

	synchronized void push(int seq, int flags, byte[] opus) {
		if (playing && seq < next) {
			return; // too late
		}
		if (buf.isEmpty() && !playing) {
			firstAt = System.currentTimeMillis();
		}
		buf.put(seq, opus);
		if ((flags & END) != 0) {
			end = seq;
		}
		lastPacket = System.currentTimeMillis();
		while (buf.size() > MAX_FRAMES) {
			buf.pollFirstEntry(); // fell behind: catch up instead of piling up delay
			next = buf.firstKey();
		}
	}

	/** The next 20 ms of audio, or null while silent. */
	synchronized short[] pull() {
		if (!playing) {
			if (buf.isEmpty() || buf.size() < START_FRAMES && System.currentTimeMillis() - firstAt < START_WAIT_MS && end == Integer.MIN_VALUE) {
				return null;
			}
			playing = true;
			misses = 0;
			next = buf.firstKey();
		}
		try {
			byte[] p = buf.remove(next);
			if (p != null) {
				decoder.decode(p, 0, p.length, pcm, 0, FRAME, false);
				misses = 0;
			} else if (++misses > MAX_MISSES) {
				stop();
				return null;
			} else {
				byte[] after = buf.get(next + 1);
				if (after != null) {
					decoder.decode(after, 0, after.length, pcm, 0, FRAME, true);
				} else {
					decoder.decode(null, 0, 0, pcm, 0, FRAME, false);
				}
			}
		} catch (Throwable bad) {
			java.util.Arrays.fill(pcm, (short) 0);
		}
		if (next >= end && end != Integer.MIN_VALUE) {
			stop();
		}
		next++;
		long sum = 0;
		for (short s : pcm) {
			sum += s * s;
		}
		level = (float) Math.sqrt(sum / (double) FRAME) / 32768f;
		return pcm;
	}

	private void stop() {
		playing = false;
		firstAt = System.currentTimeMillis(); // ran dry: build the cushion up again before resuming
		end = Integer.MIN_VALUE;
		level = 0;
		if (!buf.isEmpty()) {
			buf.headMap(next + 1).clear();
		}
	}

	boolean speaking() {
		return System.currentTimeMillis() - lastPacket < 250;
	}
}
