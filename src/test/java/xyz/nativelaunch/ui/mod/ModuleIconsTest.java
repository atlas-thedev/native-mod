package xyz.nativelaunch.ui.mod;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import xyz.nativelaunch.ui.gfx.ModuleIcons;
import static org.junit.jupiter.api.Assertions.*;

class ModuleIconsTest {
    @Test void everyCurrentModuleHasItsOwnOriginalIconAndAssetMapping() throws Exception {
        Set<Integer> glyphs = new HashSet<Integer>();
        Set<String> outlines = new HashSet<String>();
        Set<String> ids = new HashSet<String>();
        JsonObject manifest;
        try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream("/assets/native/ui/native-modules.json"), StandardCharsets.UTF_8)) {
            manifest = new JsonParser().parse(reader).getAsJsonObject();
        }
        // Idempotent initialisation, shared with the existing module consistency test.
        Modules.init(null);
        assertEquals(ModuleIcons.count(), Modules.all().size());
        assertEquals("CC0-1.0", manifest.get("license").getAsString());
        for (JsonElement e : manifest.getAsJsonArray("icons")) {
            JsonObject it = e.getAsJsonObject(); String id = it.get("id").getAsString();
            Module m = Modules.get(id); assertNotNull(m,id);
            int cp = Integer.parseInt(it.get("codepoint").getAsString(),16);
            assertTrue(ModuleIcons.custom(m.icon),id);assertEquals(cp,m.icon,id);
            assertTrue(glyphs.add(cp),"shared glyph: "+id);assertTrue(ids.add(id));
            assertTrue(outlines.add(it.get("sha256").getAsString()),"shared outline: "+id);
        }
        assertEquals(Modules.all().size(),ids.size());
        assertNotNull(getClass().getResource("/assets/native/ui/native-modules.ttf"));
    }
    @Test void customFontRangeDoesNotChangeNavigationIcons() {
        assertFalse(ModuleIcons.custom(0xE158)); assertFalse(ModuleIcons.custom(0xF100));
        assertEquals(0xE158,ModuleIcons.forModule("unknown-future-module",0xE158));
    }
}
