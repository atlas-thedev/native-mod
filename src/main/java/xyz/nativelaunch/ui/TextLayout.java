package xyz.nativelaunch.ui;

import xyz.nativelaunch.ui.gfx.Canvas;

import java.util.ArrayList;
import java.util.List;

/** Word wrapping in logical units. */
public final class TextLayout {
	private TextLayout() {
	}

	public static List<String> wrap(Canvas c, int face, float size, String text, float maxW) {
		List<String> lines = new ArrayList<String>();
		if (text == null) {
			return lines;
		}
		for (String para : text.split("\n", -1)) {
			wrapParagraph(c, face, size, para, maxW, lines);
		}
		return lines;
	}

	private static void wrapParagraph(Canvas c, int face, float size, String text, float maxW, List<String> out) {
		if (text.isEmpty()) {
			out.add("");
			return;
		}
		StringBuilder line = new StringBuilder();
		float lineW = 0;
		float space = c.textWidth(face, size, " ");
		for (String word : text.split(" ", -1)) {
			float ww = c.textWidth(face, size, word);
			if (line.length() > 0 && lineW + space + ww > maxW) {
				out.add(line.toString());
				line.setLength(0);
				lineW = 0;
			}
			if (ww > maxW) {
				// break a long word by characters
				for (int i = 0; i < word.length(); ) {
					int cp = word.codePointAt(i);
					String ch = new String(Character.toChars(cp));
					float cw = c.textWidth(face, size, ch);
					if (lineW + cw > maxW && line.length() > 0) {
						out.add(line.toString());
						line.setLength(0);
						lineW = 0;
					}
					line.append(ch);
					lineW += cw;
					i += Character.charCount(cp);
				}
				continue;
			}
			if (line.length() > 0) {
				line.append(' ');
				lineW += space;
			}
			line.append(word);
			lineW += ww;
		}
		out.add(line.toString());
	}
}
