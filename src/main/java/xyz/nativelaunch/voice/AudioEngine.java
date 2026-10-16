package xyz.nativelaunch.voice;

import xyz.nativelaunch.core.Log;
import xyz.nativelaunch.voice.opus.OpusApplication;
import xyz.nativelaunch.voice.opus.OpusEncoder;
import xyz.nativelaunch.voice.opus.OpusSignal;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

/**
 * Microphone and speakers through javax.sound (works on every Minecraft version, no natives): 48 kHz, 20 ms frames.
 * The capture thread gates (push-to-talk / voice activation), encodes and sends; the mix thread pulls every speaker's
 * jitter buffer, applies distance and stereo panning, and writes one stereo line.
 */
final class AudioEngine {
	private static final int RATE = 48000, FRAME = RemoteStream.FRAME, HANGOVER = 15;

	private volatile boolean running = true;
	private Thread capture, mix;
	volatile String micError, speakerError;

	void start() {
		capture = thread(this::captureLoop, "Native-Voice-Mic");
		mix = thread(this::mixLoop, "Native-Voice-Out");
	}

	private static Thread thread(Runnable r, String name) {
		Thread t = new Thread(r, name);
		t.setDaemon(true);
		t.setPriority(Thread.MAX_PRIORITY - 1);
		t.start();
		return t;
	}

	void stop() {
		running = false;
	}

	// ── microphone ────────────────────────────────────────────────────────

	private void captureLoop() {
		AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
		byte[] raw = new byte[FRAME * 2];
		short[] pcm = new short[FRAME];
		byte[] packet = new byte[1 + 1275];
		OpusEncoder encoder;
		try {
			encoder = new OpusEncoder(RATE, 1, OpusApplication.OPUS_APPLICATION_VOIP);
			encoder.setBitrate(32000);
			encoder.setComplexity(5);
			encoder.setSignalType(OpusSignal.OPUS_SIGNAL_VOICE);
			encoder.setUseInbandFEC(true);
			encoder.setPacketLossPercent(10);
		} catch (Throwable t) {
			micError = "Opus encoder failed: " + t;
			return;
		}
		TargetDataLine line = null;
		int hang = 0;
		boolean open = false;
		while (running) {
			try {
				if (!Voice.wantsMic()) {
					if (line != null) {
						line.stop();
						line.close();
						line = null;
					}
					open = false;
					Voice.selfLevel = 0;
					Voice.selfSpeaking = false;
					Thread.sleep(100);
					continue;
				}
				if (line == null) {
					DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
					line = (TargetDataLine) AudioSystem.getLine(info);
					line.open(fmt, FRAME * 2 * 4);
					line.start();
					micError = null;
				}
				int got = 0;
				while (got < raw.length && running) {
					int r = line.read(raw, got, raw.length - got);
					if (r <= 0) {
						break;
					}
					got += r;
				}
				float gain = Voice.micGain;
				long sum = 0;
				for (int i = 0; i < FRAME; i++) {
					int v = (int) (((raw[2 * i + 1] << 8) | (raw[2 * i] & 0xFF)) * gain);
					v = v > 32767 ? 32767 : v < -32768 ? -32768 : v;
					pcm[i] = (short) v;
					sum += (long) v * v;
				}
				double rms = Math.sqrt(sum / (double) FRAME) / 32768.0;
				Voice.selfLevel = (float) rms;
				double db = rms <= 1e-9 ? -100 : 20 * Math.log10(rms);
				boolean want;
				if (Voice.muted || Voice.deafened) {
					want = false;
				} else if (Voice.pushToTalk) {
					want = Voice.pttHeld;
				} else if (db >= Voice.thresholdDb) {
					hang = HANGOVER;
					want = true;
				} else {
					want = hang-- > 0;
				}
				Voice.selfSpeaking = want;
				if (want || open) {
					int n = encoder.encode(pcm, 0, FRAME, packet, 1, packet.length - 1);
					packet[0] = (byte) (want ? 0 : RemoteStream.END);
					Voice.sendAudio(packet, n + 1);
				}
				open = want;
			} catch (Throwable t) {
				micError = t.getMessage() == null ? t.toString() : t.getMessage();
				Log.debug("Voice microphone: {}", micError);
				if (line != null) {
					try {
						line.close();
					} catch (Throwable ignored) {
						// already gone
					}
					line = null;
				}
				sleep(2000);
			}
		}
		if (line != null) {
			line.close();
		}
	}

	// ── speakers ──────────────────────────────────────────────────────────

	private void mixLoop() {
		AudioFormat fmt = new AudioFormat(RATE, 16, 2, true, false);
		int[] acc = new int[FRAME * 2];
		byte[] out = new byte[FRAME * 4];
		double[] ear = new double[5];
		SourceDataLine line = null;
		while (running) {
			try {
				if (!Voice.active()) {
					if (line != null) {
						line.drain();
						line.close();
						line = null;
					}
					Thread.sleep(100);
					continue;
				}
				if (line == null) {
					line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, fmt));
					line.open(fmt, FRAME * 4 * 4);
					line.start();
					speakerError = null;
				}
				java.util.Arrays.fill(acc, 0);
				boolean haveEar = Voice.ear(ear);
				float volume = Voice.outVolume;
				float range = Voice.range;
				for (RemoteStream s : Voice.streams()) {
					short[] pcm = s.pull();
					if (pcm == null || Voice.deafened || Voice.duplicate(s)) {
						continue;
					}
					float l = volume, r = volume;
					if (s.proximity) {
						if (!haveEar || !s.placed) {
							continue; // not near enough to be loaded in the world: out of earshot
						}
						double dx = s.x - ear[0], dy = s.y - ear[1], dz = s.z - ear[2];
						double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
						if (d >= range) {
							continue;
						}
						float fall = (float) (1 - d / range);
						fall *= fall;
						double yaw = Math.toRadians(ear[3]);
						double pan = d < 0.5 ? 0 : (dx * -Math.cos(yaw) + dz * -Math.sin(yaw)) / Math.max(1e-6, Math.sqrt(dx * dx + dz * dz));
						pan *= Math.min(1, d / 4); // close by sounds centred
						double a = (pan + 1) * Math.PI / 4;
						l = (float) (volume * fall * Math.cos(a) * 1.4142);
						r = (float) (volume * fall * Math.sin(a) * 1.4142);
					}
					for (int i = 0; i < FRAME; i++) {
						acc[2 * i] += (int) (pcm[i] * l);
						acc[2 * i + 1] += (int) (pcm[i] * r);
					}
				}
				for (int i = 0; i < acc.length; i++) {
					int v = acc[i];
					v = v > 32767 ? 32767 : v < -32768 ? -32768 : v;
					out[2 * i] = (byte) v;
					out[2 * i + 1] = (byte) (v >> 8);
				}
				line.write(out, 0, out.length); // blocks: paces the loop at real time
			} catch (Throwable t) {
				speakerError = t.getMessage() == null ? t.toString() : t.getMessage();
				Log.debug("Voice output: {}", speakerError);
				if (line != null) {
					try {
						line.close();
					} catch (Throwable ignored) {
						// already gone
					}
					line = null;
				}
				sleep(2000);
			}
		}
		if (line != null) {
			line.close();
		}
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}
}
