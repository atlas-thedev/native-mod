package dev.noctra.client.mixin;

import dev.noctra.client.ui.components.NoctraPlayerPreview;
import dev.noctra.client.ui.components.VanillaIconButton;
import dev.noctra.client.ui.render.IconDrawUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    private static String noctraVersionCache;

    private static String noctraVersion() {
        if (noctraVersionCache == null) {
            noctraVersionCache = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("noctra")
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("");
        }
        return noctraVersionCache;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void initNoctraTitleScreen(CallbackInfo ci) {
        // --- 1. Left Side: Natural Tall 3D Player Avatar & Wardrobe Button ---
        int playerWidth = 80;
        int playerHeight = 135;
        int playerX = 30;
        // Vertically center player nicely so head is level with Singleplayer row
        int playerY = Math.max(10, (this.height / 2) - 85);

        NoctraPlayerPreview playerPreview = new NoctraPlayerPreview(playerX, playerY, playerWidth, playerHeight);
        this.addRenderableWidget(playerPreview);

        // Vanilla-style 20x20 Wardrobe Button directly below player avatar
        int wardrobeSize = 20;
        int wardrobeX = playerX + (playerWidth - wardrobeSize) / 2;
        int wardrobeY = Math.min(this.height - 28, playerY + playerHeight + 4);

        this.addRenderableWidget(new VanillaIconButton(
                wardrobeX,
                wardrobeY,
                wardrobeSize,
                Component.literal("Wardrobe"),
                btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreenAndShow(new net.minecraft.client.gui.screens.options.SkinCustomizationScreen(this, this.minecraft.options));
                    }
                },
                (extractor, x, y, hovered) -> {
                    int color = hovered ? 0xFFFFFFFF : 0xFFD8D8D8;
                    IconDrawUtil.drawShirt(extractor, x, y, color);
                }
        ));

        // --- 2. Right Side: Vertical 6-Button Toolbar (With Solid Plate & Beveled Outlines) ---
        int btnSize = 20;
        int rightX = this.width - btnSize - 4;
        int totalHeight = (6 * btnSize) + (5 * 2); // 6 buttons + 2px gaps
        int startY = (this.height - totalHeight) / 2;
        int step = btnSize + 2;

        // 1. News Icon (RSS wave)
        this.addRenderableWidget(new VanillaIconButton(
                rightX, startY, btnSize,
                Component.literal("News"),
                btn -> {},
                (extractor, x, y, hovered) -> {
                    int color = hovered ? 0xFFFFFFFF : 0xFFD8D8D8;
                    IconDrawUtil.drawNews(extractor, x, y, color);
                }
        ));

        // 2. Friends / Social Icon
        this.addRenderableWidget(new VanillaIconButton(
                rightX, startY + step, btnSize,
                Component.literal("Friends"),
                btn -> {},
                (extractor, x, y, hovered) -> {
                    int color = hovered ? 0xFFFFFFFF : 0xFFD8D8D8;
                    IconDrawUtil.drawFriends(extractor, x, y, color);
                }
        ));

        // 3. Wardrobe / Cosmetics Icon
        this.addRenderableWidget(new VanillaIconButton(
                rightX, startY + (step * 2), btnSize,
                Component.literal("Wardrobe"),
                btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreenAndShow(new net.minecraft.client.gui.screens.options.SkinCustomizationScreen(this, this.minecraft.options));
                    }
                },
                (extractor, x, y, hovered) -> {
                    int color = hovered ? 0xFFFFFFFF : 0xFFD8D8D8;
                    IconDrawUtil.drawShirt(extractor, x, y, color);
                }
        ));

        // 4. Screenshots / Gallery Icon
        this.addRenderableWidget(new VanillaIconButton(
                rightX, startY + (step * 3), btnSize,
                Component.literal("Screenshots"),
                btn -> {},
                (extractor, x, y, hovered) -> {
                    int color = hovered ? 0xFFFFFFFF : 0xFFD8D8D8;
                    IconDrawUtil.drawGallery(extractor, x, y, color);
                }
        ));

        // 5. Settings / Mixer Sliders Icon
        this.addRenderableWidget(new VanillaIconButton(
                rightX, startY + (step * 4), btnSize,
                Component.literal("Noctra Settings"),
                btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreenAndShow(new net.minecraft.client.gui.screens.options.OptionsScreen(this, this.minecraft.options));
                    }
                },
                (extractor, x, y, hovered) -> {
                    int color = hovered ? 0xFFFFFFFF : 0xFFD8D8D8;
                    IconDrawUtil.drawSettings(extractor, x, y, color);
                }
        ));

        // 6. Account / Profile Face Icon
        this.addRenderableWidget(new VanillaIconButton(
                rightX, startY + (step * 5), btnSize,
                Component.literal("Account Profile"),
                btn -> {},
                (extractor, x, y, hovered) -> {
                    // Draw 8x8 player skin face centered
                    IconDrawUtil.drawPlayerHead(extractor, x + 2, y + 2, 8);
                }
        ));
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void renderNoctraTitleScreenOverlay(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();

        // 1. Calculate dynamically lowest button Y position so account text never overlaps
        int maxBtnBottom = 0;
        for (GuiEventListener listener : this.children()) {
            if (listener instanceof AbstractWidget widget) {
                if (!(widget instanceof VanillaIconButton) && !(widget instanceof NoctraPlayerPreview)) {
                    maxBtnBottom = Math.max(maxBtnBottom, widget.getY() + widget.getHeight());
                }
            }
        }

        // Place cleanly 24 pixels below the lowest vanilla button (Options/Quit)
        int accountY = (maxBtnBottom > 0) ? (maxBtnBottom + 24) : (this.height - 15);

        String username = mc.getUser() != null ? mc.getUser().getName() : "Player";
        String accountText = "Current Account: " + username;
        int textWidth = mc.font.width(accountText);
        int centerX = (this.width - textWidth) / 2;

        // Clean text with drop shadow (Warm coral color #E58B68)
        extractor.text(mc.font, accountText, centerX, accountY, 0xFFE58B68, true);

        // 2. Client Branding on Bottom Left (Cleanly above Minecraft version to prevent collision)
        String branding = "Noctra Client v" + noctraVersion();
        extractor.text(mc.font, branding, 2, this.height - 20, 0xFFAAAAAA, true);
    }
}
