package xyz.nativelaunch.ui.mod;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatColoursTest {
    public static class OldParticle { float r,g,b; public void method_3084(float r,float g,float b) { this.r=r;this.g=g;this.b=b; } }
    public static class NewParticle { float r,g,b; public void method_74305(float r,float g,float b) { this.r=r;this.g=g;this.b=b; } }
    public static class OfficialParticle { float r,g,b; public void setColor(float r,float g,float b) { this.r=r;this.g=g;this.b=b; } }
    @Test void disabledHitboxesLeaveEveryVanillaColourAlone() {
        HitboxColours m = new HitboxColours();
        assertFalse(m.enabled);
        for (int c : new int[]{0xFFFFFFFF,0xFFFF0000,0xFF00FF00,0xFF0000FF,0xFFFFFF00}) assertEquals(c,CombatColours.hitbox(c));
    }
    @Test void onlyBodyAndEyeLinesAreTintedWithOpaqueColours() {
        HitboxColours m = new HitboxColours();m.enabled=true;
        m.body.value=0x00336699;m.eye.value=0xFF987654;
        assertEquals(0xFF336699,CombatColours.hitbox(1f,1f,1f));
        assertEquals(0xFF987654,CombatColours.hitbox(1f,0f,0f));
        assertEquals(0,CombatColours.hitbox(0f,1f,0f));
        assertEquals(0xFF0000FF,CombatColours.hitbox(0xFF0000FF));
    }
    @Test void particleSetterNamesWorkAcrossAllThreeLayouts() {
        CriticalColours m = new CriticalColours();m.enabled=true;
        m.normal.value=0xFF123456;m.enchanted.value=0xFFABCDEF;
        OldParticle old=new OldParticle();NewParticle next=new NewParticle();OfficialParticle official=new OfficialParticle();
        CombatColours.particle(old,false);CombatColours.particle(next,true);CombatColours.particle(official,false);
        assertEquals(0x12/255f,old.r);assertEquals(0x34/255f,old.g);assertEquals(0x56/255f,old.b);
        assertEquals(0xAB/255f,next.r);assertEquals(0xCD/255f,next.g);assertEquals(0xEF/255f,next.b);
        assertEquals(old.r,official.r);assertEquals(old.g,official.g);assertEquals(old.b,official.b);
    }
    @Test void disabledAndNullParticlesAreNoOps() {
        CriticalColours m = new CriticalColours();OldParticle p=new OldParticle();p.r=0.25f;
        CombatColours.particle(p,false);assertEquals(0.25f,p.r);assertDoesNotThrow(()->CombatColours.particle(null,false));
    }
    @Test void watermarkHasQuietDefaultsAndNoUnusedStyleSetting() {
        WatermarkModule m=new WatermarkModule();assertTrue(m.enabled);assertEquals(1f,m.fx);assertEquals(0f,m.fy);
        assertEquals(55f,m.strength.value);assertFalse(m.allSettings().contains(m.style));
    }
}
