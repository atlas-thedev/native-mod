package xyz.nativelaunch.ui;

/**
 * What the Native UI needs from Minecraft, implemented once per naming era (intermediary names for 1.16 - 1.21.11,
 * official names for 26.x). Screens are passed around as plain Objects.
 */
public interface McBridge {
	int TITLE = 1, RELAY = 2;

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
}
