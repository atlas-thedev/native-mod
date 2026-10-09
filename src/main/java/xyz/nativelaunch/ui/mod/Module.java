package xyz.nativelaunch.ui.mod;

import java.util.ArrayList;
import java.util.List;

/** A Native client feature the player can switch on and tune (Lunar / Feather style). */
public abstract class Module {
	public static final String HUD = "HUD", MECHANIC = "Mechanic", VISUAL = "Visual";

	public final String id, name, description, category;
	public final int icon;
	public boolean enabled;
	final boolean defaultEnabled;
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

	/** Called every frame while enabled (main thread). */
	public void frame(Game g) {
	}
}
