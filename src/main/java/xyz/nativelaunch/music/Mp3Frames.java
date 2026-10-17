package xyz.nativelaunch.music;

/** MPEG audio Layer III frame headers: enough to find frames, measure them and seek without decoding. */
final class Mp3Frames {
	private static final int[] BR_V1 = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0};
	private static final int[] BR_V2 = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0};
	private static final int[][] RATES = {{11025, 12000, 8000}, {0, 0, 0}, {22050, 24000, 16000}, {44100, 48000, 32000}}; // by version bits

	private Mp3Frames() {
	}

	static final class Header {
		boolean mpeg1, mono;
		int rate, bitrate, length, samples;
	}

	/** The Layer III frame header at {@code at}, or null when there is none. */
	static Header header(byte[] d, int at) {
		if (at < 0 || at + 4 > d.length || (d[at] & 0xFF) != 0xFF || (d[at + 1] & 0xE0) != 0xE0) {
			return null;
		}
		int b1 = d[at + 1] & 0xFF, b2 = d[at + 2] & 0xFF, b3 = d[at + 3] & 0xFF;
		int version = (b1 >> 3) & 3, layer = (b1 >> 1) & 3;
		int bri = b2 >> 4, sri = (b2 >> 2) & 3, pad = (b2 >> 1) & 1;
		if (version == 1 || layer != 1 || bri == 0 || bri == 15 || sri == 3) {
			return null;
		}
		Header h = new Header();
		h.mpeg1 = version == 3;
		h.rate = RATES[version][sri];
		h.bitrate = (h.mpeg1 ? BR_V1 : BR_V2)[bri] * 1000;
		h.samples = h.mpeg1 ? 1152 : 576;
		h.length = (h.mpeg1 ? 144 : 72) * h.bitrate / h.rate + pad;
		h.mono = (b3 >> 6) == 3;
		return h.length > 4 ? h : null;
	}

	/** Byte offset of frame number {@code n} counted from {@code first}, or -1 past the end. */
	static int offsetOf(byte[] d, int first, long n) {
		int at = first;
		long k = 0;
		while (at >= 0 && at + 4 <= d.length) {
			Header h = header(d, at);
			if (h == null) {
				at++; // junk between frames: resync
				continue;
			}
			if (k == n) {
				return at;
			}
			at += h.length;
			k++;
		}
		return -1;
	}
}
