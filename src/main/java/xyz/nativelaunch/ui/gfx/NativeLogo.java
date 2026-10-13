package xyz.nativelaunch.ui.gfx;

/** One shared Native brand asset; never substitutes a sparkle or a different N design. */
public final class NativeLogo {
    private static Image image;
    private static boolean tried;
    private NativeLogo() {}

    public static boolean draw(Canvas c, float x, float y, float size, int tint) {
        if (!tried) { tried = true; image = Image.resource("/assets/native/ui/logo.png"); }
        if (image == null) return false;
        c.stamp("native:brand-logo", image, x, y, size, size, true, tint);
        return true;
    }
}
