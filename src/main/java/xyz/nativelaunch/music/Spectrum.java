package xyz.nativelaunch.music;

/**
 * The sound bars: what is playing right now, split into log-spaced frequency bands (bass on the left), 0..1 each.
 * Fed from the audio thread with the samples that just went to the speakers; read by the UI.
 */
public final class Spectrum {
	public static final int BANDS = 32;
	private static final int N = 1024;
	private final float[] ring = new float[N];
	private int ringAt;
	private final float[] re = new float[N], im = new float[N], window = new float[N];
	private final float[] bands = new float[BANDS];
	private final int[] edges = new int[BANDS + 1];
	private int rate;

	public Spectrum() {
		for (int i = 0; i < N; i++) {
			window[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (N - 1)));
		}
	}

	private void layout(int sampleRate) {
		rate = sampleRate;
		double lo = 40, hi = Math.min(16000, sampleRate / 2.0);
		for (int b = 0; b <= BANDS; b++) {
			double f = lo * Math.pow(hi / lo, b / (double) BANDS);
			edges[b] = Math.max(1, Math.min(N / 2 - 1, (int) Math.round(f * N / sampleRate)));
		}
		for (int b = 1; b <= BANDS; b++) {
			if (edges[b] <= edges[b - 1]) {
				edges[b] = Math.min(N / 2, edges[b - 1] + 1); // every band gets at least one bin
			}
		}
	}

	/** New audio (interleaved, {@code channels} wide) and how much time it covers. */
	public synchronized void feed(short[] pcm, int frames, int channels, int sampleRate) {
		if (sampleRate != rate) {
			layout(sampleRate);
		}
		for (int f = 0; f < frames; f++) {
			float v = 0;
			for (int c = 0; c < channels; c++) {
				v += pcm[f * channels + c];
			}
			ring[ringAt] = v / (channels * 32768f);
			ringAt = (ringAt + 1) % N;
		}
		for (int i = 0; i < N; i++) {
			re[i] = ring[(ringAt + i) % N] * window[i];
			im[i] = 0;
		}
		fft(re, im);
		float dt = frames / (float) sampleRate;
		for (int b = 0; b < BANDS; b++) {
			float peak = 0;
			for (int k = edges[b]; k < Math.max(edges[b] + 1, edges[b + 1]); k++) {
				peak = Math.max(peak, re[k] * re[k] + im[k] * im[k]);
			}
			// dB, 0 at a loud tone, a gentle lift for the highs (they carry less energy)
			double db = 10 * Math.log10(peak / (N * N / 16.0) + 1e-12) + b * 0.45;
			float target = (float) Math.max(0, Math.min(1, (db + 62) / 58));
			float cur = bands[b];
			// quick to rise, slow to fall: bars that dance instead of flicker
			bands[b] = target > cur ? cur + (target - cur) * Math.min(1, dt * 28) : Math.max(target, cur - dt * 1.6f);
		}
	}

	/** The bars decay to nothing when the music stops. */
	public synchronized void decay(float dt) {
		for (int b = 0; b < BANDS; b++) {
			bands[b] = Math.max(0, bands[b] - dt * 1.6f);
		}
	}

	/** A copy of the bands into {@code out} (any length: bands are resampled). */
	public synchronized void read(float[] out) {
		for (int i = 0; i < out.length; i++) {
			float pos = out.length == 1 ? 0 : i * (BANDS - 1f) / (out.length - 1);
			int a = (int) pos;
			int b = Math.min(BANDS - 1, a + 1);
			float t = pos - a;
			out[i] = bands[a] * (1 - t) + bands[b] * t;
		}
	}

	/** In-place radix-2 FFT. */
	static void fft(float[] re, float[] im) {
		int n = re.length;
		for (int i = 1, j = 0; i < n; i++) {
			int bit = n >> 1;
			for (; (j & bit) != 0; bit >>= 1) {
				j ^= bit;
			}
			j ^= bit;
			if (i < j) {
				float t = re[i];
				re[i] = re[j];
				re[j] = t;
				t = im[i];
				im[i] = im[j];
				im[j] = t;
			}
		}
		for (int len = 2; len <= n; len <<= 1) {
			double ang = -2 * Math.PI / len;
			float wr = (float) Math.cos(ang), wi = (float) Math.sin(ang);
			for (int i = 0; i < n; i += len) {
				float cr = 1, ci = 0;
				for (int k = 0; k < len / 2; k++) {
					int a = i + k, b = i + k + len / 2;
					float xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
					re[b] = re[a] - xr;
					im[b] = im[a] - xi;
					re[a] += xr;
					im[a] += xi;
					float nr = cr * wr - ci * wi;
					ci = cr * wi + ci * wr;
					cr = nr;
				}
			}
		}
	}
}
