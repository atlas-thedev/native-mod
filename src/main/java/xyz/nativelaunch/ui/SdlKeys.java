package xyz.nativelaunch.ui;

/**
 * Minecraft 26.3 replaced GLFW with SDL and with it the numbering of every key: the game now reports (and stores
 * its key bindings as) SDL scancodes, where A is 4 instead of GLFW's 65.
 *
 * The Native UI speaks GLFW everywhere - saved settings, the keybind picker, {@link xyz.nativelaunch.ui.mod.Keys}
 * - so the version bridge translates at the boundary: SDL codes coming in from 26.3 become GLFW codes, and GLFW
 * codes going back to the game become SDL ones. On GLFW versions both directions are the identity.
 */
public final class SdlKeys {
	/** True when this Minecraft runs on SDL (26.3+): org.lwjgl.glfw.GLFW no longer exists. */
	private static final boolean SDL = detect();

	private static final int MAX_SCANCODE = 232;
	private static final int[] TO_GLFW = new int[MAX_SCANCODE];
	private static final java.util.Map<Integer, Integer> TO_SDL = new java.util.HashMap<Integer, Integer>();

	static {
		java.util.Arrays.fill(TO_GLFW, -1);
		for (int i = 0; i < 26; i++) {
			map(4 + i, 'A' + i); // A - Z
		}
		for (int i = 0; i < 9; i++) {
			map(30 + i, '1' + i); // 1 - 9
		}
		map(39, '0');
		map(40, 257); // enter
		map(41, 256); // escape
		map(42, 259); // backspace
		map(43, 258); // tab
		map(44, 32); // space
		map(45, 45); // -
		map(46, 61); // =
		map(47, 91); // [
		map(48, 93); // ]
		map(49, 92); // backslash
		map(50, 92); // non-US #
		map(51, 59); // ;
		map(52, 39); // '
		map(53, 96); // `
		map(54, 44); // ,
		map(55, 46); // .
		map(56, 47); // /
		map(57, 280); // caps lock
		for (int i = 0; i < 12; i++) {
			map(58 + i, 290 + i); // F1 - F12
		}
		map(70, 283); // print screen
		map(71, 281); // scroll lock
		map(72, 284); // pause
		map(73, 260); // insert
		map(74, 268); // home
		map(75, 266); // page up
		map(76, 261); // delete
		map(77, 269); // end
		map(78, 267); // page down
		map(79, 262); // right
		map(80, 263); // left
		map(81, 264); // down
		map(82, 265); // up
		map(83, 282); // num lock
		map(84, 331); // keypad /
		map(85, 332); // keypad *
		map(86, 333); // keypad -
		map(87, 334); // keypad +
		map(88, 335); // keypad enter
		for (int i = 0; i < 9; i++) {
			map(89 + i, 321 + i); // keypad 1 - 9
		}
		map(98, 320); // keypad 0
		map(99, 330); // keypad .
		map(100, 92); // non-US backslash
		map(101, 348); // menu
		map(103, 336); // keypad =
		for (int i = 0; i < 12; i++) {
			map(104 + i, 302 + i); // F13 - F24
		}
		map(224, 341); // left control
		map(225, 340); // left shift
		map(226, 342); // left alt
		map(227, 343); // left super
		map(228, 345); // right control
		map(229, 344); // right shift
		map(230, 346); // right alt
		map(231, 347); // right super
	}

	private SdlKeys() {
	}

	private static void map(int scancode, int glfw) {
		TO_GLFW[scancode] = glfw;
		if (!TO_SDL.containsKey(Integer.valueOf(glfw))) {
			TO_SDL.put(Integer.valueOf(glfw), Integer.valueOf(scancode));
		}
	}

	private static boolean detect() {
		try {
			Class.forName("org.lwjgl.glfw.GLFW");
			return false;
		} catch (Throwable t) {
			return true;
		}
	}

	/** True on Minecraft 26.3+, where the game speaks SDL. */
	public static boolean active() {
		return SDL;
	}

	/** A key code coming from the game -> the GLFW code the Native UI uses. */
	public static int toGlfw(int code) {
		if (!SDL || code < 0 || code >= MAX_SCANCODE) {
			return code;
		}
		int glfw = TO_GLFW[code];
		return glfw < 0 ? code : glfw;
	}

	/** A GLFW code from the Native UI -> the code this game understands. */
	public static int toGame(int glfw) {
		if (!SDL) {
			return glfw;
		}
		Integer sdl = TO_SDL.get(Integer.valueOf(glfw));
		return sdl == null ? glfw : sdl.intValue();
	}

	/** Mouse buttons: SDL counts from 1 (left 1, middle 2, right 3), GLFW from 0 (left 0, right 1, middle 2). */
	public static int mouseToGlfw(int button) {
		if (!SDL) {
			return button;
		}
		switch (button) {
			case 1: return 0;
			case 2: return 2;
			case 3: return 1;
			default: return button <= 0 ? button : button - 1;
		}
	}
}
