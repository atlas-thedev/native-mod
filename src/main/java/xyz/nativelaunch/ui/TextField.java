package xyz.nativelaunch.ui;

import xyz.nativelaunch.ui.gfx.Fonts;

/** Single-line text input with caret, selection, clipboard and horizontal scrolling. */
public final class TextField {
	public final String id;
	public final StringBuilder text = new StringBuilder();
	public int caret, anchor;
	private float scrollX;
	private long blinkStart;
	public int maxLength = 2000;
	/** Ctrl+V was pressed this frame; pasteMiss = the text clipboard was empty (maybe a picture is there). */
	public boolean pasteKey, pasteMiss;

	public TextField(String id) {
		this.id = id;
	}

	public String value() {
		return text.toString();
	}

	public void set(String value) {
		text.setLength(0);
		text.append(value == null ? "" : value);
		caret = anchor = text.length();
	}

	public void clear() {
		set("");
	}

	public boolean focused(Ui ui) {
		return id.equals(ui.focus);
	}

	public void focus(Ui ui) {
		ui.focus = id;
		blinkStart = ui.now;
	}

	/**
	 * Draws and edits the field. Returns true when Enter was pressed while focused.
	 */
	public boolean draw(Ui ui, float x, float y, float w, float h, String placeholder, float size) {
		boolean over = ui.hover(x, y, w, h);
		if (over) {
			ui.cursorText = true;
		}
		if (ui.pressed && over) {
			focus(ui);
			ui.focusClaimed = true;
		}
		boolean focused = focused(ui);
		pasteKey = pasteMiss = false;
		boolean submit = false;
		float pad = 12;
		float innerW = w - pad * 2;
		if (focused) {
			submit = edit(ui);
		}
		if (ui.pressed && over) {
			caret = anchor = indexAt(ui, ui.mx - (x + pad) + scrollX, size);
		} else if (ui.down && focused && ui.active == null && over) {
			// drag-select handled loosely: extend selection while held inside
		}

		float fw = ui.anim(id + "#f", focused, 14f);
		ui.c.round(x, y, w, h, 10, Theme.mix(0xCC0B0C10, 0xF00E1014, fw));
		ui.c.outline(x, y, w, h, 10, 1, Theme.mix(over ? Theme.HAIRLINE_STRONG : Theme.HAIRLINE, 0x47FFFFFF, fw));
		float lh = ui.c.lineHeight(Fonts.REGULAR, size);
		float ty = y + (h - lh) / 2;
		String s = text.toString();
		float caretX = ui.c.textWidth(Fonts.REGULAR, size, s.substring(0, caret));
		if (caretX - scrollX > innerW - 2) {
			scrollX = caretX - innerW + 2;
		}
		if (caretX - scrollX < 0) {
			scrollX = caretX;
		}
		float full = ui.c.textWidth(Fonts.REGULAR, size, s);
		if (full - scrollX < innerW && scrollX > 0) {
			scrollX = Math.max(0, full - innerW);
		}
		ui.c.pushClip(x + pad - 1, y, innerW + 2, h);
		if (caret != anchor) {
			int a = Math.min(caret, anchor), b = Math.max(caret, anchor);
			float sx0 = ui.c.textWidth(Fonts.REGULAR, size, s.substring(0, a)) - scrollX;
			float sx1 = ui.c.textWidth(Fonts.REGULAR, size, s.substring(0, b)) - scrollX;
			ui.c.fill(x + pad + sx0, ty + 1, sx1 - sx0, lh - 2, 0x66A1A1AA);
		}
		if (s.isEmpty() && placeholder != null) {
			ui.c.text(Fonts.REGULAR, size, ui.c.ellipsize(Fonts.REGULAR, size, placeholder, innerW), x + pad, ty, Theme.TEXT_MUTED);
		} else {
			ui.c.text(Fonts.REGULAR, size, s, x + pad - scrollX, ty, Theme.TEXT_STRONG);
		}
		if (focused && ((ui.now - blinkStart) / 530) % 2 == 0) {
			ui.c.fill(x + pad + caretX - scrollX, ty + 2, 1.2f, lh - 4, Theme.TEXT_STRONG);
		}
		ui.c.popClip();
		return submit;
	}

	private int indexAt(Ui ui, float localX, float size) {
		String s = text.toString();
		float acc = 0;
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			int n = Character.charCount(cp);
			float a = ui.c.textWidth(Fonts.REGULAR, size, s.substring(i, i + n));
			if (acc + a / 2 > localX) {
				return i;
			}
			acc += a;
			i += n;
		}
		return s.length();
	}

	private boolean edit(Ui ui) {
		boolean submit = false;
		for (int cp : ui.chars) {
			if (cp < 32 || cp == 127) {
				continue;
			}
			insert(new String(Character.toChars(cp)));
		}
		for (int[] k : ui.keys) {
			int key = k[0], mods = k[1];
			boolean ctrl = (mods & (Ui.MOD_CONTROL | Ui.MOD_SUPER)) != 0;
			boolean shift = (mods & Ui.MOD_SHIFT) != 0;
			blinkStart = ui.now;
			if (key == Ui.KEY_ENTER || key == Ui.KEY_KP_ENTER) {
				submit = true;
			} else if (key == Ui.KEY_BACKSPACE) {
				if (caret != anchor) {
					deleteSelection();
				} else if (caret > 0) {
					int from = ctrl ? wordLeft(caret) : text.offsetByCodePoints(caret, -1);
					text.delete(from, caret);
					caret = anchor = from;
				}
			} else if (key == Ui.KEY_DELETE) {
				if (caret != anchor) {
					deleteSelection();
				} else if (caret < text.length()) {
					int to = ctrl ? wordRight(caret) : text.offsetByCodePoints(caret, 1);
					text.delete(caret, to);
				}
			} else if (key == Ui.KEY_LEFT) {
				int to = caret == 0 ? 0 : (ctrl ? wordLeft(caret) : text.offsetByCodePoints(caret, -1));
				if (!shift && caret != anchor) {
					to = Math.min(caret, anchor);
				}
				caret = to;
				if (!shift) {
					anchor = caret;
				}
			} else if (key == Ui.KEY_RIGHT) {
				int to = caret >= text.length() ? text.length() : (ctrl ? wordRight(caret) : text.offsetByCodePoints(caret, 1));
				if (!shift && caret != anchor) {
					to = Math.max(caret, anchor);
				}
				caret = to;
				if (!shift) {
					anchor = caret;
				}
			} else if (key == Ui.KEY_HOME) {
				caret = 0;
				if (!shift) {
					anchor = caret;
				}
			} else if (key == Ui.KEY_END) {
				caret = text.length();
				if (!shift) {
					anchor = caret;
				}
			} else if (ctrl && key == Ui.KEY_A) {
				anchor = 0;
				caret = text.length();
			} else if (ctrl && (key == Ui.KEY_C || key == Ui.KEY_X) && caret != anchor && ui.clipboard != null) {
				ui.clipboard.set(text.substring(Math.min(caret, anchor), Math.max(caret, anchor)));
				if (key == Ui.KEY_X) {
					deleteSelection();
				}
			} else if (ctrl && key == Ui.KEY_V && ui.clipboard != null) {
				String clip = ui.clipboard.get();
				pasteKey = true;
				if (clip == null || clip.isEmpty()) {
					pasteMiss = true;
				}
				if (clip != null) {
					insert(clip.replace('\n', ' ').replace('\r', ' ').replace('\t', ' '));
				}
			}
		}
		return submit;
	}

	private void insert(String s) {
		if (caret != anchor) {
			deleteSelection();
		}
		int room = maxLength - text.length();
		if (room <= 0) {
			return;
		}
		if (s.length() > room) {
			s = s.substring(0, room);
		}
		text.insert(caret, s);
		caret += s.length();
		anchor = caret;
	}

	private void deleteSelection() {
		int a = Math.min(caret, anchor), b = Math.max(caret, anchor);
		text.delete(a, b);
		caret = anchor = a;
	}

	private int wordLeft(int from) {
		int i = from;
		while (i > 0 && text.charAt(i - 1) == ' ') {
			i--;
		}
		while (i > 0 && text.charAt(i - 1) != ' ') {
			i--;
		}
		return i;
	}

	private int wordRight(int from) {
		int i = from, n = text.length();
		while (i < n && text.charAt(i) == ' ') {
			i++;
		}
		while (i < n && text.charAt(i) != ' ') {
			i++;
		}
		return i;
	}
}
