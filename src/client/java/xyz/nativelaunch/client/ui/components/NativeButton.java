package xyz.nativelaunch.client.ui.components;

import xyz.nativelaunch.client.ui.render.ColorUtil;
import xyz.nativelaunch.client.ui.render.Render2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class NativeButton extends NativeWidget {

    private final int cornerRadius;

    public NativeButton(int x, int y, int width, int height, Component message, Runnable onClickAction) {
        super(x, y, width, height, message, onClickAction);
        this.cornerRadius = 5;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        updateHoverState(delta);

        // Smooth background interpolation
        int bgColor = ColorUtil.lerpColor(ColorUtil.GLASS_BG, ColorUtil.GLASS_BG_HOVER, this.hoverProgress);
        int borderColor = ColorUtil.lerpColor(ColorUtil.GLASS_BORDER, ColorUtil.ACCENT_CYAN, this.hoverProgress);

        // Hover Glow
        if (this.hoverProgress > 0.05f) {
            Render2D.drawGlow(extractor, this.getX(), this.getY(), this.width, this.height, ColorUtil.ACCENT_CYAN, (int) (this.hoverProgress * 3));
        }

        // Glass panel surface
        Render2D.drawGlassPanel(extractor, this.getX(), this.getY(), this.width, this.height, this.cornerRadius, bgColor, borderColor);

        // Bottom Accent line when hovered
        if (this.hoverProgress > 0.05f) {
            int accentAlpha = (int) (this.hoverProgress * 255.0f);
            int accentColor = ColorUtil.withAlpha(ColorUtil.ACCENT_CYAN, accentAlpha);
            Render2D.drawRoundedRect(extractor, this.getX() + 4, this.getY() + this.height - 2, this.width - 8, 2, 1, accentColor);
        }

        // Text
        Font font = Minecraft.getInstance().font;
        int textColor = ColorUtil.lerpColor(ColorUtil.TEXT_PRIMARY, ColorUtil.ACCENT_CYAN, this.hoverProgress);
        String label = this.getMessage().getString();
        int textX = this.getX() + this.width / 2;
        int textY = this.getY() + (this.height - 8) / 2;

        Render2D.drawCenteredString(extractor, font, label, textX, textY, textColor);
    }
}
