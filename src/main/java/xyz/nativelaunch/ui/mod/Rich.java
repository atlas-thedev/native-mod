package xyz.nativelaunch.ui.mod;

/** A line of coloured text read from a Minecraft text component (colour runs, legacy section codes resolved). Reused, no per-frame garbage. */
public final class Rich {
	public String[] text = new String[8];
	public int[] color = new int[8];
	public int n;

	public void clear() {
		n = 0;
	}

	public void add(String s, int argb) {
		if (s == null || s.isEmpty()) {
			return;
		}
		if (n > 0 && color[n - 1] == argb) {
			text[n - 1] = text[n - 1] + s;
			return;
		}
		if (n == text.length) {
			String[] t = new String[n * 2];
			int[] c = new int[n * 2];
			System.arraycopy(text, 0, t, 0, n);
			System.arraycopy(color, 0, c, 0, n);
			text = t;
			color = c;
		}
		text[n] = s;
		color[n] = argb;
		n++;
	}

	private static final int[] LEGACY = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF,
			0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

	/** Appends a run that may still carry legacy "section sign" colour codes (old servers put them in team prefixes). */
	public void addLegacy(String s, int base) {
		if (s.indexOf('\u00a7') < 0) {
			add(s, base);
			return;
		}
		int col = base, start = 0;
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == '\u00a7' && i + 1 < s.length()) {
				add(s.substring(start, i), col);
				char k = Character.toLowerCase(s.charAt(i + 1));
				int idx = "0123456789abcdef".indexOf(k);
				if (idx >= 0) {
					col = 0xFF000000 | LEGACY[idx];
				} else if (k == 'r') {
					col = base;
				}
				i++;
				start = i + 1;
			}
		}
		add(s.substring(Math.min(start, s.length())), col);
	}

	public void set(String s, int argb) {
		clear();
		add(s, argb);
	}

	public boolean isEmpty() {
		for (int i = 0; i < n; i++) {
			if (!text[i].trim().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	public void copy(Rich o) {
		clear();
		for (int i = 0; i < o.n; i++) {
			add(o.text[i], o.color[i]);
		}
	}
}
