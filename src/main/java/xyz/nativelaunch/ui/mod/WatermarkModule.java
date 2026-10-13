package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.ui.gfx.Image;

/** Quiet corner branding. Existing title/menu/editor logos are not duplicated. */
public final class WatermarkModule extends HudModule {
    public final Setting.Bool text = add(new Setting.Bool("text", "Show Native Client text", true));
    public final Setting.Num strength = add(new Setting.Num("strength", "Opacity", 55, 10, 100, 5, "%"));
    private Image logo;
    private boolean tried;
    private final float[] size = new float[2];

    public WatermarkModule() {
        super("watermark", "Native Watermark", "A subtle Native logo. Move or resize it in the HUD editor; hide it with F1 or switch it off.", Theme.I_SPARKLES, true, 1f, 0f);
        drop(style);
    }

    @Override
    public float[] measure(Canvas c, Game g) {
        float k = scale.value;
        size[0] = (text.value ? 24 + c.textWidth(Fonts.SEMIBOLD, 10, "Native Client") : 18) * k;
        size[1] = 18 * k;
        return size;
    }

    @Override
    public void paint(Ui ui, float x, float y, Game g) {
        if (!tried) { tried = true; logo = Image.resource("/assets/native/ui/logo.png"); }
        Canvas c = ui.c;
        float k = scale.value, d = 18 * k;
        int tint = Theme.alpha(0xFFFFFFFF, strength.value / 100f);
        if (logo != null) c.stamp("native:watermark", logo, x, y, d, d, true, tint);
        else c.icon(Theme.I_SPARKLES, d, x + d / 2, y + d / 2, tint);
        if (text.value) {
            float fs = 10 * k, ty = y + (d - c.lineHeight(Fonts.SEMIBOLD, fs)) / 2;
            c.text(Fonts.SEMIBOLD, fs, "Native Client", x + 24 * k + k, ty + k, Theme.alpha(0xFF000000, strength.value / 100f));
            c.text(Fonts.SEMIBOLD, fs, "Native Client", x + 24 * k, ty, tint);
        }
    }
}
