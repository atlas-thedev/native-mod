package xyz.nativelaunch.ui;

/** Cheap GIF header checks, done before handing a GIF to stb (which decodes every frame at once). */
public final class GifHeader {
	private GifHeader() {
	}

	/** Width x height from a GIF's logical screen descriptor, or -1. */
	public static long area(byte[] b) {
		if (b.length < 10) {
			return -1;
		}
		int w = (b[6] & 0xFF) | (b[7] & 0xFF) << 8, h = (b[8] & 0xFF) | (b[9] & 0xFF) << 8;
		return (long) w * h;
	}

	/** Rough frame count: image descriptors (0x2C) that follow a graphic control extension block. */
	public static int frames(byte[] b) {
		int n = 0;
		for (int i = 0; i + 3 < b.length; i++) {
			if (b[i] == 0x21 && (b[i + 1] & 0xFF) == 0xF9 && b[i + 2] == 0x04) {
				n++;
				i += 7;
			}
		}
		return Math.max(1, n);
	}
}
