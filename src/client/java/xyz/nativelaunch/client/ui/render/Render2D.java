package xyz.nativelaunch.client.ui.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class Render2D {

    public static void drawRect(GuiGraphicsExtractor extractor, int x, int y, int width, int height, int color) {
        extractor.fill(x, y, x + width, y + height, color);
    }

    public static void drawOutline(GuiGraphicsExtractor extractor, int x, int y, int width, int height, int thickness, int color) {
        // Top
        extractor.fill(x, y, x + width, y + thickness, color);
        // Bottom
        extractor.fill(x, y + height - thickness, x + width, y + height, color);
        // Left
        extractor.fill(x, y + thickness, x + thickness, y + height - thickness, color);
        // Right
        extractor.fill(x + width - thickness, y + thickness, x + width, y + height - thickness, color);
    }

    public static void drawRoundedRect(GuiGraphicsExtractor extractor, int x, int y, int width, int height, int radius, int color) {
        if (radius <= 0) {
            drawRect(extractor, x, y, width, height, color);
            return;
        }

        radius = Math.min(radius, Math.min(width, height) / 2);

        // Center rectangle
        extractor.fill(x + radius, y, x + width - radius, y + height, color);

        // Left and Right strips
        extractor.fill(x, y + radius, x + radius, y + height - radius, color);
        extractor.fill(x + width - radius, y + radius, x + width, y + height - radius, color);

        // Smooth corner steps
        for (int i = 0; i < radius; i++) {
            int cornerOffset = (int) Math.round(Math.sqrt(radius * radius - (radius - i) * (radius - i)));
            int yTop = y + radius - 1 - i;
            int yBottom = y + height - radius + i;

            // Top-Left & Top-Right
            extractor.fill(x + radius - cornerOffset, yTop, x + radius, yTop + 1, color);
            extractor.fill(x + width - radius, yTop, x + width - radius + cornerOffset, yTop + 1, color);

            // Bottom-Left & Bottom-Right
            extractor.fill(x + radius - cornerOffset, yBottom, x + radius, yBottom + 1, color);
            extractor.fill(x + width - radius, yBottom, x + width - radius + cornerOffset, yBottom + 1, color);
        }
    }

    public static void drawGlassPanel(GuiGraphicsExtractor extractor, int x, int y, int width, int height, int radius, int bgColor, int borderColor) {
        // Background rounded surface
        drawRoundedRect(extractor, x, y, width, height, radius, bgColor);

        // Refined subtle border
        drawOutline(extractor, x, y, width, height, 1, borderColor);
    }

    public static void drawGlow(GuiGraphicsExtractor extractor, int x, int y, int width, int height, int glowColor, int spread) {
        for (int i = 1; i <= spread; i++) {
            int alpha = (int) (((spread - i + 1.0f) / spread) * 25.0f);
            int layerColor = ColorUtil.withAlpha(glowColor, alpha);
            drawRoundedRect(extractor, x - i, y - i, width + (i * 2), height + (i * 2), 3 + i, layerColor);
        }
    }

    public static void drawGradient(GuiGraphicsExtractor extractor, int x, int y, int width, int height, int startColor, int endColor) {
        extractor.fillGradient(x, y, x + width, y + height, startColor, endColor);
    }

    public static void drawCenteredString(GuiGraphicsExtractor extractor, Font font, String text, int centerX, int y, int color) {
        extractor.centeredText(font, text, centerX, y, color);
    }

    public static void drawString(GuiGraphicsExtractor extractor, Font font, String text, int x, int y, int color) {
        extractor.text(font, text, x, y, color);
    }
}
