package xyz.nativelaunch.ui;

/**
 * What the Native UI needs from Minecraft, implemented once per naming era (intermediary names for 1.16 - 1.21.11,
 * official names for 26.x). Screens are passed around as plain Objects.
 */
public interface McBridge {
	int TITLE = 1, RELAY = 2, MENU = 3, HUD = 4;

	boolean ready();

	Object screen();

	boolean isVanillaTitle(Object screen);

	/** 0 when the screen is not one of ours, else TITLE / RELAY. */
	int hostKind(Object screen);

	Object hostParent(Object screen);

	Object newHost(int kind, Object parent);

	Object newVanillaTitle();

	void setScreen(Object screen);

	/** singleplayer, multiplayer, realms, options, mods */
	boolean open(String action, Object parent);

	boolean hasMods();

	void quit();

	boolean inWorld();

	/** A loading overlay (resource reload) is covering the screen. */
	boolean overlay();

	long window();

	int fbWidth();

	int fbHeight();

	int windowWidth();

	int windowHeight();

	String tr(String key, String fallback);

	String version();

	// ── client mods (default: "not available", so a bridge without them simply shows no HUD data) ──

	/** The open screen is the vanilla chat (HUD stays visible behind it). */
	default boolean isChat(Object screen) {
		return false;
	}

	/** Fills x, y, z, yaw, pitch of the local player; false when there is none. */
	default boolean player(double[] out) {
		return false;
	}

	/** Latency to the server in ms, or -1. */
	default int ping() {
		return -1;
	}

	/** Address of the current server, "Singleplayer", or null. */
	default String server() {
		return null;
	}

	/** F1: the vanilla HUD is hidden. */
	default boolean hudHidden() {
		return false;
	}

	/** F3: the debug overlay is open. */
	default boolean debugOpen() {
		return false;
	}

	/**
	 * GLFW code bound to a vanilla control (forward, left, back, right, jump, sneak, sprint, attack, use), -1 when
	 * unknown. Codes 0 - 7 are mouse buttons.
	 */
	default int boundKey(String control) {
		return -1;
	}

	/** Holds (or lets go of) a vanilla control, like the key being down. */
	default void setPressed(String control, boolean pressed) {
	}

	/** Brightness option value, NaN when unknown. */
	default double gamma() {
		return Double.NaN;
	}

	default void setGamma(double value) {
	}

	/** Cinematic (smooth) camera option. */
	default boolean smoothCamera() {
		return false;
	}

	default void setSmoothCamera(boolean on) {
	}

	/** The signed-in Minecraft name. */
	default String username() {
		return null;
	}
}
