package xyz.nativelaunch.ui.mod;

import java.lang.reflect.Method;
import xyz.nativelaunch.core.Log;

/** Cosmetic-only hooks: never alter collision boxes, damage, particle counts or visibility. */
public final class CombatColours {
    static HitboxColours hitboxes;
    static CriticalColours critical;
    private CombatColours() {}

    public static int hitbox(int vanilla) {
        HitboxColours m = hitboxes;
        if (m == null || !m.enabled) return vanilla;
        if (vanilla == 0xFFFFFFFF) return 0xFF000000 | (m.body.value & 0xFFFFFF);
        if (vanilla == 0xFF0000FF) return 0xFF000000 | (m.direction.value & 0xFFFFFF);
        return vanilla; // the red eye-height, riding, server-side and multipart debug colours stay vanilla
    }

    public static int hitbox(float r, float g, float b) {
        if (r == 1f && g == 1f && b == 1f) return hitbox(0xFFFFFFFF);
        if (r == 0f && g == 0f && b == 1f) return hitbox(0xFF0000FF);
        return 0; // sentinel: leave the original floats untouched
    }

    public static float channel(int argb, int shift) {
        return ((argb >>> shift) & 255) / 255f;
    }

    // Colour setters moved from Particle to SingleQuadParticle in 1.21.9.
    // Resolve once per particle class, not once per particle or frame.
    private static final ClassValue<Method> setters = new ClassValue<Method>() {
        protected Method computeValue(Class<?> type) {
            for (String name : new String[]{"method_3084", "method_74305", "setColor"}) {
                try { return type.getMethod(name, float.class, float.class, float.class); }
                catch (NoSuchMethodException ignored) {}
            }
            Log.warn("Critical colours unsupported by particle class {}", type.getName());
            return null;
        }
    };

    public static void particle(Object particle, boolean enchanted) {
        CriticalColours m = critical;
        if (particle == null || m == null || !m.enabled) return;
        int rgb = enchanted ? m.enchanted.value : m.normal.value;
        try {
            Method setter = setters.get(particle.getClass());
            if (setter != null) setter.invoke(particle, channel(rgb, 16), channel(rgb, 8), channel(rgb, 0));
        } catch (ReflectiveOperationException ignored) {
            // An optional colour must never stop the game's particle spawning.
        }
    }
}
