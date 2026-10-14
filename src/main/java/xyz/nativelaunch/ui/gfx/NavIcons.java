package xyz.nativelaunch.ui.gfx;

import java.util.HashMap;
import java.util.Map;

/** Native navigation icons drawn in code (24 x 24 line art, rasterised with anti-aliasing at the exact pixel size). */
public final class NavIcons {
    public static final int MODS = 0xF500, LOCKER = 0xF501, SETTINGS = 0xF502;
    private static final float STROKE = 1.9f;
    private static final Map<Long, Image> cache = new HashMap<Long, Image>();

    private NavIcons() {}

    public static boolean has(int codepoint) {
        return codepoint >= MODS && codepoint <= SETTINGS;
    }

    /** White icon of px x px pixels (tinted when drawn). */
    static Image get(int codepoint, int px) {
        long key = ((long) codepoint << 32) | px;
        Image img = cache.get(key);
        if (img == null) {
            if (cache.size() > 256) cache.clear();
            img = render(codepoint, px);
            cache.put(key, img);
        }
        return img;
    }

    private static Image render(int cp, int px) {
        int[] argb = new int[px * px];
        float k = 24f / px, half = STROKE / 2, aa = k;
        for (int j = 0; j < px; j++) {
            for (int i = 0; i < px; i++) {
                float x = (i + 0.5f) * k, y = (j + 0.5f) * k;
                float d = dist(cp, x, y);
                float a = Math.max(0f, Math.min(1f, 0.5f - (d - half) / aa));
                if (a > 0) argb[j * px + i] = (Math.round(a * 255) << 24) | 0xFFFFFF;
            }
        }
        return new Image(px, px, argb);
    }

    /** Distance (viewBox units) from the point to the stroke centre line; filled parts return -half. */
    private static float dist(int cp, float x, float y) {
        switch (cp) {
            case MODS: {
                float d = roundRect(x, y, 3, 3, 7.5f, 7.5f, 2.2f);
                d = Math.min(d, roundRect(x, y, 3, 13.5f, 7.5f, 7.5f, 2.2f));
                d = Math.min(d, roundRect(x, y, 13.5f, 13.5f, 7.5f, 7.5f, 2.2f));
                float s = star(x, y, 17.25f, 6.75f, 4.6f, 1.25f);
                return Math.min(d, s + STROKE / 2);
            }
            case LOCKER: {
                float d = arc(x, y, 12, 6, 2.4f, -180, 40);
                double a = Math.toRadians(40);
                d = Math.min(d, seg(x, y, 12 + 2.4f * (float) Math.cos(a), 6 + 2.4f * (float) Math.sin(a), 12, 9.6f));
                d = Math.min(d, seg(x, y, 12, 9.6f, 2.8f, 16.4f));
                d = Math.min(d, seg(x, y, 12, 9.6f, 21.2f, 16.4f));
                d = Math.min(d, seg(x, y, 2.8f, 16.4f, 4.2f, 18.6f));
                d = Math.min(d, seg(x, y, 21.2f, 16.4f, 19.8f, 18.6f));
                return Math.min(d, seg(x, y, 4.2f, 18.6f, 19.8f, 18.6f));
            }
            default: {
                float[] ys = {6, 12, 18}, kx = {15.5f, 8.5f, 13.5f};
                float r = 2.4f, d = Float.MAX_VALUE;
                for (int i = 0; i < 3; i++) {
                    d = Math.min(d, seg(x, y, 3, ys[i], kx[i] - r - 1.6f, ys[i]));
                    d = Math.min(d, seg(x, y, kx[i] + r + 1.6f, ys[i], 21, ys[i]));
                    d = Math.min(d, Math.abs(len(x - kx[i], y - ys[i]) - r));
                }
                return d;
            }
        }
    }

    private static float len(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }

    private static float seg(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        float t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));
        return len(px - ax - t * dx, py - ay - t * dy);
    }

    /** Arc outline, angles in degrees (screen space, y down), from a0 to a1. */
    private static float arc(float px, float py, float cx, float cy, float r, float a0, float a1) {
        double a = Math.toDegrees(Math.atan2(py - cy, px - cx));
        while (a < a0) a += 360;
        if (a <= a1) return Math.abs(len(px - cx, py - cy) - r);
        double r0 = Math.toRadians(a0), r1 = Math.toRadians(a1);
        float e0 = len(px - cx - r * (float) Math.cos(r0), py - cy - r * (float) Math.sin(r0));
        float e1 = len(px - cx - r * (float) Math.cos(r1), py - cy - r * (float) Math.sin(r1));
        return Math.min(e0, e1);
    }

    private static float roundRect(float px, float py, float x, float y, float w, float h, float r) {
        float hx = w / 2 - r, hy = h / 2 - r;
        float qx = Math.abs(px - (x + w / 2)) - hx, qy = Math.abs(py - (y + h / 2)) - hy;
        float out = len(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - r;
        return Math.abs(out);
    }

    /** Signed distance to a filled four-point sparkle (negative inside). */
    private static float star(float px, float py, float cx, float cy, float outer, float inner) {
        float[] xs = new float[8], ys = new float[8];
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 - 90);
            float r = i % 2 == 0 ? outer : inner;
            xs[i] = cx + r * (float) Math.cos(a);
            ys[i] = cy + r * (float) Math.sin(a);
        }
        float d = Float.MAX_VALUE;
        boolean inside = false;
        for (int i = 0, j = 7; i < 8; j = i++) {
            d = Math.min(d, seg(px, py, xs[j], ys[j], xs[i], ys[i]));
            if ((ys[i] > py) != (ys[j] > py) && px < (xs[j] - xs[i]) * (py - ys[i]) / (ys[j] - ys[i]) + xs[i]) inside = !inside;
        }
        return inside ? -d : d;
    }
}
