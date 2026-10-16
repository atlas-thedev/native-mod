package xyz.nativelaunch.ui;

/**
 * What the Native UI needs from Minecraft, implemented once per naming era (intermediary names for 1.16 - 1.21.11,
 * official names for 26.x). Screens are passed around as plain Objects.
 */
public interface McBridge {
	int TITLE = 1, RELAY = 2, MENU = 3, HUD = 4, PAUSE = 5;

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

	/** Joins a multiplayer server (host or host:port) like the vanilla server list does. */
	default boolean connect(String address, Object parent) {
		return false;
	}

	/** The vanilla pause screen (Esc in a world). */
	default boolean isVanillaPause(Object screen) {
		return false;
	}

	/** Leaves the world like the vanilla "Save and Quit to Title" / "Disconnect" button. */
	default void exitWorld() {
	}

	/** The game thread's task queue (Minecraft is an Executor on every version). */
	java.util.concurrent.Executor gameThread();

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

	/** Fills x, y, z of another player in the loaded world, found by in-game name; false when not loaded. */
	default boolean playerPos(String name, double[] out) {
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

	/** "particles" (0 all, 1 decreased, 2 minimal) or "entityDistance" (scale); NaN when unknown. */
	default double videoOption(String key) {
		return Double.NaN;
	}

	default void setVideoOption(String key, double value) {
	}

	/** The signed-in Minecraft name. */
	/** Vanilla GUI size: {scaledWidth, scaledHeight, guiScale}; false when unknown. */
	default boolean guiSize(float[] out) {
		return false;
	}

	/**
	 * Fills the player state the status modules show: health, food, effects, armour, inventory and the entity under the
	 * crosshair, limited to the parts in {@code needs} (Game.NEED_* bits). False when this version cannot be read.
	 */
	default boolean vitals(xyz.nativelaunch.ui.mod.Game g, int needs) {
		return false;
	}

	/** Path of the dimension you are in: "overworld", "the_nether", "the_end" (or a custom one); null when unknown. */
	default String dimension() {
		return null;
	}

	/** The world's time of day in ticks (0 = 06:00, 12000 = dusk), -1 when this version cannot be read. */
	default long dayTime() {
		return -1;
	}

	/** Distance from your eyes to the entity under the crosshair, NaN when you are not pointing at one. */
	default double reach() {
		return Double.NaN;
	}

	/** Recolours the red flash of a hurt entity (ABGR, as the game's overlay texture stores it). False when this version cannot. */
	default boolean setHurtTint(int abgr) {
		return false;
	}

	/**
	 * The contents of the shulker box in your main hand, else your off hand (27 slots into {@code out}, in slot order).
	 * Returns the box's item id ("purple_shulker_box"), null when neither hand holds one or this version cannot read it.
	 */
	default String shulkerInHand(xyz.nativelaunch.ui.mod.Game.Slot[] out) {
		return null;
	}

	/** The item stack under the cursor in an open inventory / chest screen, null when there is none. */
	default Object hoveredStack() {
		return null;
	}

	/** Like {@link #shulkerInHand} for any item stack: its id when it is a shulker box (contents into {@code out}), else null. */
	default String shulkerContents(Object stack, xyz.nativelaunch.ui.mod.Game.Slot[] out) {
		return null;
	}

	/**
	 * Items of yours that are on cooldown right now (ender pearl, shield, chorus fruit ...): fills {@code ids} (bare item ids)
	 * and {@code progress} (1 = just started, 0 = ready) and returns how many. 0 when none or this version cannot be read.
	 */
	default int cooldowns(String[] ids, float[] progress) {
		return 0;
	}

	/** The local player's entity object (what the game passes to its look-direction hook), or null. */
	default Object playerEntity() {
		return null;
	}

	/** Camera perspective: 0 first person, 1 third person (behind), 2 third person (front); -1 when unknown. */
	default int perspective() {
		return -1;
	}

	default void setPerspective(int perspective) {
	}

	/** Reads the scoreboard sidebar; false when this version can't be read (the vanilla one then stays). */
	default boolean sidebar(xyz.nativelaunch.ui.mod.Overlays.Sidebar out) {
		return false;
	}

	/** Reads the boss bars; false when this version can't be read. */
	default boolean bossBars(xyz.nativelaunch.ui.mod.Overlays.Bars out) {
		return false;
	}

	default String username() {
		return null;
	}

	// ── Minecraft 26.x: the UI is drawn through the game's GUI pipeline and fed by the game's input handlers ──

	/** The renderer to use; null = raw OpenGL. */
	default xyz.nativelaunch.ui.gfx.Renderer renderer() {
		return null;
	}

	/** True when frames come from the GUI pass (UiRuntime.onGuiFrame) instead of RenderSystem.flipFrame. */
	default boolean guiFrames() {
		return false;
	}

	/** True when keyboard / mouse events arrive through mixins instead of GLFW callbacks. */
	default boolean handlesInput() {
		return false;
	}

	/** Cursor position in window coordinates; false when unknown. */
	default boolean cursor(double[] out) {
		return false;
	}

	default String clipboard() {
		return null;
	}

	default void setClipboard(String text) {
	}

	// ── keyboard / window state (GLFW on 1.16 - 26.2, the game's own API on 26.3+) ──

	/**
	 * Live state of a key (codes 0 - 7 are mouse buttons): 1 down, 0 up, -1 when this version cannot be polled.
	 * On -1 the caller falls back to the key state tracked from the input events.
	 */
	default int keyState(int code) {
		return -1;
	}

	/** The platform's own label for a key, null when unknown. */
	default String keyLabel(int code) {
		return null;
	}

	/** False while the game window is in the background. */
	default boolean windowFocused() {
		return true;
	}

	/** True while the game window is minimised / iconified. */
	default boolean windowMinimized() {
		return false;
	}

}
