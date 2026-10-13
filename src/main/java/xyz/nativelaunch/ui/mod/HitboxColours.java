package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;

/** Uses vanilla F3+B and depth testing; this is not an always-on ESP overlay. */
public final class HitboxColours extends Module {
    public final Setting.Color body = add(new Setting.Color("body", "Hitbox colour", 0xFFFFFFFF));
    public final Setting.Color eye = add(new Setting.Color("eye", "Eye-line colour", 0xFFEF4444));

    public HitboxColours() {
        super("hitbox-colours", "Hitbox Colours", "Choose the body and red eye-line colours of F3+B entity hitboxes. F3+B still controls visibility.", VISUAL, Theme.I_CROSSHAIR, false);
        CombatColours.hitboxes = this;
    }

    @Override
    public boolean preview(Ui ui, Game g, float x, float y, float w, float h) {
        float bw = Math.min(w * 0.28f, h * 0.32f), bh = bw * 2;
        float bx = x + (w - bw) / 2, by = y + (h - bh) / 2, d = bw * 0.3f;
        ui.c.outline(bx, by, bw, bh, 0, 1.5f, body.value | 0xFF000000);
        ui.c.outline(bx + d, by - d, bw, bh, 0, 1.5f, body.value | 0xFF000000);
        ui.c.fill(bx, by + bh * 0.16f, bw + d, 2, eye.value | 0xFF000000);
        return true;
    }
}
