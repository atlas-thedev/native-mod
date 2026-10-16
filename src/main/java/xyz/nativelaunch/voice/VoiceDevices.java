package xyz.nativelaunch.voice;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Microphones and speakers javax.sound can open, refreshed in the background (plugging a headset in shows up). */
public final class VoiceDevices {
	private static volatile List<String> inputs = Collections.emptyList(), outputs = Collections.emptyList();
	private static volatile long scannedAt;
	private static volatile boolean scanning;

	private VoiceDevices() {
	}

	public static List<String> inputs() {
		rescan();
		return inputs;
	}

	public static List<String> outputs() {
		rescan();
		return outputs;
	}

	private static void rescan() {
		if (scanning || System.currentTimeMillis() - scannedAt < 4000) {
			return;
		}
		scanning = true;
		Thread t = new Thread(() -> {
			try {
				List<String> in = new ArrayList<String>(), out = new ArrayList<String>();
				for (Mixer.Info info : AudioSystem.getMixerInfo()) {
					try {
						Mixer m = AudioSystem.getMixer(info);
						if (m.isLineSupported(new Line.Info(TargetDataLine.class)) && !in.contains(info.getName())) {
							in.add(info.getName());
						}
						if (m.isLineSupported(new Line.Info(SourceDataLine.class)) && !out.contains(info.getName())) {
							out.add(info.getName());
						}
					} catch (Throwable ignored) {
						// a broken driver: leave it out
					}
				}
				inputs = Collections.unmodifiableList(in);
				outputs = Collections.unmodifiableList(out);
			} finally {
				scannedAt = System.currentTimeMillis();
				scanning = false;
			}
		}, "Native-Voice-Devices");
		t.setDaemon(true);
		t.start();
	}

	/** Opens a line on the named device, or on the system default when the name is empty / not plugged in. */
	static <T extends DataLine> T open(Class<T> type, AudioFormat fmt, String name) throws Exception {
		DataLine.Info info = new DataLine.Info(type, fmt);
		if (name != null && !name.isEmpty()) {
			for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
				if (mi.getName().equals(name)) {
					Mixer m = AudioSystem.getMixer(mi);
					if (m.isLineSupported(info)) {
						return type.cast(m.getLine(info));
					}
				}
			}
		}
		return type.cast(AudioSystem.getLine(info));
	}
}
