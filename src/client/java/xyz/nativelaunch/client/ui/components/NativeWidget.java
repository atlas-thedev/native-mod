package xyz.nativelaunch.client.ui.components;

import xyz.nativelaunch.client.ui.render.AnimationUtil;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public abstract class NativeWidget extends AbstractWidget {

    protected float hoverProgress = 0.0f;
    protected Runnable onClickAction;

    public NativeWidget(int x, int y, int width, int height, Component message, Runnable onClickAction) {
        super(x, y, width, height, message);
        this.onClickAction = onClickAction;
    }

    public void setOnClick(Runnable onClickAction) {
        this.onClickAction = onClickAction;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (this.onClickAction != null && this.active && this.visible) {
            playButtonClickSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
            this.onClickAction.run();
        }
    }

    protected void updateHoverState(float delta) {
        float target = this.isHovered ? 1.0f : 0.0f;
        this.hoverProgress = AnimationUtil.lerp(this.hoverProgress, target, 0.25f);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
