package xyz.nativelaunch.ui.gfx;

/** Original, CC0 Native module artwork. Codepoints mirror native-modules.json; append only. */
public final class ModuleIcons {
    public static final int FIRST = 0xF400;
    private static final String[] IDS = {"fps", "cps", "keystrokes", "coords", "direction", "clock", "memory", "ping", "server", "speed", "scoreboard", "bossbar", "togglesprint", "zoom", "fullbright", "bgfps", "effects", "armor", "inventory", "crosshair", "hitbox-colours", "critical-colours", "watermark"};
    private ModuleIcons() {}

    public static int forModule(String id, int fallback) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return FIRST + i;
        return fallback;
    }

    public static boolean custom(int codepoint) {
        return codepoint >= FIRST && codepoint < FIRST + IDS.length;
    }

    public static int count() { return IDS.length; }
}
