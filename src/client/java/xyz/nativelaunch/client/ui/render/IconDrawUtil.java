package xyz.nativelaunch.client.ui.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.world.entity.player.PlayerSkin;

public class IconDrawUtil {

    /**
     * Draws an authentic, crisp pixel-art T-shirt icon (Essential Wardrobe style, 12x11)
     */
    public static void drawShirt(GuiGraphicsExtractor extractor, int x, int y, int color) {
        // Collar cut at top
        extractor.fill(x + 2, y, x + 4, y + 1, color);
        extractor.fill(x + 7, y, x + 9, y + 1, color);

        // Shoulders & Sleeves
        extractor.fill(x + 1, y + 1, x + 10, y + 2, color);
        extractor.fill(x, y + 2, x + 11, y + 3, color);
        extractor.fill(x, y + 3, x + 3, y + 5, color);
        extractor.fill(x + 8, y + 3, x + 11, y + 5, color);

        // Body / Torso
        extractor.fill(x + 3, y + 3, x + 8, y + 10, color);

        // Bottom hemline
        extractor.fill(x + 3, y + 10, x + 8, y + 11, ColorUtil.withAlpha(color, 180));
    }

    /**
     * Draws an authentic Friends / Social icon (Two player silhouettes, 12x11)
     */
    public static void drawFriends(GuiGraphicsExtractor extractor, int x, int y, int color) {
        // Left person (slightly lower/behind)
        extractor.fill(x + 1, y + 2, x + 5, y + 5, color);  // head 4x3
        extractor.fill(x, y + 6, x + 6, y + 11, color);      // body 6x5

        // Separator shadow
        extractor.fill(x + 5, y + 3, x + 6, y + 11, 0xFF141414);

        // Right person (main/foreground)
        extractor.fill(x + 7, y, x + 11, y + 4, color);     // head 4x4
        extractor.fill(x + 6, y + 5, x + 12, y + 11, color); // body 6x6
    }

    /**
     * Draws an authentic News / RSS Broadcast wave icon (Essential style, 12x11)
     */
    public static void drawNews(GuiGraphicsExtractor extractor, int x, int y, int color) {
        // Bottom-left radio dot (3x3)
        extractor.fill(x, y + 8, x + 3, y + 11, color);

        // Middle wave arc
        extractor.fill(x, y + 4, x + 2, y + 6, color);
        extractor.fill(x + 2, y + 3, x + 5, y + 5, color);
        extractor.fill(x + 5, y + 4, x + 7, y + 7, color);
        extractor.fill(x + 5, y + 7, x + 7, y + 11, color);

        // Outer wave arc
        extractor.fill(x, y, x + 3, y + 2, color);
        extractor.fill(x + 3, y, x + 7, y + 2, color);
        extractor.fill(x + 7, y + 1, x + 10, y + 4, color);
        extractor.fill(x + 9, y + 4, x + 11, y + 8, color);
        extractor.fill(x + 9, y + 8, x + 11, y + 11, color);
    }

    /**
     * Draws an authentic Screenshot / Picture frame icon (12x10)
     */
    public static void drawGallery(GuiGraphicsExtractor extractor, int x, int y, int color) {
        // Outer rectangular frame (1px border)
        extractor.fill(x, y, x + 12, y + 1, color);
        extractor.fill(x, y + 9, x + 12, y + 10, color);
        extractor.fill(x, y + 1, x + 1, y + 9, color);
        extractor.fill(x + 11, y + 1, x + 12, y + 9, color);

        // Sun dot at top right
        extractor.fill(x + 8, y + 2, x + 10, y + 4, color);

        // Mountain silhouettes inside
        // Left smaller peak
        extractor.fill(x + 2, y + 6, x + 5, y + 9, color);
        extractor.fill(x + 3, y + 5, x + 4, y + 6, color);

        // Right larger peak
        extractor.fill(x + 5, y + 4, x + 10, y + 9, color);
        extractor.fill(x + 6, y + 3, x + 8, y + 4, color);
    }

    /**
     * Draws an authentic Settings / Mixer sliders icon (12x11)
     */
    public static void drawSettings(GuiGraphicsExtractor extractor, int x, int y, int color) {
        // Track 1 (top)
        extractor.fill(x, y + 2, x + 12, y + 3, ColorUtil.withAlpha(color, 120));
        // Knob 1 (left)
        extractor.fill(x + 2, y + 1, x + 5, y + 4, color);

        // Track 2 (middle)
        extractor.fill(x, y + 5, x + 12, y + 6, ColorUtil.withAlpha(color, 120));
        // Knob 2 (right)
        extractor.fill(x + 7, y + 4, x + 10, y + 7, color);

        // Track 3 (bottom)
        extractor.fill(x, y + 8, x + 12, y + 9, ColorUtil.withAlpha(color, 120));
        // Knob 3 (center)
        extractor.fill(x + 4, y + 7, x + 7, y + 10, color);
    }

    /**
     * Draws the player's 8x8 skin face directly inside the button
     */
    public static void drawPlayerHead(GuiGraphicsExtractor extractor, int x, int y, int size) {
        Minecraft mc = Minecraft.getInstance();
        PlayerSkin skin = mc.getSkinManager().createLookup(mc.getGameProfile(), true).get();
        if (skin != null) {
            PlayerFaceExtractor.extractRenderState(extractor, skin, x, y, size);
        }
    }
}
