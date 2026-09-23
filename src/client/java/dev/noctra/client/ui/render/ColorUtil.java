package dev.noctra.client.ui.render;

public class ColorUtil {

    // Theme Palettes
    public static final int ACCENT_CYAN = rgba(0, 229, 255, 255);
    public static final int ACCENT_ORANGE = rgba(255, 140, 66, 255);
    public static final int ACCENT_PURPLE = rgba(157, 78, 221, 255);

    // Glassmorphism Grays
    public static final int GLASS_BG = rgba(20, 20, 25, 160);
    public static final int GLASS_BG_HOVER = rgba(35, 35, 45, 200);
    public static final int GLASS_BORDER = rgba(255, 255, 255, 30);
    public static final int GLASS_BORDER_HOVER = rgba(0, 229, 255, 180);

    // Text Colors
    public static final int TEXT_PRIMARY = rgba(245, 245, 250, 255);
    public static final int TEXT_SECONDARY = rgba(160, 160, 175, 255);
    public static final int TEXT_MUTED = rgba(100, 100, 115, 255);

    public static int rgba(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) |
               ((r & 0xFF) << 16) |
               ((g & 0xFF) << 8)  |
               (b & 0xFF);
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static int lerpColor(int from, int to, float factor) {
        factor = Math.clamp(factor, 0.0f, 1.0f);
        int a1 = (from >> 24) & 0xFF;
        int r1 = (from >> 16) & 0xFF;
        int g1 = (from >> 8) & 0xFF;
        int b1 = from & 0xFF;

        int a2 = (to >> 24) & 0xFF;
        int r2 = (to >> 16) & 0xFF;
        int g2 = (to >> 8) & 0xFF;
        int b2 = to & 0xFF;

        int a = (int) (a1 + (a2 - a1) * factor);
        int r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor);
        int b = (int) (b1 + (b2 - b1) * factor);

        return rgba(r, g, b, a);
    }
}
