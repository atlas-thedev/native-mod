package xyz.nativelaunch.music;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;

/**
 * What a song file says about itself, read without decoding the audio: title, artist, album, length and the cover
 * picture. MP3 (ID3v2.2 - 2.4, ID3v1, Xing / VBRI / CBR length), Ogg Vorbis (Vorbis comments, METADATA_BLOCK_PICTURE,
 * last granule) and WAV (RIFF INFO). Anything missing falls back to the file name ("Artist - Title.mp3").
 */
public final class TrackInfo {
	public String title, artist, album;
	/** Length in ms, or -1 when unknown. */
	public long durationMs = -1;
	/** Embedded cover (JPEG / PNG bytes), or null. */
	public byte[] cover;

	private static final int HEAD = 4 * 1024 * 1024; // covers can be big; nothing reads past this
	private static final Charset LATIN1 = StandardCharsets.ISO_8859_1;

	public static String extension(Path p) {
		String n = p.getFileName().toString();
		int dot = n.lastIndexOf('.');
		return dot < 0 ? "" : n.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	public static TrackInfo read(Path file) {
		TrackInfo t = new TrackInfo();
		try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
			long size = f.length();
			byte[] head = new byte[(int) Math.min(size, HEAD)];
			f.readFully(head);
			byte[] tail = new byte[(int) Math.min(size, 65536)];
			f.seek(size - tail.length);
			f.readFully(tail);
			String ext = extension(file);
			if ("mp3".equals(ext)) {
				mp3(t, head, tail, size);
			} else if ("ogg".equals(ext) || "oga".equals(ext)) {
				ogg(t, head, tail);
			} else if ("wav".equals(ext)) {
				wav(t, head, size);
			}
		} catch (IOException | RuntimeException ignored) {
			// unreadable tags: the file name still says something
		}
		fromName(t, file.getFileName().toString());
		return t;
	}

	/** "Artist - Title.mp3" -> artist + title, for whatever the tags left empty. */
	static void fromName(TrackInfo t, String fileName) {
		String base = fileName.replaceFirst("\\.[^.]+$", "").replace('_', ' ').trim();
		int dash = base.indexOf(" - ");
		String nameArtist = dash > 0 ? base.substring(0, dash).trim() : null;
		String nameTitle = dash > 0 ? base.substring(dash + 3).trim() : base;
		if (blank(t.title)) {
			t.title = nameTitle.isEmpty() ? base : nameTitle;
		}
		if (blank(t.artist)) {
			t.artist = nameArtist;
		}
	}

	private static boolean blank(String s) {
		return s == null || s.trim().isEmpty();
	}

	// ── MP3 ──────────────────────────────────────────────────────────────

	/** Bytes of the ID3v2 tag at the start (0 when there is none). */
	public static int id3Size(byte[] d) {
		if (d.length < 10 || d[0] != 'I' || d[1] != 'D' || d[2] != '3') {
			return 0;
		}
		int size = syncsafe(d, 6);
		return 10 + size + ((d[5] & 0x10) != 0 ? 10 : 0);
	}

	static void mp3(TrackInfo t, byte[] head, byte[] tail, long fileSize) {
		int tag = id3Size(head);
		if (tag > 0) {
			id3v2(t, head, Math.min(tag, head.length));
		}
		if (tail.length >= 128 && tail[tail.length - 128] == 'T' && tail[tail.length - 127] == 'A' && tail[tail.length - 126] == 'G') {
			int o = tail.length - 128;
			if (blank(t.title)) {
				t.title = latin(tail, o + 3, 30);
			}
			if (blank(t.artist)) {
				t.artist = latin(tail, o + 33, 30);
			}
			if (blank(t.album)) {
				t.album = latin(tail, o + 63, 30);
			}
			fileSize -= 128;
		}
		if (t.durationMs < 0) {
			t.durationMs = mp3Duration(head, tag, fileSize);
		}
	}

	static void id3v2(TrackInfo t, byte[] d, int end) {
		int major = d[3];
		int flags = d[5] & 0xFF;
		byte[] body = java.util.Arrays.copyOfRange(d, 10, end);
		if (major < 4 && (flags & 0x80) != 0) {
			body = unsync(body); // v2.2 / v2.3: the whole tag was unsynchronised
		}
		int at = 0;
		if ((flags & 0x40) != 0 && major >= 3 && body.length >= 4) {
			at = major == 4 ? syncsafe(body, 0) : 4 + int32(body, 0);
		}
		int coverType = -1;
		int idLen = major == 2 ? 3 : 4, hdr = major == 2 ? 6 : 10;
		while (at + hdr <= body.length) {
			String id = new String(body, at, idLen, LATIN1);
			if (id.charAt(0) == 0) {
				break; // padding
			}
			int size = major == 2 ? ((body[at + 3] & 0xFF) << 16) | ((body[at + 4] & 0xFF) << 8) | (body[at + 5] & 0xFF)
					: major == 4 ? syncsafe(body, at + 4) : int32(body, at + 4);
			int fflags = major == 2 ? 0 : ((body[at + 8] & 0xFF) << 8) | (body[at + 9] & 0xFF);
			int start = at + hdr;
			if (size <= 0 || start + size > body.length) {
				break;
			}
			byte[] data = java.util.Arrays.copyOfRange(body, start, start + size);
			if (major == 4) {
				if ((fflags & 0x0001) != 0 && data.length >= 4) {
					data = java.util.Arrays.copyOfRange(data, 4, data.length); // data length indicator
				}
				if ((fflags & 0x0002) != 0) {
					data = unsync(data);
				}
			}
			if ((fflags & (major == 4 ? 0x000C : 0x00C0)) == 0) { // not compressed / encrypted
				if (id.equals("TIT2") || id.equals("TT2")) {
					t.title = text(data);
				} else if (id.equals("TPE1") || id.equals("TP1")) {
					t.artist = text(data);
				} else if (id.equals("TALB") || id.equals("TAL")) {
					t.album = text(data);
				} else if (id.equals("TLEN") || id.equals("TLE")) {
					try {
						long ms = Long.parseLong(text(data).trim());
						if (ms > 0) {
							t.durationMs = ms;
						}
					} catch (NumberFormatException ignored) {
						// not a number
					}
				} else if (id.equals("APIC") || id.equals("PIC")) {
					int[] type = new int[1];
					byte[] img = picture(data, id.equals("PIC"), type);
					// the front cover (type 3) wins over any other picture
					if (img != null && (t.cover == null || type[0] == 3 && coverType != 3)) {
						t.cover = img;
						coverType = type[0];
					}
				}
			}
			at = start + size;
		}
	}

	private static byte[] picture(byte[] d, boolean v22, int[] type) {
		if (d.length < 4) {
			return null;
		}
		int enc = d[0];
		int at = 1;
		if (v22) {
			at += 3; // image format, e.g. "JPG"
		} else {
			while (at < d.length && d[at] != 0) {
				at++; // MIME type
			}
			at++;
		}
		if (at >= d.length) {
			return null;
		}
		type[0] = d[at++] & 0xFF;
		at = skipString(d, at, enc); // description
		return at < d.length ? java.util.Arrays.copyOfRange(d, at, d.length) : null;
	}

	private static int skipString(byte[] d, int at, int enc) {
		if (enc == 1 || enc == 2) {
			while (at + 1 < d.length && !(d[at] == 0 && d[at + 1] == 0)) {
				at += 2;
			}
			return at + 2;
		}
		while (at < d.length && d[at] != 0) {
			at++;
		}
		return at + 1;
	}

	static String text(byte[] d) {
		if (d.length == 0) {
			return null;
		}
		int enc = d[0];
		Charset cs = enc == 1 ? StandardCharsets.UTF_16 : enc == 2 ? StandardCharsets.UTF_16BE : enc == 3 ? StandardCharsets.UTF_8 : LATIN1;
		String s = new String(d, 1, d.length - 1, cs);
		int nul = s.indexOf('\u0000');
		if (nul >= 0) {
			s = s.substring(0, nul); // v2.4 lists several values: the first one
		}
		s = s.trim();
		return s.isEmpty() ? null : s;
	}

	/** Length from the first frame: Xing / Info or VBRI frame count, else the bitrate (CBR). */
	static long mp3Duration(byte[] d, int from, long fileSize) {
		int at = firstFrame(d, from);
		if (at < 0) {
			return -1;
		}
		Mp3Frames.Header h = Mp3Frames.header(d, at);
		if (h == null) {
			return -1;
		}
		int side = h.mpeg1 ? (h.mono ? 17 : 32) : (h.mono ? 9 : 17);
		int x = at + 4 + side;
		if (x + 12 <= d.length && (match(d, x, "Xing") || match(d, x, "Info")) && (int32(d, x + 4) & 1) != 0) {
			long frames = int32(d, x + 8) & 0xFFFFFFFFL;
			return frames * h.samples * 1000L / h.rate;
		}
		int v = at + 4 + 32;
		if (v + 18 <= d.length && match(d, v, "VBRI")) {
			long frames = int32(d, v + 14) & 0xFFFFFFFFL;
			return frames * h.samples * 1000L / h.rate;
		}
		return h.bitrate <= 0 ? -1 : (fileSize - at) * 8L / h.bitrate;
	}

	static int firstFrame(byte[] d, int from) {
		for (int at = Math.max(0, from); at + 4 <= d.length; at++) {
			Mp3Frames.Header h = Mp3Frames.header(d, at);
			// a real frame is followed by another one (or the end): skips false syncs inside leftover tag data
			if (h != null && (at + h.length + 4 > d.length || Mp3Frames.header(d, at + h.length) != null)) {
				return at;
			}
		}
		return -1;
	}

	// ── Ogg Vorbis ───────────────────────────────────────────────────────

	static void ogg(TrackInfo t, byte[] head, byte[] tail) {
		// packets, rebuilt from the pages at the start of the file
		java.io.ByteArrayOutputStream packets = new java.io.ByteArrayOutputStream();
		int rate = 0;
		int at = 0, page = 0;
		java.util.List<Integer> ends = new java.util.ArrayList<Integer>(); // where each packet ends in `packets`
		while (at + 27 <= head.length && match(head, at, "OggS") && page < 400) {
			int segs = head[at + 26] & 0xFF;
			int data = at + 27 + segs;
			for (int i = 0; i < segs && data <= head.length; i++) {
				int len = head[at + 27 + i] & 0xFF;
				if (data + len > head.length) {
					break;
				}
				packets.write(head, data, len);
				data += len;
				if (len < 255) {
					ends.add(packets.size());
				}
			}
			at = data;
			page++;
			if (ends.size() >= 2) {
				break; // identification + comment packets
			}
		}
		byte[] p = packets.toByteArray();
		if (ends.size() >= 1 && p.length >= 16 && p[0] == 1 && match(p, 1, "vorbis")) {
			rate = int32le(p, 12);
		}
		if (ends.size() >= 2) {
			int c = ends.get(0);
			if (p[c] == 3 && match(p, c + 1, "vorbis")) {
				comments(t, p, c + 7, ends.get(1));
			}
		}
		if (rate > 0) {
			for (int i = tail.length - 27; i >= 0; i--) {
				if (match(tail, i, "OggS")) {
					long granule = int32le(tail, i + 6) & 0xFFFFFFFFL | (long) int32le(tail, i + 10) << 32;
					if (granule > 0) {
						t.durationMs = granule * 1000L / rate;
					}
					break;
				}
			}
		}
	}

	static void comments(TrackInfo t, byte[] p, int at, int end) {
		int vendor = int32le(p, at);
		at += 4 + vendor;
		int n = int32le(p, at);
		at += 4;
		for (int i = 0; i < n && at + 4 <= end; i++) {
			int len = int32le(p, at);
			at += 4;
			if (len < 0 || at + len > end) {
				break;
			}
			String kv = new String(p, at, len, StandardCharsets.UTF_8);
			at += len;
			int eq = kv.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String key = kv.substring(0, eq).toUpperCase(Locale.ROOT), value = kv.substring(eq + 1).trim();
			if (key.equals("TITLE") && blank(t.title)) {
				t.title = value;
			} else if (key.equals("ARTIST") && blank(t.artist)) {
				t.artist = value;
			} else if (key.equals("ALBUM") && blank(t.album)) {
				t.album = value;
			} else if (key.equals("METADATA_BLOCK_PICTURE") && t.cover == null) {
				t.cover = flacPicture(value);
			}
		}
	}

	/** A base64 FLAC picture block (how Vorbis comments carry cover art). */
	static byte[] flacPicture(String b64) {
		try {
			byte[] b = Base64.getDecoder().decode(b64.replaceAll("\\s", ""));
			int at = 4;
			at += 4 + int32(b, at); // MIME
			at += 4 + int32(b, at); // description
			at += 16; // width, height, depth, colours
			int len = int32(b, at);
			at += 4;
			return len > 0 && at + len <= b.length ? java.util.Arrays.copyOfRange(b, at, at + len) : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	// ── WAV ──────────────────────────────────────────────────────────────

	static void wav(TrackInfo t, byte[] d, long fileSize) {
		if (d.length < 12 || !match(d, 0, "RIFF") || !match(d, 8, "WAVE")) {
			return;
		}
		int byteRate = 0;
		int at = 12;
		while (at + 8 <= d.length) {
			String id = new String(d, at, 4, LATIN1);
			int len = int32le(d, at + 4);
			int body = at + 8;
			if (len < 0) {
				break;
			}
			if (id.equals("fmt ") && body + 12 <= d.length) {
				byteRate = int32le(d, body + 8);
			} else if (id.equals("data")) {
				long bytes = Math.min(len & 0xFFFFFFFFL, fileSize - body);
				if (byteRate > 0) {
					t.durationMs = bytes * 1000L / byteRate;
				}
				break;
			} else if (id.equals("LIST") && body + 4 <= d.length && match(d, body, "INFO")) {
				int i = body + 4;
				while (i + 8 <= Math.min(d.length, body + len)) {
					String sub = new String(d, i, 4, LATIN1);
					int sl = int32le(d, i + 4);
					if (sl < 0 || i + 8 + sl > d.length) {
						break;
					}
					String v = latin(d, i + 8, sl);
					if (sub.equals("INAM")) {
						t.title = v;
					} else if (sub.equals("IART")) {
						t.artist = v;
					} else if (sub.equals("IPRD")) {
						t.album = v;
					}
					i += 8 + sl + (sl & 1);
				}
			}
			at = body + len + (len & 1);
		}
	}

	// ── bytes ────────────────────────────────────────────────────────────

	static int syncsafe(byte[] d, int at) {
		return (d[at] & 0x7F) << 21 | (d[at + 1] & 0x7F) << 14 | (d[at + 2] & 0x7F) << 7 | (d[at + 3] & 0x7F);
	}

	static int int32(byte[] d, int at) {
		return (d[at] & 0xFF) << 24 | (d[at + 1] & 0xFF) << 16 | (d[at + 2] & 0xFF) << 8 | (d[at + 3] & 0xFF);
	}

	static int int32le(byte[] d, int at) {
		return (d[at] & 0xFF) | (d[at + 1] & 0xFF) << 8 | (d[at + 2] & 0xFF) << 16 | (d[at + 3] & 0xFF) << 24;
	}

	static boolean match(byte[] d, int at, String s) {
		if (at < 0 || at + s.length() > d.length) {
			return false;
		}
		for (int i = 0; i < s.length(); i++) {
			if (d[at + i] != s.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	private static String latin(byte[] d, int at, int len) {
		String s = new String(d, at, Math.min(len, d.length - at), LATIN1);
		int nul = s.indexOf('\u0000');
		s = (nul >= 0 ? s.substring(0, nul) : s).trim();
		return s.isEmpty() ? null : s;
	}

	/** Undoes ID3 unsynchronisation (FF 00 -> FF). */
	static byte[] unsync(byte[] d) {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(d.length);
		for (int i = 0; i < d.length; i++) {
			out.write(d[i]);
			if ((d[i] & 0xFF) == 0xFF && i + 1 < d.length && d[i + 1] == 0) {
				i++;
			}
		}
		return out.toByteArray();
	}
}
