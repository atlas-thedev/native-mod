package xyz.nativelaunch.ui.mod;

import org.junit.jupiter.api.Test;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.gfx.Atlas;
import xyz.nativelaunch.ui.gfx.Canvas;
import static org.junit.jupiter.api.Assertions.*;

class CrosshairModuleTest {
    private Canvas paint(CrosshairModule m, Game g, boolean editing, float scale) {
        Canvas c = new Canvas(new Atlas(256), null);
        c.begin(1280,720,scale);
        Ui ui = new Ui(c); ui.now = 1000;
        m.overlay(ui,g,editing);
        return c;
    }
    private boolean colour(Canvas c, int rgb) {
        for(int i=0;i<c.quads*4;i++) if(c.col[i]==rgb) return true;
        return false;
    }
    @Test void chosenColourIsNotSilentlyReplacedOverEntities() {
        CrosshairModule m = new CrosshairModule();m.color.value=0xFF00FF00;
        Game g=new Game();g.hudAllowed=true;g.hasTarget=true;
        assertFalse(m.onTarget.value);
        assertTrue(colour(paint(m,g,false,1),0xFF00FF00));
        m.onTarget.value=true;m.targetColor.value=0xFFFF00FF;
        assertTrue(colour(paint(m,g,false,1),0xFFFF00FF));
        assertTrue(colour(paint(m,g,true,1),0xFF00FF00));
    }
    @Test void allFiveShapesDrawCentrallyAtMultipleUiScales() {
        CrosshairModule m=new CrosshairModule();Game g=new Game();g.hudAllowed=true;
        for(int shape=0;shape<5;shape++) for(float scale:new float[]{0.75f,1f,1.5f}) {
            m.shape.value=shape;Canvas c=paint(m,g,false,scale);assertTrue(c.quads>0);
            for(int i=0;i<c.quads;i++) {
                float x0=c.geo[i*8],y0=c.geo[i*8+1],x1=c.geo[i*8+2],y1=c.geo[i*8+3];
                assertTrue(x0>=580&&x1<=700&&y0>=300&&y1<=420,"crosshair left the framebuffer centre");
            }
        }
    }
    @Test void everyCustomControlIsEditableAndRoundTrips() {
        CrosshairModule m=new CrosshairModule();
        assertEquals(13,m.allSettings().size());
        for(Setting s:m.allSettings()) {
            if(s instanceof Setting.Num)((Setting.Num)s).set(((Setting.Num)s).value+((Setting.Num)s).step);
            if(s instanceof Setting.Bool)((Setting.Bool)s).value=!((Setting.Bool)s).value;
            if(s instanceof Setting.Color)((Setting.Color)s).value=0xFF123456;
            com.google.gson.JsonElement saved=s.save();s.reset();s.load(saved);assertEquals(saved,s.save(),s.id);
        }
        m.onTarget.value=false;assertFalse(m.shownSettings().contains(m.targetColor));
        m.onTarget.value=true;assertTrue(m.shownSettings().contains(m.targetColor));
    }
    @Test void disablingRestoresVanillaImmediately() {
        CrosshairModule m=new CrosshairModule();Game g=new Game();g.hudAllowed=true;
        m.setEnabled(true);m.frame(g);assertTrue(Overlays.hideCrosshair());
        m.setEnabled(false);assertFalse(Overlays.hideCrosshair());
        m.setEnabled(true);m.frame(g);m.hide.value=false;m.frame(g);assertFalse(Overlays.hideCrosshair());
        assertTrue(m.overlays(g));g.hudAllowed=false;assertFalse(m.overlays(g));
    }
}
