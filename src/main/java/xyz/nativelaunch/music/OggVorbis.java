package xyz.nativelaunch.music;

import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * Ogg Vorbis through LWJGL's stb_vorbis, which every Minecraft version Native supports ships. Kept in its own class so
 * a game without it only loses .ogg playback.
 */
final class OggVorbis extends PcmSource {
	private final ByteBuffer file;
	private final long handle;
	private final int rate, channels;
	private ShortBuffer pcm;

	private OggVorbis(ByteBuffer file, long handle, int rate, int channels) {
		this.file = file;
		this.handle = handle;
		this.rate = rate;
		this.channels = channels;
	}

	static PcmSource open(byte[] data, long startMs) throws IOException {
		ByteBuffer file;
		try {
			file = MemoryUtil.memAlloc(data.length);
		} catch (Throwable t) {
			throw new IOException("OGG files can't be played in this game version.");
		}
		file.put(data).flip();
		long handle;
		int rate, channels;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer error = stack.mallocInt(1);
			handle = STBVorbis.stb_vorbis_open_memory(file, error, null);
			if (handle == 0) {
				MemoryUtil.memFree(file);
				throw new IOException("This OGG file can't be read (error " + error.get(0) + ").");
			}
			STBVorbisInfo info = STBVorbisInfo.malloc(); // heap: the same call on every LWJGL 3 version the game ships
			try {
				STBVorbis.stb_vorbis_get_info(handle, info);
				rate = info.sample_rate();
				channels = Math.min(2, info.channels());
			} finally {
				info.free();
			}
		}
		OggVorbis o = new OggVorbis(file, handle, rate, channels);
		if (startMs > 0) {
			STBVorbis.stb_vorbis_seek(handle, (int) Math.min(Integer.MAX_VALUE, startMs * rate / 1000));
		}
		return o;
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
		int want = Math.min(frames, out.length / channels);
		if (pcm == null || pcm.capacity() < want * channels) {
			if (pcm != null) {
				MemoryUtil.memFree(pcm);
			}
			pcm = MemoryUtil.memAllocShort(want * channels);
		}
		pcm.clear();
		pcm.limit(want * channels);
		int n = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, pcm);
		if (n <= 0) {
			return -1;
		}
		pcm.get(out, 0, n * channels);
		return n;
	}

	@Override
	public void close() {
		STBVorbis.stb_vorbis_close(handle);
		MemoryUtil.memFree(file);
		if (pcm != null) {
			MemoryUtil.memFree(pcm);
			pcm = null;
		}
	}
}
