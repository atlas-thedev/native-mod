package xyz.nativelaunch.client.ui.components;

import xyz.nativelaunch.client.ui.render.ColorUtil;
import xyz.nativelaunch.client.ui.render.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class NativeIconButton extends NativeWidget {

    private final String iconText;
    private final Component tooltip;
    private final int cornerRadius;

    public NativeIconButton(int x, int y, int size, String iconText, Component tooltip, Runnable onClickAction) {
        super(x, y, size, size, Component.literal(iconText), onClickAction);
        this.iconText = iconText;
        this.tooltip = tooltip;
        this.cornerRadius = 4;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        updateHoverState(delta);

        // Smooth background interpolation
        int bgColor = ColorUtil.lerpColor(ColorUtil.GLASS_BG, ColorUtil.GLASS_BG_HOVER, this.hoverProgress);
        int borderColor = ColorUtil.lerpColor(ColorUtil.GLASS_BORDER, ColorUtil.GLASS_BORDER_HOVER, this.hoverProgress);

        // Subtle glow when hovered
        if (this.hoverProgress > 0.05f) {
            Render2D.drawGlow(extractor, this.getX(), this.getY(), this.width, this.height, ColorUtil.ACCENT_CYAN, (int) (this.hoverProgress * 3));
        }

        // Glass panel
        Render2D.drawGlassPanel(extractor, this.getX(), this.getY(), this.width, this.height, this.cornerRadius, bgColor, borderColor);

        // Icon Rendering (centered)
        Font font = Minecraft.getInstance().font;
        int iconColor = ColorUtil.lerpColor(ColorUtil.TEXT_SECONDARY, ColorUtil.ACCENT_CYAN, this.hoverProgress);
        int iconWidth = font.width(this.iconText);
        int iconX = this.getX() + (this.width - iconWidth) / 2;
        int iconY = this.getY() + (this.height - 8) / 2;

        Render2D.drawString(extractor, font, this.iconText, iconX, iconY, iconColor);

        // Show Tooltip when hovered
        if (this.isHovered && this.tooltip != null) {
            extractor.setTooltipForNextFrame(this.tooltip, mouseX, mouseY);
        }
    }
}
