package xyz.nativelaunch.music;

import xyz.nativelaunch.music.mp3.Sound;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Decoded audio from one song file: 16-bit samples, interleaved, from any position. */
abstract class PcmSource implements Closeable {
	/** Files bigger than this are not loaded (a 2-hour MP3 at 320 kbps is about 290 MB). */
	static final long MAX_BYTES = 400L * 1024 * 1024;

	abstract int rate();

	abstract int channels();

	/** Fills {@code out} with up to {@code frames} interleaved frames; returns how many, or -1 at the end. */
	abstract int read(short[] out, int frames) throws IOException;

	@Override
	public void close() {
	}

	/** Opens a song at {@code startMs}. */
	static PcmSource open(Path file, long startMs) throws IOException {
		if (Files.size(file) > MAX_BYTES) {
			throw new IOException("This file is too big to play.");
		}
		String ext = TrackInfo.extension(file);
		byte[] data = Files.readAllBytes(file);
		if ("mp3".equals(ext)) {
			return new Mp3(data, startMs);
		}
		if ("ogg".equals(ext) || "oga".equals(ext)) {
			return OggVorbis.open(data, startMs);
		}
		if ("wav".equals(ext)) {
			return new Wav(data, startMs);
		}
		throw new IOException("Unsupported file type: ." + ext);
	}

	// ── MP3 (pure Java decoder) ──────────────────────────────────────────

	static final class Mp3 extends PcmSource {
		/** Frames decoded and thrown away after a seek, so the bit reservoir is full again (the output is then exact). */
		private static final int WARMUP = 4;
		private final Sound sound;
		private final int channels;
		private final byte[] buf = new byte[1152 * 2 * 2 * 4];

		Mp3(byte[] data, long startMs) throws IOException {
			int first = TrackInfo.firstFrame(data, TrackInfo.id3Size(data));
			if (first < 0) {
				throw new IOException("No MP3 audio in this file.");
			}
			Mp3Frames.Header h = Mp3Frames.header(data, first);
			long target = startMs <= 0 ? 0 : startMs * h.rate / 1000 / h.samples;
			long from = Math.max(0, target - WARMUP);
			int at = from == 0 ? first : Mp3Frames.offsetOf(data, first, from);
			if (at < 0) {
				at = first;
				from = 0;
				target = 0;
			}
			InputStream in = new BufferedInputStream(new ByteArrayInputStream(data, at, data.length - at), 64 * 1024);
			sound = new Sound(in);
			channels = sound.isStereo() ? 2 : 1;
			// skip the warm-up frames, and inside the target frame up to the exact millisecond
			long skipFrames = (target - from) * h.samples + Math.max(0, startMs * h.rate / 1000 - target * h.samples);
			long skipBytes = skipFrames * channels * 2;
			while (skipBytes > 0) {
				int r = sound.read(buf, 0, (int) Math.min(buf.length, skipBytes));
				if (r <= 0) {
					break;
				}
				skipBytes -= r;
			}
		}

		@Override
		int rate() {
			return sound.getSamplingFrequency();
		}

		@Override
		int channels() {
			return channels;
		}

		@Override
		int read(short[] out, int frames) throws IOException {
			int want = Math.min(frames * channels * 2, buf.length) & ~(channels * 2 - 1);
			int got = 0;
			while (got < want) {
				int r = sound.read(buf, got, want - got);
				if (r <= 0) {
					break;
				}
				got += r;
			}
			got &= ~(channels * 2 - 1);
			if (got == 0) {
				return -1;
			}
			for (int i = 0, s = 0; i < got; i += 2, s++) {
				out[s] = (short) ((buf[i] & 0xFF) | (buf[i + 1] << 8));
			}
			return got / (channels * 2);
		}

		@Override
		public void close() {
			try {
				sound.close();
			} catch (IOException ignored) {
				// in memory
			}
		}
	}

	// ── WAV (PCM 8 / 16 / 24 / 32 bit, float) ───────────────────────────

	static final class Wav extends PcmSource {
		private final byte[] d;
		private final int rate, channels, bits, block;
		private final boolean floats;
		private int at;
		private final int end;

		Wav(byte[] data, long startMs) throws IOException {
			d = data;
			if (d.length < 12 || !TrackInfo.match(d, 0, "RIFF") || !TrackInfo.match(d, 8, "WAVE")) {
				throw new IOException("Not a WAV file.");
			}
			int r = 0, ch = 0, b = 0, fmt = 0, dataAt = -1, dataLen = 0;
			int p = 12;
			while (p + 8 <= d.length) {
				String id = new String(d, p, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
				int len = TrackInfo.int32le(d, p + 4);
				if (id.equals("fmt ") && p + 24 <= d.length) {
					fmt = (d[p + 8] & 0xFF) | (d[p + 9] & 0xFF) << 8;
					ch = (d[p + 10] & 0xFF) | (d[p + 11] & 0xFF) << 8;
					r = TrackInfo.int32le(d, p + 12);
					b = (d[p + 22] & 0xFF) | (d[p + 23] & 0xFF) << 8;
					if (fmt == 0xFFFE && p + 34 <= d.length) {
						fmt = (d[p + 32] & 0xFF) | (d[p + 33] & 0xFF) << 8; // WAVE_FORMAT_EXTENSIBLE sub-format
					}
				} else if (id.equals("data")) {
					dataAt = p + 8;
					dataLen = (int) Math.min(len & 0xFFFFFFFFL, d.length - dataAt);
					break;
				}
				if (len < 0) {
					break;
				}
				p += 8 + len + (len & 1);
			}
			if (dataAt < 0 || r <= 0 || ch < 1 || ch > 8 || (fmt != 1 && fmt != 3) || (b != 8 && b != 16 && b != 24 && b != 32)) {
				throw new IOException("This WAV format is not supported.");
			}
			rate = r;
			channels = Math.min(2, ch);
			bits = b;
			floats = fmt == 3;
			block = ch * b / 8;
			end = dataAt + dataLen - dataLen % block;
			at = (int) Math.min(end, dataAt + Math.max(0, startMs) * rate / 1000 * block);
		}

		@Override
		int rate() {
			return rate;
		}

		@Override
		int channels() {
			return channels;
		}

		@Override
		int read(short[] out, int frames) {
			if (at >= end) {
				return -1;
			}
			int n = Math.min(frames, (end - at) / block);
			int bps = bits / 8;
			for (int f = 0; f < n; f++) {
				for (int c = 0; c < channels; c++) {
					int o = at + c * bps;
					int v;
					if (floats) {
						float x = Float.intBitsToFloat(TrackInfo.int32le(d, o));
						v = (int) (Math.max(-1f, Math.min(1f, x)) * 32767);
					} else if (bits == 8) {
						v = ((d[o] & 0xFF) - 128) << 8;
					} else if (bits == 16) {
						v = (short) ((d[o] & 0xFF) | (d[o + 1] << 8));
					} else if (bits == 24) {
						v = ((d[o + 1] & 0xFF) | (d[o + 2] << 8));
					} else {
						v = (d[o + 2] & 0xFF) | (d[o + 3] << 8);
					}
					out[f * channels + c] = (short) v;
				}
				at += block;
			}
			return n;
		}
	}
}
