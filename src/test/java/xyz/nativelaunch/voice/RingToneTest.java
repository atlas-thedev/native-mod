package xyz.nativelaunch.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RingToneTest {
	@Test
	void twoBeepsThenSilenceInAThreeSecondCycle() {
		assertEquals(0.0, Voice.beepEnvelope(0), 1e-9, "starts from silence (no click)");
		assertTrue(Voice.beepEnvelope(9600) > 0.99, "first beep at full level");
		assertEquals(0.0, Voice.beepEnvelope(24000), 1e-9, "gap between the beeps");
		assertTrue(Voice.beepEnvelope(28800 + 9600) > 0.99, "second beep");
		assertEquals(0.0, Voice.beepEnvelope(48000 * 2), 1e-9, "quiet for the rest of the cycle");
		assertTrue(Voice.beepEnvelope(19199) < 0.01, "fades out before it ends");
	}

	@Test
	void theSpeakerTestIsThreeAudibleNotesThatFadeOut() {
		byte[] pcm = Voice.chime(48000, 0.4f);
		assertEquals(48000 * 28 / 100 * 3 * 2, pcm.length, "three notes of 0.28 s, 16-bit mono");
		int peak = 0;
		for (int i = 0; i < pcm.length; i += 2) {
			peak = Math.max(peak, Math.abs((short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8))));
		}
		assertTrue(peak > 8000 && peak < 32767, "loud enough to hear, never clipping: " + peak);
		assertEquals(0, pcm[0] | pcm[1], "starts from silence (no click)");
	}
}
