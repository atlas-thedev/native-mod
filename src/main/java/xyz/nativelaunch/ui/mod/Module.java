package xyz.nativelaunch.ui.mod;

import java.util.ArrayList;
import java.util.List;

/** A Native client feature the player can switch on and tune (Lunar / Feather style). */
public abstract class Module {
	public static final String HUD = "HUD", MECHANIC = "Mechanic", VISUAL = "Visual", PERFORMANCE = "Performance";

	public final String id, name, description, category;
	public final int icon;
	public boolean enabled;
	/** Pinned by the player in the mod menu. */
	public boolean favorite;
	public final boolean defaultEnabled;
	public final List<Setting> settings = new ArrayList<Setting>();

	protected Module(String id, String name, String description, String category, int icon, boolean enabled) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.icon = icon;
		this.enabled = this.defaultEnabled = enabled;
	}

	protected <T extends Setting> T add(T s) {
		settings.add(s);
		return s;
	}

	public boolean isHud() {
		return false;
	}

	public void setEnabled(boolean on) {
		if (on == enabled) {
			return;
		}
		enabled = on;
		if (on) {
			onEnable();
		} else {
			onDisable();
		}
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Which parts of the player this module reads each frame (Game.NEED_* bits). */
	public int needs() {
		return 0;
	}

	/**
	 * Drawn over the game while playing, for modules that are not a movable box (the crosshair). The HUD editor draws it too
	 * ({@code editing}), so you can see it while you move things around.
	 */
	public void overlay(xyz.nativelaunch.ui.Ui ui, Game g, boolean editing) {
	}

	/** Draws a live preview in the mod page's stage (a box x, y, w, h). Return false to get the default icon. */
	public boolean preview(xyz.nativelaunch.ui.Ui ui, Game g, float x, float y, float w, float h) {
		return false;
	}

	/** True when {@link #overlay} would draw something right now (keeps the HUD pass running while playing). */
	public boolean overlays(Game g) {
		return false;
	}

	/** Called every frame while enabled (main thread). */
	public void frame(Game g) {
	}

	/** A key went down / up while playing (no Native screen open). Return true to swallow it. */
	public boolean onKey(int key, int action, Game g) {
		return false;
	}

	/** Mouse wheel while playing. Return true to swallow it. */
	public boolean onScroll(double dy, Game g) {
		return false;
	}

	/** Every setting, including shared appearance ones (HUD modules). */
	public List<Setting> allSettings() {
		return settings;
	}

	/** True for a setting that has no effect with the current choices (the menu leaves it out instead of showing a dead switch). */
	protected boolean hidden(Setting s) {
		return false;
	}

	private final List<Setting> shownBuf = new ArrayList<Setting>();

	/** The module's own settings that currently do something. */
	public List<Setting> shownSettings() {
		shownBuf.clear();
		for (int i = 0; i < settings.size(); i++) {
			if (!hidden(settings.get(i))) {
				shownBuf.add(settings.get(i));
			}
		}
		return shownBuf;
	}

	/** Everything the settings panel lists for this module. */
	public List<Setting> shownAll() {
		return shownSettings();
	}

	public void resetSettings() {
		for (Setting s : allSettings()) {
			s.reset();
		}
		Modules.changed();
	}
}
