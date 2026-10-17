package xyz.nativelaunch.music;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.nativelaunch.ui.gfx.Image;

class MusicTest {
	@TempDir
	Path tmp;

	private static byte[] kikuo() throws IOException {
		try (InputStream in = MusicTest.class.getResourceAsStream("/music/stereo_kikuo.mp3")) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] b = new byte[8192];
			int n;
			while ((n = in.read(b)) > 0) {
				out.write(b, 0, n);
			}
			return out.toByteArray();
		}
	}

	// ── the shared folder ─────────────────────────────────────────────────

	@Test
	void theLauncherSharesOneMusicFolderNextToItsInstances() throws IOException {
		Path root = tmp.resolve("Native/minecraft");
		Files.createDirectories(root.resolve("versions"));
		Path inst = Files.createDirectories(root.resolve("instances/abc-123"));
		assertEquals(root.resolve("music"), MusicPlayer.resolveFolder(inst, new HashMap<String, String>(), "Windows 11", tmp.toString()));
	}

	@Test
	void otherLaunchersUseTheNativeFolderToo() throws IOException {
		Map<String, String> env = new HashMap<String, String>();
		env.put("APPDATA", tmp.toString());
		Path other = Files.createDirectories(tmp.resolve("PrismLauncher/instances/pvp/.minecraft"));
		assertEquals(tmp.resolve("Native/minecraft/music"), MusicPlayer.resolveFolder(other, env, "Windows 10", "C:/Users/x"));
		Files.createDirectories(tmp.resolve("Native Client/minecraft"));
		assertEquals(tmp.resolve("Native Client/minecraft/music"), MusicPlayer.resolveFolder(other, env, "Windows 10", "C:/Users/x"));
		Map<String, String> linux = new HashMap<String, String>();
		linux.put("XDG_CONFIG_HOME", tmp.resolve("cfg").toString());
		assertEquals(tmp.resolve("cfg/Native/minecraft/music"), MusicPlayer.resolveFolder(other, linux, "Linux", tmp.toString()));
	}

	// ── tags ──────────────────────────────────────────────────────────────

	private static byte[] frame23(String id, byte[] data) {
		byte[] f = new byte[10 + data.length];
		System.arraycopy(id.getBytes(StandardCharsets.ISO_8859_1), 0, f, 0, 4);
		f[4] = (byte) (data.length >>> 24);
		f[5] = (byte) (data.length >>> 16);
		f[6] = (byte) (data.length >>> 8);
		f[7] = (byte) data.length;
		System.arraycopy(data, 0, f, 10, data.length);
		return f;
	}

	private static byte[] text(int enc, String s) {
		byte[] b = s.getBytes(enc == 3 ? StandardCharsets.UTF_8 : enc == 1 ? StandardCharsets.UTF_16 : StandardCharsets.ISO_8859_1);
		byte[] out = new byte[b.length + 1];
		out[0] = (byte) enc;
		System.arraycopy(b, 0, out, 1, b.length);
		return out;
	}

	private static byte[] tag(int major, byte[]... frames) {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		for (byte[] f : frames) {
			body.write(f, 0, f.length);
		}
		byte[] pad = new byte[64];
		body.write(pad, 0, pad.length);
		int size = body.size();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write('I');
		out.write('D');
		out.write('3');
		out.write(major);
		out.write(0);
		out.write(0);
		out.write((size >> 21) & 0x7F);
		out.write((size >> 14) & 0x7F);
		out.write((size >> 7) & 0x7F);
		out.write(size & 0x7F);
		byte[] b = body.toByteArray();
		out.write(b, 0, b.length);
		return out.toByteArray();
	}

	private static byte[] concat(byte[]... parts) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (byte[] p : parts) {
			out.write(p, 0, p.length);
		}
		return out.toByteArray();
	}

	@Test
	void mp3TagsTitleArtistAlbumCoverAndLength() throws IOException {
		byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};
		ByteArrayOutputStream apic = new ByteArrayOutputStream();
		apic.write(0);
		apic.write("image/png\0".getBytes(StandardCharsets.ISO_8859_1));
		apic.write(3); // front cover
		apic.write("cover\0".getBytes(StandardCharsets.ISO_8859_1));
		apic.write(png);
		byte[] id3 = tag(3, frame23("TIT2", text(1, "Sweden")), frame23("TPE1", text(0, "C418")), frame23("TALB", text(0, "Volume Alpha")),
				frame23("APIC", apic.toByteArray()));
		Path f = tmp.resolve("whatever.mp3");
		Files.write(f, concat(id3, kikuo()));
		TrackInfo t = TrackInfo.read(f);
		assertEquals("Sweden", t.title);
		assertEquals("C418", t.artist);
		assertEquals("Volume Alpha", t.album);
		assertArrayEquals(png, t.cover);
		// 723456 samples at 48 kHz = 15.072 s; the length comes from the bitrate (a CBR file)
		assertTrue(Math.abs(t.durationMs - 15072) < 400, "length " + t.durationMs);
	}

	@Test
	void id3v24Utf8AndTheFileNameAsFallback() throws IOException {
		byte[] f24 = frame23("TIT2", text(3, "Ünïcødé ☆"));
		// v2.4 sizes are syncsafe: small sizes look the same
		Path f = tmp.resolve("Lena Raine - Pigstep.mp3");
		Files.write(f, concat(tag(4, f24), kikuo()));
		TrackInfo t = TrackInfo.read(f);
		assertEquals("Ünïcødé ☆", t.title);
		assertEquals("Lena Raine", t.artist, "no artist tag: from the file name");
		Path g = tmp.resolve("Just a title.mp3");
		Files.write(g, kikuo());
		TrackInfo u = TrackInfo.read(g);
		assertEquals("Just a title", u.title);
		assertNull(u.artist);
	}

	@Test
	void id3v1AtTheEnd() throws IOException {
		byte[] v1 = new byte[128];
		v1[0] = 'T';
		v1[1] = 'A';
		v1[2] = 'G';
		System.arraycopy("Wet Hands".getBytes(StandardCharsets.ISO_8859_1), 0, v1, 3, 9);
		System.arraycopy("C418".getBytes(StandardCharsets.ISO_8859_1), 0, v1, 33, 4);
		Path f = tmp.resolve("x.mp3");
		Files.write(f, concat(kikuo(), v1));
		TrackInfo t = TrackInfo.read(f);
		assertEquals("Wet Hands", t.title);
		assertEquals("C418", t.artist);
	}

	private static byte[] le32(int v) {
		return new byte[] {(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
	}

	private static byte[] oggPage(long granule, byte[] packet) {
		ByteArrayOutputStream lacing = new ByteArrayOutputStream();
		int left = packet.length;
		while (left >= 255) {
			lacing.write(255);
			left -= 255;
		}
		lacing.write(left);
		byte[] lace = lacing.toByteArray();
		ByteArrayOutputStream p = new ByteArrayOutputStream();
		p.write('O');
		p.write('g');
		p.write('g');
		p.write('S');
		p.write(0);
		p.write(0);
		byte[] g = concat(le32((int) granule), le32((int) (granule >>> 32)));
		p.write(g, 0, 8);
		byte[] rest = new byte[12];
		p.write(rest, 0, 12);
		p.write(lace.length);
		p.write(lace, 0, lace.length);
		p.write(packet, 0, packet.length);
		return p.toByteArray();
	}

	@Test
	void oggVorbisComments() throws IOException {
		byte[] id = concat(new byte[] {1}, "vorbis".getBytes(StandardCharsets.ISO_8859_1), le32(0), new byte[] {2}, le32(44100), new byte[16]);
		String[] tags = {"TITLE=Aria Math", "ARTIST=C418", "ALBUM=Volume Beta"};
		ByteArrayOutputStream cm = new ByteArrayOutputStream();
		cm.write(3);
		cm.write("vorbis".getBytes(StandardCharsets.ISO_8859_1));
		byte[] vendor = "test".getBytes(StandardCharsets.UTF_8);
		cm.write(le32(vendor.length));
		cm.write(vendor);
		cm.write(le32(tags.length));
		for (String s : tags) {
			byte[] b = s.getBytes(StandardCharsets.UTF_8);
			cm.write(le32(b.length));
			cm.write(b);
		}
		cm.write(1);
		Path f = tmp.resolve("a.ogg");
		Files.write(f, concat(oggPage(0, id), oggPage(0, cm.toByteArray()), oggPage(44100L * 185, new byte[40])));
		TrackInfo t = TrackInfo.read(f);
		assertEquals("Aria Math", t.title);
		assertEquals("C418", t.artist);
		assertEquals("Volume Beta", t.album);
		assertEquals(185_000, t.durationMs);
	}

	private static byte[] wav(int rate, int seconds, String title) {
		int frames = rate * seconds;
		byte[] data = new byte[frames * 4];
		for (int i = 0; i < frames; i++) {
			short v = (short) (Math.sin(2 * Math.PI * 440 * i / rate) * 12000);
			data[i * 4] = data[i * 4 + 2] = (byte) v;
			data[i * 4 + 1] = data[i * 4 + 3] = (byte) (v >> 8);
		}
		byte[] name = (title + "\0").getBytes(StandardCharsets.ISO_8859_1);
		byte[] info = concat("INFO".getBytes(StandardCharsets.ISO_8859_1), "INAM".getBytes(StandardCharsets.ISO_8859_1), le32(name.length), name,
				name.length % 2 == 1 ? new byte[1] : new byte[0]);
		byte[] fmt = concat(new byte[] {1, 0, 2, 0}, le32(rate), le32(rate * 4), new byte[] {4, 0, 16, 0});
		byte[] body = concat("WAVE".getBytes(StandardCharsets.ISO_8859_1), "fmt ".getBytes(StandardCharsets.ISO_8859_1), le32(16), fmt,
				"LIST".getBytes(StandardCharsets.ISO_8859_1), le32(info.length), info, "data".getBytes(StandardCharsets.ISO_8859_1), le32(data.length), data);
		return concat("RIFF".getBytes(StandardCharsets.ISO_8859_1), le32(body.length), body);
	}

	@Test
	void wavTagsLengthAndPlayback() throws IOException {
		Path f = tmp.resolve("tone.wav");
		Files.write(f, wav(44100, 2, "A440"));
		TrackInfo t = TrackInfo.read(f);
		assertEquals("A440", t.title);
		assertEquals(2000, t.durationMs);
		PcmSource s = PcmSource.open(f, 1000);
		assertEquals(44100, s.rate());
		assertEquals(2, s.channels());
		short[] buf = new short[4096];
		int total = 0, n;
		while ((n = s.read(buf, 1024)) > 0) {
			total += n;
		}
		assertEquals(44100, total, "started at 1 s: one second left");
	}

	// ── decoding ──────────────────────────────────────────────────────────

	private static short[] decodeAll(PcmSource s) throws IOException {
		List<short[]> parts = new ArrayList<short[]>();
		int total = 0, n;
		short[] buf = new short[2048 * 2];
		while ((n = s.read(buf, 2048)) > 0) {
			parts.add(Arrays.copyOf(buf, n * s.channels()));
			total += n * s.channels();
		}
		short[] all = new short[total];
		int at = 0;
		for (short[] p : parts) {
			System.arraycopy(p, 0, all, at, p.length);
			at += p.length;
		}
		return all;
	}

	@Test
	void mp3DecodesWholeAndSeeksExactly() throws IOException {
		Path f = tmp.resolve("k.mp3");
		Files.write(f, concat(tag(3, frame23("TIT2", text(0, "x"))), kikuo()));
		PcmSource whole = PcmSource.open(f, 0);
		assertEquals(48000, whole.rate());
		assertEquals(2, whole.channels());
		short[] all = decodeAll(whole);
		assertEquals(723456 * 2, all.length, "every sample of the reference decode");
		// 7.2 s = frame 300: after a seek the output is exactly what a decode from the start gives there
		PcmSource from = PcmSource.open(f, 7200);
		short[] tail = decodeAll(from);
		int at = 300 * 1152 * 2;
		assertEquals(all.length - at, tail.length);
		assertArrayEquals(Arrays.copyOfRange(all, at, at + 48000), Arrays.copyOfRange(tail, 0, 48000));
		// and between frames, to the millisecond
		short[] mid = decodeAll(PcmSource.open(f, 7210));
		assertArrayEquals(Arrays.copyOfRange(all, at + 480 * 2, at + 480 * 2 + 20000), Arrays.copyOfRange(mid, 0, 20000));
	}

	// ── sound bars ────────────────────────────────────────────────────────

	@Test
	void theBarsFollowTheMusic() {
		Spectrum sp = new Spectrum();
		short[] tone = new short[1024 * 2];
		for (int k = 0; k < 40; k++) {
			for (int i = 0; i < 1024; i++) {
				short v = (short) (Math.sin(2 * Math.PI * 1000 * (k * 1024 + i) / 48000.0) * 20000);
				tone[i * 2] = tone[i * 2 + 1] = v;
			}
			sp.feed(tone, 1024, 2, 48000);
		}
		float[] bands = new float[Spectrum.BANDS];
		sp.read(bands);
		int loud = 0;
		for (int b = 1; b < bands.length; b++) {
			if (bands[b] > bands[loud]) {
				loud = b;
			}
		}
		// 1 kHz on a 40 Hz - 16 kHz log scale sits a bit past the middle
		assertTrue(loud >= 13 && loud <= 19, "loudest band " + loud);
		assertTrue(bands[loud] > 0.6f && bands[0] < 0.3f);
		for (int i = 0; i < 40; i++) {
			sp.decay(0.05f);
		}
		sp.read(bands);
		assertEquals(0f, bands[loud], 0.0001f, "silence: the bars sink");
	}

	// ── play order ────────────────────────────────────────────────────────

	@Test
	void nextAndPreviousWithRepeat() {
		List<MusicPlayer.Track> list = new ArrayList<MusicPlayer.Track>();
		for (int i = 0; i < 3; i++) {
			list.add(new MusicPlayer.Track(tmp.resolve(i + ".mp3"), i + ".mp3", null, 1, 1));
		}
		MusicPlayer.Track a = list.get(0), c = list.get(2);
		assertSame(list.get(1), MusicPlayer.step(list, a, 1, MusicPlayer.Repeat.OFF));
		assertNull(MusicPlayer.step(list, c, 1, MusicPlayer.Repeat.OFF), "repeat off: the playlist ends");
		assertSame(a, MusicPlayer.step(list, c, 1, MusicPlayer.Repeat.ALL));
		assertSame(c, MusicPlayer.step(list, a, -1, MusicPlayer.Repeat.ALL));
		assertSame(a, MusicPlayer.step(list, null, 1, MusicPlayer.Repeat.OFF), "nothing playing: the first song");
		assertNull(MusicPlayer.step(Collections.<MusicPlayer.Track>emptyList(), null, 1, MusicPlayer.Repeat.ALL));
	}

	// ── covers ────────────────────────────────────────────────────────────

	@Test
	void coversAreScaledRoundedAndGiveAColour() {
		int[] px = new int[512 * 512];
		Arrays.fill(px, 0xFF2050E0);
		Image big = new Image(512, 512, px);
		Image small = MusicPlayer.scaled(big, 256);
		assertEquals(256, small.width);
		assertEquals(0xFF2050E0, small.argb[128 * 256 + 128]);
		Image round = MusicPlayer.rounded(small, 0.1f);
		assertEquals(0, round.argb[0] >>> 24, "the corner is cut away");
		assertEquals(0xFF2050E0, round.argb[128 * 256 + 128], "the middle is untouched");
		int acc = MusicPlayer.accent(small);
		assertTrue((acc & 0xFF) > ((acc >> 16) & 0xFF), "a blue cover gives a blue glow");
		float[] hsv = MusicPlayer.hsv(0.2f, 0.4f, 0.9f);
		int back = MusicPlayer.rgb(hsv[0], hsv[1], hsv[2]);
		assertEquals(0xFF3366E6, back, "colour maths round-trips");
		assertNotNull(MusicPlayer.time(65_000));
		assertEquals("1:05", MusicPlayer.time(65_000));
		assertEquals("1:02:03", MusicPlayer.time(3_723_000));
	}
}
