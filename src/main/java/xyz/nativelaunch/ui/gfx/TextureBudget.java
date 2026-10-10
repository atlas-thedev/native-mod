package xyz.nativelaunch.ui.gfx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;

/** Which uploaded images a renderer may free: unused for a while, or the least recently used ones over the cap. */
public final class TextureBudget {
	/** Images kept on the GPU at most (chat pictures, GIF frames, avatars, artwork). */
	public static final int MAX = 192;
	/** Unused this long (ms) = freed; drawn again later = uploaded again. */
	public static final long IDLE_MS = 20_000;
	/** Never free anything drawn this recently (ms): the game may still be rendering it this frame. */
	public static final long IN_FLIGHT_MS = 1_500;

	private TextureBudget() {
	}

	public static List<Image> stale(Collection<Image> uploaded, long now) {
		return stale(uploaded, now, new ToLongFunction<Image>() {
			@Override
			public long applyAsLong(Image img) {
				return img.lastUsed;
			}
		});
	}

	public static <T> List<T> stale(Collection<T> uploaded, long now, final ToLongFunction<T> lastUsed) {
		List<T> out = new ArrayList<T>();
		List<T> keep = new ArrayList<T>();
		for (T img : uploaded) {
			if (now - lastUsed.applyAsLong(img) > IDLE_MS) {
				out.add(img);
			} else {
				keep.add(img);
			}
		}
		if (keep.size() > MAX) {
			Collections.sort(keep, new Comparator<T>() {
				@Override
				public int compare(T a, T b) {
					return Long.compare(lastUsed.applyAsLong(a), lastUsed.applyAsLong(b));
				}
			});
			for (int i = 0; i < keep.size() - MAX; i++) {
				T img = keep.get(i);
				if (now - lastUsed.applyAsLong(img) > IN_FLIGHT_MS) {
					out.add(img);
				}
			}
		}
		return out;
	}
}
