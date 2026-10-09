package xyz.nativelaunch.ui.gfx;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The faces the UI uses (Poppins like the launcher, Lucide icons) plus system fallbacks for other scripts. */
public final class Fonts {
	public static final int REGULAR = 0, MEDIUM = 1, SEMIBOLD = 2, BOLD = 3, ICONS = 4;
	private static final String[] FILES = {"Poppins-Regular.ttf", "Poppins-Medium.ttf", "Poppins-SemiBold.ttf", "Poppins-Bold.ttf", "lucide.ttf"};
	private final FontFace[] faces = new FontFace[FILES.length];
	private final Map<Long, Font> sized = new HashMap<Long, Font>();
	private List<FontFace> fallbacks;

	public Fonts() {
		for (int i = 0; i < FILES.length; i++) {
			faces[i] = FontFace.fromResource("/assets/native/ui/" + FILES[i]);
		}
		for (int i = 0; i < 4; i++) {
			if (faces[i] == null) {
				faces[i] = faces[0];
			}
		}
		if (faces[0] == null) {
			throw new IllegalStateException("Native UI fonts are missing from the jar");
		}
	}

	/** A face at a size in physical pixels. */
	public Font get(int face, int px) {
		px = Math.max(4, Math.min(px, 200));
		long key = ((long) face << 32) | px;
		Font f = sized.get(key);
		if (f == null) {
			FontFace ff = faces[face] == null ? faces[0] : faces[face];
			f = new Font(this, ff, px);
			sized.put(key, f);
		}
		return f;
	}

	FontFace fallbackFor(int cp) {
		if (fallbacks == null) {
			fallbacks = loadFallbacks();
		}
		for (FontFace f : fallbacks) {
			if (f.glyphIndex(cp) != 0) {
				return f;
			}
		}
		return null;
	}

	private static List<FontFace> loadFallbacks() {
		List<FontFace> out = new ArrayList<FontFace>();
		String windir = System.getenv("WINDIR");
		String win = (windir == null ? "C:\\Windows" : windir) + "\\Fonts\\";
		String[] candidates = {
				win + "segoeui.ttf", win + "Nirmala.ttf", win + "arial.ttf", win + "msyh.ttc", win + "malgun.ttf", win + "seguisym.ttf", win + "seguiemj.ttf",
				"/System/Library/Fonts/Supplemental/Arial Unicode.ttf", "/Library/Fonts/Arial Unicode.ttf", "/System/Library/Fonts/Helvetica.ttc",
				"/System/Library/Fonts/Supplemental/Sinhala Sangam MN.ttc",
				"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/dejavu/DejaVuSans.ttf",
				"/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf", "/usr/share/fonts/noto/NotoSans-Regular.ttf",
				"/usr/share/fonts/truetype/noto/NotoSansSinhala-Regular.ttf", "/usr/share/fonts/liberation/LiberationSans-Regular.ttf"
		};
		for (String c : candidates) {
			try {
				Path p = Paths.get(c);
				FontFace f = FontFace.fromFile(p);
				if (f != null) {
					out.add(f);
				}
			} catch (Throwable ignored) {
				// unreadable or not a font
			}
			if (out.size() >= 4) {
				break;
			}
		}
		return out;
	}
}
