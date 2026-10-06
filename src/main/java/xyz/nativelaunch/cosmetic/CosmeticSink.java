package xyz.nativelaunch.cosmetic;

/**
 * What a Minecraft version's renderer provides to {@link CosmeticRenderer}: a matrix stack, the vanilla part
 * transforms and a way to draw one baked part. Every call happens on the render thread.
 */
public interface CosmeticSink {
	void push();

	void pop();

	/** Moves into the space of the vanilla model part a cosmetic root is attached to. */
	void attach(CosmeticModel.Attach attach);

	/** Translate by a pivot (pixels) then rotate z, y, x (radians), like a vanilla ModelPart. */
	void transform(float px, float py, float pz, float pitch, float yaw, float roll);

	/** Draws the boxes of one part (already transformed) with its texture and layer. */
	void draw(CosmeticLibrary.Loaded cosmetic, CosmeticModel.Part part);
}
