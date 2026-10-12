package io.github.adytech99.healthindicators;

import io.github.adytech99.healthindicators.commands.ModCommands;
import io.github.adytech99.healthindicators.config.Config;
import io.github.adytech99.healthindicators.config.ModConfig;
import io.github.adytech99.healthindicators.enums.MessageTypeEnum;
import io.github.adytech99.healthindicators.util.ConfigUtils;
import io.github.adytech99.healthindicators.util.Maths;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

@Environment(EnvType.CLIENT)
public class HealthIndicatorsMod implements ClientModInitializer {
    public static final String MOD_ID = "healthindicators";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final String CONFIG_FILE = "healthindicators.json";

    public static final KeyBinding RENDERING_ENABLED = registerKeyBinding(new KeyBinding(
            "key." + MOD_ID + ".renderingEnabled",
            InputUtil.GLFW_KEY_LEFT,
            "key.categories." + MOD_ID
    ));

    public static final KeyBinding ARMOR_RENDERING_ENABLED = registerKeyBinding(new KeyBinding(
            "key." + MOD_ID + ".armorRenderingEnabled",
            InputUtil.GLFW_KEY_RIGHT_SHIFT,
            "key.categories." + MOD_ID
    ));

    public static final KeyBinding OVERRIDE_ALL_FILTERS = registerKeyBinding(new KeyBinding(
            "key." + MOD_ID + ".overrideAllFilters",
            InputUtil.GLFW_KEY_RIGHT,
            "key.categories." + MOD_ID
    ));
    public static final KeyBinding INCREASE_HEART_OFFSET = registerKeyBinding(new KeyBinding(
            "key." + MOD_ID + ".increaseHeartOffset",
            InputUtil.GLFW_KEY_UP,
            "key.categories." + MOD_ID
    ));
    public static final KeyBinding DECREASE_HEART_OFFSET = registerKeyBinding(new KeyBinding(
            "key." + MOD_ID + ".decreaseHeartOffset",
            InputUtil.GLFW_KEY_DOWN,
            "key.categories." + MOD_ID
    ));

    public static final KeyBinding OPEN_MOD_MENU_CONFIG = registerKeyBinding(new KeyBinding(
            "key." + MOD_ID + ".openModMenuConfig",
            InputUtil.GLFW_KEY_I,
            "key.categories." + MOD_ID
    ));

    private static final HealthIndicatorsMod INSTANCE = new HealthIndicatorsMod();
    private boolean changed = false;
    private static boolean openConfig = false;

    public static void openConfig(MinecraftClient client){
        openConfig = client != null && client.world != null;
    }

    public static void init() {
        INSTANCE.onInitializeClient();
    }

    @Override
    public void onInitializeClient() {
        try {
            ModConfig.HANDLER.load();
        } catch (Throwable ignored) {
        }
        Config.load();
        try {
            if (ModConfig.HANDLER.instance().enable_commands) ModCommands.registerCommands();
        } catch (Throwable ignored) {
        }
        registerTickEvents();
        registerShutdownHook();
        LOGGER.info("Never be heartless! Health Indicators loaded.");
    }

    private static KeyBinding registerKeyBinding(KeyBinding keyBinding) {
        try {
            Class<?> helper = Class.forName("net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper");
            for (Method m : helper.getMethods()) {
                if ("registerKeyBinding".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.invoke(null, keyBinding);
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        return keyBinding;
    }

    private static void registerTickEvents() {
        try {
            Class<?> cbClass = Class.forName("net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents");
            Object endTickEvent = cbClass.getField("END_CLIENT_TICK").get(null);
            Class<?> endTickInterface = Class.forName("net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents$EndTick");
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                HealthIndicatorsMod.class.getClassLoader(),
                new Class<?>[] { endTickInterface },
                (p, method, args) -> {
                    if ("onEndTick".equals(method.getName()) && args.length >= 1) {
                        onClientTick((MinecraftClient) args[0]);
                    }
                    return null;
                }
            );
            for (Method m : endTickEvent.getClass().getMethods()) {
                if ("register".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.invoke(endTickEvent, proxy);
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void registerShutdownHook() {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(HealthIndicatorsMod::saveModConfig));
        } catch (Throwable ignored) {
        }
    }

    public static void onClientTick(MinecraftClient client) {
        if (client == null) return;
        RenderTracker.tick(client);

        if (openConfig) {
            try {
                Screen configScreen = ModConfig.createScreen(client.currentScreen);
                if (configScreen != null) {
                    client.setScreen(configScreen);
                }
            } catch (Throwable ignored) {
            }
            openConfig = false;
        }

        while (RENDERING_ENABLED.wasPressed()) {
            Config.setRenderingEnabled(!Config.getHeartsRenderingEnabled());
            if (client.player != null) {
                Formatting formatting;
                if (ModConfig.HANDLER.instance().colored_messages) formatting = Config.getHeartsRenderingEnabled() ? Formatting.GREEN : Formatting.RED;
                else formatting = Formatting.WHITE;
                ConfigUtils.sendMessage(client.player, Text.literal((Config.getHeartsRenderingEnabled() ? "Enabled" : "Disabled") + " Health Indicators").formatted(formatting));
            }
        }

        while (ARMOR_RENDERING_ENABLED.wasPressed()) {
            Config.setArmorRenderingEnabled(!Config.getArmorRenderingEnabled());
            if (client.player != null) {
                Formatting formatting;
                if (ModConfig.HANDLER.instance().colored_messages) formatting = Config.getArmorRenderingEnabled() ? Formatting.GREEN : Formatting.RED;
                else formatting = Formatting.WHITE;
                ConfigUtils.sendMessage(client.player, Text.literal((Config.getArmorRenderingEnabled() ? "Enabled" : "Disabled") + " Armor Indicators").formatted(formatting));
            }
        }

        while (INCREASE_HEART_OFFSET.wasPressed()) {
            ModConfig.HANDLER.instance().display_offset = (ModConfig.HANDLER.instance().display_offset + ModConfig.HANDLER.instance().offset_step_size);
            INSTANCE.changed = true;
            if (client.player != null) {
                ConfigUtils.sendMessage(client.player, Text.literal("Set heart offset to " + Maths.truncate(ModConfig.HANDLER.instance().display_offset, 2)));
            }
        }

        while (DECREASE_HEART_OFFSET.wasPressed()) {
            ModConfig.HANDLER.instance().display_offset = (ModConfig.HANDLER.instance().display_offset - ModConfig.HANDLER.instance().offset_step_size);
            INSTANCE.changed = true;
            if (client.player != null) {
                ConfigUtils.sendMessage(client.player, Text.literal("Set heart offset to " + Maths.truncate(ModConfig.HANDLER.instance().display_offset, 2)));
            }
        }

        if (OVERRIDE_ALL_FILTERS.isPressed()) {
            Config.setOverrideAllFiltersEnabled(true);
            if (client.player != null) {
                ConfigUtils.sendOverlayMessage(client.player, Text.literal(" Config Criteria " + (Config.getOverrideAllFiltersEnabled() ? "Temporarily Overridden" : "Re-implemented")));
            }
        } else if (Config.getOverrideAllFiltersEnabled()) {
            Config.setOverrideAllFiltersEnabled(false);
            if (client.inGameHud != null) {
                client.inGameHud.setOverlayMessage(Text.literal(""), false);
            }
        }

        if (OPEN_MOD_MENU_CONFIG.isPressed()) {
            openConfig(client);
        }

        if (client.world != null && INSTANCE.changed && client.world.getTime() % 200 == 0) {
            saveModConfig();
            INSTANCE.changed = false;
        }
    }

    public static void saveModConfig(){
        try {
            ModConfig.HANDLER.save();
        } catch (Throwable ignored) {
        }
        Config.save();
    }
}
