package xyz.nativelaunch.ui.gfx;

/** Turns a finished Canvas into pixels: raw OpenGL (1.16 - 26.1) or the game's own GUI pipeline (26.x). */
public interface Renderer {
	void render(Canvas c);
}
