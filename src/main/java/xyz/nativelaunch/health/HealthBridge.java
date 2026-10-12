package xyz.nativelaunch.health;

/** What the Native menu module needs from the game version (set by the version-specific hook once it is up). */
public interface HealthBridge {
	/** Registry id of the mob under the crosshair ("minecraft:zombie"), "minecraft:player" for a player, or null. */
	String targetTypeId();

	/** Show a short message ({@code chat}: in the chat, otherwise on the action bar); {@code rgb} < 0 means the default colour. */
	void message(String text, int rgb, boolean chat);

	/** Set by the hook on game start; null on versions without indicators. */
	BridgeHolder HOLDER = new BridgeHolder();

	final class BridgeHolder {
		public volatile HealthBridge bridge;
	}
}
