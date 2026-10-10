package xyz.nativelaunch.ui;

/** Smooth vertical scrolling for a clipped region, with a slim launcher-style scrollbar. */
public final class Scroll {
	public float offset, target, content;
	private float vx, vy, vw, vh;
	private boolean dragging;
	private float dragFrom, dragOffset;
	/** Stick to the bottom when new content arrives (chat). */
	public boolean stickBottom;
	/** Pinned to the bottom this frame (decided before the wheel moves anything). */
	private boolean pinned;

	/** Begins the region: pushes a clip and returns the y offset to subtract from content. */
	public float begin(Ui ui, float x, float y, float w, float h) {
		vx = x;
		vy = y;
		vw = w;
		vh = h;
		// Pinned = the *target* sits at the bottom. It used to be read from the animated offset after the wheel had
		// already moved the target, so in a chat every wheel step up was undone on the same frame (scrolling never
		// worked from the bottom) and the view twitched between the two positions.
		pinned = stickBottom && content > 0 && target >= Math.max(0, content - vh) - 2;
		if (ui.hover(x, y, w, h) && ui.scroll != 0) {
			target -= ui.scroll * 48;
			pinned = false;
		}
		ui.c.pushClip(x, y, w, h);
		return offset;
	}

	/** Ends the region with the content height measured while drawing. */
	public void end(Ui ui, float contentHeight) {
		ui.c.popClip();
		float max = Math.max(0, contentHeight - vh);
		content = contentHeight;
		if (pinned && !dragging) {
			target = max;
		}
		target = Math.max(0, Math.min(max, target));
		float k = 1f - (float) Math.exp(-18 * ui.dt);
		offset += (target - offset) * k;
		if (Math.abs(target - offset) < 0.5f) {
			offset = target;
		}
		if (max <= 0) {
			offset = target = 0;
			return;
		}
		float trackH = vh - 8;
		float thumbH = Math.max(24, trackH * vh / contentHeight);
		float thumbY = vy + 4 + (trackH - thumbH) * (offset / max);
		float tx = vx + vw - 6;
		boolean over = ui.hover(tx - 4, vy, 10, vh);
		if (over && ui.pressed) {
			dragging = true;
			dragFrom = ui.my;
			dragOffset = target;
		}
		if (!ui.down) {
			dragging = false;
		}
		if (dragging) {
			target = dragOffset + (ui.my - dragFrom) * (contentHeight / Math.max(1, trackH));
			target = Math.max(0, Math.min(max, target));
			offset = target;
		}
		float a = ui.anim("scroll#" + System.identityHashCode(this), over || dragging, 12f);
		ui.c.round(tx, thumbY, 4, thumbH, 2, Theme.mix(0x29FFFFFF, 0x47FFFFFF, a));
	}

	/** Content was added above what is on screen (older messages): move by the same amount so nothing jumps. */
	public void shift(float dy) {
		if (dy != 0) {
			offset += dy;
			target += dy;
		}
	}

	public void toBottom() {
		target = 1e9f;
	}

	public void snapBottom() {
		target = offset = Math.max(0, content - vh);
	}

	public boolean atBottom() {
		return target >= Math.max(0, content - vh) - 4;
	}
}
