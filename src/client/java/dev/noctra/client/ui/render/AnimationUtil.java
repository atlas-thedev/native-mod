package dev.noctra.client.ui.render;

public class AnimationUtil {

    public static float lerp(float current, float target, float speed) {
        return current + (target - current) * Math.clamp(speed, 0.0f, 1.0f);
    }

    public static float easeOutCubic(float x) {
        x = Math.clamp(x, 0.0f, 1.0f);
        return (float) (1.0 - Math.pow(1.0 - x, 3));
    }

    public static float easeInOutQuad(float x) {
        x = Math.clamp(x, 0.0f, 1.0f);
        return x < 0.5f ? 2.0f * x * x : 1.0f - (float) Math.pow(-2.0 * x + 2.0, 2) / 2.0f;
    }
}
