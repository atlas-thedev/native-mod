package xyz.nativelaunch.ui;

/** Colours and sizes taken from the Native launcher (dark, monochrome, soft hairlines). */
public final class Theme {
	private Theme() {
	}

	public static final int PAGE = 0xFF000000;
	public static final int SURFACE = 0xFF050608;
	public static final int PANEL = 0xFF08090C;
	public static final int PANEL_HOVER = 0xFF0E1014;
	public static final int PANEL_PRESSED = 0xFF14171D;
	public static final int HAIRLINE = 0x14FFFFFF;      // 8 %
	public static final int HAIRLINE_STRONG = 0x29FFFFFF; // 16 %
	public static final int BORDER_HOVER = 0x47FFFFFF;
	public static final int SUBTLE = 0x0FFFFFFF;        // 6 %
	public static final int SUBTLE_HOVER = 0x1FFFFFFF;  // 12 %
	public static final int TEXT = 0xFFD2D2D2;
	public static final int TEXT_STRONG = 0xFFFFFFFF;
	public static final int TEXT_SECONDARY = 0xFFA1A1AA;
	public static final int TEXT_MUTED = 0xFF75717A;
	public static final int ACCENT = 0xFFD9A6DA;
	public static final int ONLINE = 0xFF22C55E;
	public static final int IDLE = 0xFFF59E0B;
	public static final int DANGER = 0xFFEF4444;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int SOLID_FG = 0xFF09090B;

	// Lucide icon codepoints
	public static final int I_MESSAGE = 0xE11A, I_USERS = 0xE1A4, I_USER_PLUS = 0xE1A2, I_SEND = 0xE156, I_SETTINGS = 0xE158,
			I_X = 0xE1B2, I_SEARCH = 0xE155, I_HASH = 0xE0F2, I_CHEVRON_LEFT = 0xE072, I_CHEVRON_RIGHT = 0xE073, I_GLOBE = 0xE0EB,
			I_GAMEPAD = 0xE0E2, I_LOG_OUT = 0xE112, I_PLAY = 0xE140, I_SERVER = 0xE157, I_USER = 0xE19F, I_BELL = 0xE05D,
			I_CHECK_CIRCLE = 0xE226, I_ARROW_LEFT = 0xE04C, I_PLUS = 0xE141, I_POWER = 0xE144, I_LAYERS = 0xE104,
			I_SPARKLES = 0xE417, I_SMILE = 0xE168, I_WIFI_OFF = 0xE1AF, I_PUZZLE = 0xE29C, I_MONITOR = 0xE121, I_CLOUD = 0xE08C,
			I_HOME = 0xE0F8, I_REFRESH = 0xE149, I_REPLY = 0xE22A, I_KEYBOARD = 0xE284, I_EYE = 0xE0BE, I_LOCK = 0xE10F,
			I_LOG_IN = 0xE111, I_LOADER = 0xE10E, I_MSG_MORE = 0xE56A, I_ELLIPSIS = 0xE0BA, I_CROWN = 0xE1D6;

	public static int alpha(int argb, float a) {
		return xyz.nativelaunch.ui.gfx.Canvas.mulAlpha(argb, a);
	}

	public static int mix(int a, int b, float t) {
		return xyz.nativelaunch.ui.gfx.Canvas.lerpColor(a, b, Math.max(0, Math.min(1, t)));
	}

	/** A stable, friendly colour for a name (avatar fallback). */
	public static int nameColor(String name) {
		int[] palette = {0xFF6366F1, 0xFF8B5CF6, 0xFFEC4899, 0xFFF43F5E, 0xFFF97316, 0xFFEAB308, 0xFF22C55E, 0xFF14B8A6, 0xFF06B6D4, 0xFF3B82F6};
		int h = name == null ? 0 : name.toLowerCase().hashCode();
		return palette[Math.abs(h % palette.length)];
	}
}
