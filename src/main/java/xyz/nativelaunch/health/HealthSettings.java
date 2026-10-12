package xyz.nativelaunch.health;

/**
 * Everything the in-world health indicators read, as plain fields. The "Health Indicators" module in the Native
 * menu owns the real settings and copies them in here (once per frame), so the renderer never touches the UI
 * classes and none of this needs a Minecraft jar to compile or test.
 */
public final class HealthSettings {
	public static final int STYLE_HEARTS = 0, STYLE_NUMBER = 1, STYLE_DYNAMIC = 2;
	public static final int MESSAGE_ACTIONBAR = 0, MESSAGE_CHAT = 1, MESSAGE_NONE = 2;

	/** The settings the game hooks read. */
	public static final HealthSettings cur = new HealthSettings();

	/** Master switch: false until the module has loaded (and whenever the player turns it off). */
	public volatile boolean enabled;
	/** Runtime toggles (keybinds flip them). */
	public boolean hearts = true, armor = false;
	/** Held key: show everything on every living entity regardless of the filters. */
	public boolean override;

	// ── which entities ──
	public boolean passive = true, hostile = true, players = true, self = false;
	/** Also show the mob you are aiming at when blocks are in the way (never players). */
	public boolean throughWalls = false;
	/** Other players ignore the advanced criteria below. */
	public boolean overridePlayers = false;
	public boolean afterAttack = false;
	public int timeAfterHit = 60;
	public boolean damagedOnly = false;
	public int maxHealthPct = 100;
	public boolean lookingAt = false;
	public int reach = 3;
	public boolean withinDistance = false;
	public int distance = 64;

	// ── look ──
	public int style = STYLE_HEARTS;
	public int dynamicThreshold = 100;
	public int iconsPerRow = 10;
	/** 0 - 100 */
	public int opacity = 100;
	public boolean effects = true;
	public int numberColor = 0xFFFF0000;
	public boolean numberShadow = false;
	public int decimals = 1;
	public boolean percent = false;
	public int percentMax = 20;
	/** Icon size multiplier: 1 = 9 Native-UI pixels per icon. The size on screen does not depend on the distance. */
	public float size = 1f;
	/** Optional: shrink a little with distance (never below half size) instead of staying the same. */
	public boolean shrinkWithDistance = false;
	/** Extra height, in rows of icons. */
	public float offset = 0f;
	public float offsetStep = 0.25f;
	public boolean higherForPlayers = false;

	// ── messages ──
	public int messageType = MESSAGE_ACTIONBAR;
	public boolean coloredMessages = true;

	// ── damage direction arc ──
	public boolean arc = false;
	public float arcScale = 2f;
	public int arcColor = 0xFFFF0000;
	public int arcSeconds = 4;
	public boolean arcFade = true;
	public int arcFadeSeconds = 3;

	/** True when the number style should be used for an entity with this max health. */
	public boolean useNumber(float maxHealth, boolean tooManyIcons) {
		if (style == STYLE_NUMBER) {
			return true;
		}
		if (style == STYLE_DYNAMIC && maxHealth > dynamicThreshold) {
			return true;
		}
		return tooManyIcons;
	}
}
