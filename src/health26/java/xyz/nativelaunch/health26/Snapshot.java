package xyz.nativelaunch.health26;

import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import xyz.nativelaunch.health.HealthLayout;

/** One entity's indicator for one frame, filled in while the render state is extracted and drawn when it is submitted. */
public final class Snapshot {
	public boolean show, hearts, armor, number, seeThrough, regen, hardcore;
	/** 0 normal, 1 poisoned, 2 withered, 3 frozen. */
	public int heartKind;
	public float health, max, absorb;
	public int armorValue;
	/** Height of the first row above the entity's feet (blocks), before the user's offset. */
	public float baseHeight;
	public float ageInTicks;
	public double distanceSq;

	// the number text is rebuilt only when one of its inputs changes
	private float tHealth = Float.NaN, tMax, tAbsorb;
	private boolean tPercent;
	private int tDecimals = -1;
	private FormattedCharSequence seq;
	private int width;

	public FormattedCharSequence text(net.minecraft.client.gui.Font font, boolean percent, int decimals) {
		if (seq == null || tHealth != health || tMax != max || tAbsorb != absorb || tPercent != percent || tDecimals != decimals) {
			tHealth = health;
			tMax = max;
			tAbsorb = absorb;
			tPercent = percent;
			tDecimals = decimals;
			seq = Component.literal(HealthLayout.text(health, max, absorb, percent, decimals)).getVisualOrderText();
			width = font.width(seq);
		}
		return seq;
	}

	public int textWidth() {
		return width;
	}
}
