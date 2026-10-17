package xyz.nativelaunch.voice;

import org.junit.jupiter.api.Test;
import xyz.nativelaunch.voice.opus.OpusApplication;
import xyz.nativelaunch.voice.opus.OpusEncoder;

import static org.junit.jupiter.api.Assertions.*;

class RemoteStreamTest {
	private static byte[][] sine(int frames) throws Exception {
		OpusEncoder enc = new OpusEncoder(48000, 1, OpusApplication.OPUS_APPLICATION_VOIP);
		enc.setUseInbandFEC(true);
		enc.setPacketLossPercent(10);
		short[] pcm = new short[RemoteStream.FRAME];
		byte[] buf = new byte[1275];
		byte[][] out = new byte[frames][];
		for (int f = 0; f < frames; f++) {
			for (int i = 0; i < pcm.length; i++) {
				pcm[i] = (short) (8000 * Math.sin(2 * Math.PI * 440 * (f * pcm.length + i) / 48000.0));
			}
			int n = enc.encode(pcm, 0, pcm.length, buf, 0, buf.length);
			out[f] = java.util.Arrays.copyOf(buf, n);
		}
		return out;
	}

	@Test
	void lostPacketIsConcealedAndEndStopsPlayback() throws Exception {
		byte[][] p = sine(20);
		RemoteStream s = new RemoteStream(7);
		int played = 0;
		for (int f = 0; f < 20; f++) {
			if (f != 10) {
				s.push(100 + f, f == 19 ? RemoteStream.END : 0, p[f]);
			}
			if (f >= 1 && s.pull() != null) {
				played++;
			}
		}
		while (s.pull() != null) {
			played++;
		}
		assertEquals(20, played, "every frame plays, the lost one concealed");
		assertNull(s.pull(), "silent after the end-of-speech frame");
	}

	@Test
	void fallingBehindDropsOldAudio() throws Exception {
		byte[][] p = sine(30);
		RemoteStream s = new RemoteStream(8);
		for (int f = 0; f < 30; f++) {
			s.push(f, 0, p[f]);
		}
		int played = 0;
		while (s.pull() != null && played < 100) {
			played++;
		}
		assertTrue(played <= 12 + 6, "at most the capped buffer (plus concealment) plays: " + played);
	}
}
