package xyz.nativelaunch.client.ui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.function.Supplier;

public class NativePlayerPreview extends NativeWidget {

    private final Model.Simple wideModel;
    private final Model.Simple slimModel;
    private final Supplier<PlayerSkin> skinSupplier;

    private float rotationX = -5.0f;
    private float rotationY = 25.0f; // Turned 25 degrees naturally like Essential

    public NativePlayerPreview(int x, int y, int width, int height) {
        super(x, y, width, height, Component.literal("Player Preview"), null);

        Minecraft mc = Minecraft.getInstance();
        EntityModelSet modelSet = mc.getEntityModels();
        this.skinSupplier = mc.getSkinManager().createLookup(mc.getGameProfile(), true);

        ModelPart widePart = modelSet.bakeLayer(ModelLayers.PLAYER);
        ModelPart slimPart = modelSet.bakeLayer(ModelLayers.PLAYER_SLIM);

        this.wideModel = new Model.Simple(widePart, RenderTypes::entityTranslucent);
        this.slimModel = new Model.Simple(slimPart, RenderTypes::entityTranslucent);
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        this.rotationY = Mth.clamp(this.rotationY + (float) dragX * 2.5f, -80.0f, 80.0f);
        this.rotationX = Mth.clamp(this.rotationX - (float) dragY * 2.5f, -30.0f, 30.0f);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        updateHoverState(delta);

        PlayerSkin skin = this.skinSupplier.get();
        if (skin != null) {
            float scale = 0.97f * this.height / 2.125f;
            Model.Simple model = (skin.model() == PlayerModelType.SLIM) ? this.slimModel : this.wideModel;

            extractor.skin(
                    model,
                    skin.body().texturePath(),
                    scale,
                    this.rotationX,
                    this.rotationY,
                    -1.0625f,
                    this.getX(),
                    this.getY(),
                    this.getRight(),
                    this.getBottom()
            );
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
