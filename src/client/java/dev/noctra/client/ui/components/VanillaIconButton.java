package dev.noctra.client.ui.components;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public class VanillaIconButton extends Button {

    @FunctionalInterface
    public interface IconRenderer {
        void render(GuiGraphicsExtractor extractor, int x, int y, boolean hovered);
    }

    private final IconRenderer iconRenderer;

    public VanillaIconButton(int x, int y, int size, Component tooltip, OnPress onPress, IconRenderer iconRenderer) {
        super(x, y, size, size, Component.empty(), onPress, DEFAULT_NARRATION);
        this.iconRenderer = iconRenderer;
        if (tooltip != null) {
            this.setTooltip(Tooltip.create(tooltip));
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        int x = this.getX();
        int y = this.getY();
        int w = this.width;
        int h = this.height;
        boolean hovered = this.isHoveredOrFocused();

        // 1. Dark Solid Button Plate Background
        int bgColor = hovered ? 0xFF2D2D2D : 0xFF1C1C1C;
        extractor.fill(x + 1, y + 1, x + w - 1, y + h - 1, bgColor);

        // 2. High-Contrast Beveled Outline
        if (hovered) {
            // Bright white outline on hover (Essential style)
            extractor.fill(x, y, x + w, y + 1, 0xFFFFFFFF);             // Top
            extractor.fill(x, y + h - 1, x + w, y + h, 0xFFFFFFFF);     // Bottom
            extractor.fill(x, y + 1, x + 1, y + h - 1, 0xFFFFFFFF);     // Left
            extractor.fill(x + w - 1, y + 1, x + w, y + h - 1, 0xFFFFFFFF); // Right
        } else {
            // Classic Minecraft beveled outline
            extractor.fill(x, y, x + w, y + 1, 0xFF000000);             // Outer black top
            extractor.fill(x, y + h - 1, x + w, y + h, 0xFF000000);     // Outer black bottom
            extractor.fill(x, y, x + 1, y + h, 0xFF000000);             // Outer black left
            extractor.fill(x + w - 1, y, x + w, y + h, 0xFF000000);     // Outer black right

            // Inner light bevel (top/left)
            extractor.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF585858);
            extractor.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFF585858);

            // Inner dark bevel (bottom/right)
            extractor.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF121212);
            extractor.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, 0xFF121212);
        }

        // 3. Render Crisp Icon Centered
        if (this.iconRenderer != null) {
            int iconX = x + (w - 12) / 2;
            int iconY = y + (h - 11) / 2;
            this.iconRenderer.render(extractor, iconX, iconY, hovered);
        }
    }
}
