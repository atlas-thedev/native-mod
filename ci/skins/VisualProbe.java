package sprobe;

import java.lang.reflect.*;
import java.util.*;

/** Checks the actual transformed client classes, not just plain-Java colour maths. No world/servers required. */
public final class VisualProbe {
    private static Class<?> load(String... names) throws Exception {
        for (String name : names) try { return Class.forName(name); } catch (ClassNotFoundException ignored) {}
        throw new ClassNotFoundException(Arrays.toString(names));
    }
    private static void check(boolean value, String what) {
        if (!value) throw new AssertionError(what);
    }
    private static Field field(Class<?> type, String... names) throws Exception {
        for (; type != null; type = type.getSuperclass()) for (String name : names)
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(Arrays.toString(names));
    }
    private static Object module(String id) throws Exception {
        Object m = Class.forName("xyz.nativelaunch.ui.mod.Modules").getMethod("get", String.class).invoke(null, id);
        check(m != null, "missing module " + id); return m;
    }
    private static void colour(Object m, String name, int colour) throws Exception {
        Object setting = field(m.getClass(),name).get(m);
        setting.getClass().getField("value").setInt(setting, colour);
    }
    private static void enabled(Object m, boolean on) throws Exception { m.getClass().getField("enabled").setBoolean(m, on); }
    private static void factory(boolean official, boolean enchanted) throws Exception {
        Class<?> f = load(official ? "net.minecraft.client.particle.CritParticle$" + (enchanted ? "MagicProvider" : "Provider")
                : "net.minecraft.class_657$" + (enchanted ? "class_659" : "class_658"));
        Constructor<?> ctor = f.getDeclaredConstructors()[0]; ctor.setAccessible(true);
        Class<?> spriteSet = ctor.getParameterTypes()[0];
        Object sprites = Proxy.newProxyInstance(spriteSet.getClassLoader(), new Class<?>[]{spriteSet}, (p, m, a) -> null);
        Object provider = ctor.newInstance(sprites);
        String method = official ? "createParticle" : enchanted ? "method_3014" : "method_3013";
        Method spawn = null;
        for (Method m : f.getDeclaredMethods()) if (m.getName().equals(method) && !m.isBridge()) spawn = m;
        check(spawn != null, "particle factory method missing");
        Object[] args = new Object[spawn.getParameterCount()]; Class<?>[] types = spawn.getParameterTypes();
        for (int i = 0; i < args.length; i++) {
            if (types[i] == double.class) args[i] = 0d;
            else if (i == args.length - 1 && i > 7) {
                for (Method m : types[i].getMethods()) if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0 && m.getReturnType() == types[i]) {
                    args[i] = m.invoke(null); break;
                }
            }
        }
        spawn.setAccessible(true); Object particle = spawn.invoke(provider, args);
        int rgb = enchanted ? 0xFFABCDEF : 0xFF123456;
        float red = field(particle.getClass(), "field_3861", "field_62633", "rCol").getFloat(particle);
        float green = field(particle.getClass(), "field_3842", "field_62634", "gCol").getFloat(particle);
        float blue = field(particle.getClass(), "field_3859", "field_62635", "bCol").getFloat(particle);
        check(Math.abs(red - ((rgb >>> 16) & 255) / 255f) < 0.001f && Math.abs(green - ((rgb >>> 8) & 255) / 255f) < 0.001f
                && Math.abs(blue - (rgb & 255) / 255f) < 0.001f, "factory did not apply selected colour");
    }
    public static void run(Object mc) throws Exception {
        boolean official = mc.getClass().getName().equals("net.minecraft.client.Minecraft");
        check(Class.forName("xyz.nativelaunch.ui.mod.Modules").getMethod("get", String.class).invoke(null, "health") == null, "Health Indicators still registered");
        check(VisualProbe.class.getResource("/assets/healthindicators/icon.png") == null, "legacy health assets still packaged");
        Object critical = module("critical-colours");
        boolean previous = critical.getClass().getField("enabled").getBoolean(critical);
        try { enabled(critical,true); colour(critical,"normal",0xFF123456);colour(critical,"enchanted",0xFFABCDEF);
            factory(official,false);factory(official,true);
        } finally { critical.getClass().getMethod("resetSettings").invoke(critical); enabled(critical,previous); }
        System.out.println("VPROBE critical factories OK");
        // Loading these classes also forces every version-specific mixin through the real transformer.
        Class<?> boxes = load(official ? "net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer" : "net.minecraft.class_12155",
                "net.minecraft.class_10932", "net.minecraft.class_898");
        boolean hooked=false;
        for (Method m : boxes.getDeclaredMethods()) if (m.getName().contains("nativeClient$")) hooked=true;
        check(hooked,"hitbox hook was not merged");
        if (boxes.getName().equals("net.minecraft.class_10932")) {
            Object hitbox=module("hitbox-colours"); enabled(hitbox,true);colour(hitbox,"body",0xFF123456);
            try {
                Object record=boxes.getConstructor(double.class,double.class,double.class,double.class,double.class,double.class,float.class,float.class,float.class)
                        .newInstance(0d,0d,0d,1d,1d,1d,1f,1f,1f);
                check(Math.abs(field(boxes,"comp_3858").getFloat(record)-0x12/255f)<0.001f,"queued hitbox colour not applied");
            } finally {hitbox.getClass().getMethod("resetSettings").invoke(hitbox);enabled(hitbox,false);}
        }
        // Force the 1.21.9/10 command renderer through the transformer as well.
        try { Class.forName("net.minecraft.class_11686"); } catch (ClassNotFoundException ignored) {}
        Object hitbox=module("hitbox-colours");
        boolean hitboxWasEnabled=hitbox.getClass().getField("enabled").getBoolean(hitbox);
        try {
            try { hitbox.getClass().getField("eye"); throw new AssertionError("obsolete eye colour option remains"); }
            catch (NoSuchFieldException expected) {}
            enabled(hitbox,true); colour(hitbox,"direction",0xFF123456);
            Method palette=Class.forName("xyz.nativelaunch.ui.mod.CombatColours").getMethod("hitbox",int.class);
            check(((Integer)palette.invoke(null,0xFF0000FF))==0xFF123456,"blue look direction not recoloured");
            check(((Integer)palette.invoke(null,0xFFFF0000))==0xFFFF0000,"red eye-height line changed");
        } finally { hitbox.getClass().getMethod("resetSettings").invoke(hitbox);enabled(hitbox,hitboxWasEnabled); }
        System.out.println("VPROBE hitbox transformer OK");
        Class<?> icons=Class.forName("xyz.nativelaunch.ui.gfx.ModuleIcons");
        List<?> modules=(List<?>)Class.forName("xyz.nativelaunch.ui.mod.Modules").getMethod("all").invoke(null);
        Set<Integer> glyphs=new HashSet<Integer>();
        for(Object m:modules) {
            int cp=m.getClass().getField("icon").getInt(m);
            check((Boolean)icons.getMethod("custom",int.class).invoke(null,cp),"module has a stock icon");
            check(glyphs.add(cp),"modules share an icon");
        }
        check(glyphs.size()==(Integer)icons.getMethod("count").invoke(null),"custom icon count mismatch");
        check(VisualProbe.class.getResource("/assets/native/ui/native-modules.ttf")!=null,"custom font missing");
        System.out.println("VPROBE custom module icons OK");
        Object crosshair=module("crosshair");
        boolean crosshairWasEnabled=crosshair.getClass().getField("enabled").getBoolean(crosshair);
        try {
            check(((List<?>)crosshair.getClass().getMethod("allSettings").invoke(crosshair)).size()==13,"crosshair controls remain fixed/hidden");
            colour(crosshair,"color",0xFF00FF00);
            Object target=field(crosshair.getClass(),"onTarget").get(crosshair);
            target.getClass().getField("value").setBoolean(target,false);
            Class<?> canvas=Class.forName("xyz.nativelaunch.ui.gfx.Canvas"),atlas=Class.forName("xyz.nativelaunch.ui.gfx.Atlas"),fonts=Class.forName("xyz.nativelaunch.ui.gfx.Fonts");
            Class<?> ui=Class.forName("xyz.nativelaunch.ui.Ui"),game=Class.forName("xyz.nativelaunch.ui.mod.Game"),mod=Class.forName("xyz.nativelaunch.ui.mod.Module");
            Object c=canvas.getConstructor(atlas,fonts).newInstance(atlas.getConstructor(int.class).newInstance(256),null);
            Object u=ui.getConstructor(canvas).newInstance(c),g=game.getConstructor().newInstance();
            game.getField("hudAllowed").setBoolean(g,true);game.getField("hasTarget").setBoolean(g,true);
            Object shape=field(crosshair.getClass(),"shape").get(crosshair);
            for(int i=0;i<5;i++) {
                shape.getClass().getField("value").setInt(shape,i);
                canvas.getMethod("begin",int.class,int.class,float.class).invoke(c,1280,720,1f);
                mod.getMethod("overlay",ui,game,boolean.class).invoke(crosshair,u,g,false);
                int n=canvas.getField("quads").getInt(c);check(n>0,"crosshair shape emitted no geometry");
                boolean green=false;int[] colours=(int[])canvas.getField("col").get(c);
                for(int q=0;q<n*4;q++) if(colours[q]==0xFF00FF00)green=true;
                check(green,"chosen crosshair colour was not retained over a target");
            }
            Class<?> hud=load(official ? "net.minecraft.client.gui.Hud" : "net.minecraft.class_329","net.minecraft.client.gui.Gui");
            boolean crosshairHook=false;
            for(Method m:hud.getDeclaredMethods()) if(m.getName().contains("nativeClient$crosshair"))crosshairHook=true;
            check(crosshairHook,"vanilla crosshair hide hook missing");
        } finally {
            crosshair.getClass().getMethod("resetSettings").invoke(crosshair);enabled(crosshair,crosshairWasEnabled);
            Class.forName("xyz.nativelaunch.ui.mod.Overlays").getMethod("releaseCrosshair").invoke(null);
        }
        System.out.println("VPROBE custom crosshair OK");
        Object win = mc.getClass().getMethod(official ? "getWindow" : "method_22683").invoke(mc);
        boolean title=false;
        for (Method m : win.getClass().getDeclaredMethods()) if (m.getName().contains("nativeClient$title") && m.getReturnType()==String.class) {
            m.setAccessible(true);title="Native Client".equals(m.invoke(win,"Minecraft"));
        }
        check(title,"window title hook missing"); check(module("watermark")!=null,"watermark missing");
        System.out.println("VPROBE branding OK");
    }
}
