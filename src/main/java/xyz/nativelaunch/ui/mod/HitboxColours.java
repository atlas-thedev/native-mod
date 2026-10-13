package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.ui.view.PlayerPreview;

/** Vanilla F3+B geometry and visibility; red eye-height markings are deliberately untouched. */
public final class HitboxColours extends Module {
    public final Setting.Color body = add(new Setting.Color("body", "Hitbox colour", 0xFFFFFFFF));
    public final Setting.Color direction = add(new Setting.Color("direction", "Look-direction colour", 0xFF0000FF));
    private final PlayerPreview player = new PlayerPreview(true);

    public HitboxColours() {
        super("hitbox-colours", "Hitbox Colours", "Choose the F3+B body and blue look-direction arrow colours. The red eye-height line stays vanilla.", VISUAL, Theme.I_CROSSHAIR, false);
        CombatColours.hitboxes = this;
    }

    @Override
    public boolean preview(Ui ui, Game g, float x, float y, float w, float h) {
        player.hitbox = true;
        player.hitboxColour = body.value | 0xFF000000;
        player.directionColour = direction.value | 0xFF000000;
        player.draw(ui, "hitbox:player", x, y, w, h - 20, null);
        String hint = "Drag to rotate";
        ui.c.text(Fonts.MEDIUM, 10, hint, x + (w - ui.c.textWidth(Fonts.MEDIUM, 10, hint)) / 2, y + h - 16, Theme.TEXT_MUTED);
        return true;
    }
}
