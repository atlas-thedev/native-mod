package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;

/** Tints CRIT and ENCHANTED_HIT, not the separate damage-indicator particle. */
public final class CriticalColours extends Module {
    public final Setting.Color normal = add(new Setting.Color("normal", "Critical-hit colour", 0xFFFFC857));
    public final Setting.Color enchanted = add(new Setting.Color("enchanted", "Enchanted-hit colour", 0xFF9D7BFF));

    public CriticalColours() {
        super("critical-colours", "Critical Colours", "Custom colours for critical-hit sparks, bow critical trails and enchanted-hit particles. Keeps vanilla counts and movement.", VISUAL, Theme.I_SPARKLES, false);
        CombatColours.critical = this;
    }

    @Override
    public boolean preview(Ui ui, Game g, float x, float y, float w, float h) {
        for (int i = 0; i < 16; i++) {
            float a = i * 2.39996f, r = 12 + (i % 5) * Math.min(w, h) * 0.07f;
            float px = x + w / 2 + (float)Math.cos(a) * r, py = y + h / 2 + (float)Math.sin(a) * r;
            int col = (i % 2 == 0 ? normal.value : enchanted.value) | 0xFF000000;
            ui.c.fill(px - 3, py - 1, 6, 2, col);
            ui.c.fill(px - 1, py - 3, 2, 6, col);
        }
        return true;
    }
}
