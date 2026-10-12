package io.github.adytech99.healthindicators.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.adytech99.healthindicators.HealthIndicatorsMod;
import io.github.adytech99.healthindicators.config.ModConfig;
import io.github.adytech99.healthindicators.enums.HealthDisplayTypeEnum;
import io.github.adytech99.healthindicators.util.ConfigUtils;
import io.github.adytech99.healthindicators.util.Maths;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import java.lang.reflect.Method;

public class ModCommands {
    public static void registerCommands() {
        try {
            Class<?> cbClass = Class.forName("net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback");
            Object event = cbClass.getField("EVENT").get(null);
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                ModCommands.class.getClassLoader(),
                new Class<?>[] { cbClass },
                (p, method, args) -> {
                    if ("register".equals(method.getName()) && args.length >= 1) {
                        registerCommandsOnDispatcher(args[0]);
                    }
                    return null;
                }
            );
            for (Method m : event.getClass().getMethods()) {
                if ("register".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.invoke(event, proxy);
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerCommandsOnDispatcher(Object dispatcherObj) {
        try {
            Class<?> ccm = Class.forName("net.fabricmc.fabric.api.client.command.v2.ClientCommandManager");
            Method literalMethod = ccm.getMethod("literal", String.class);
            Method argumentMethod = ccm.getMethod("argument", String.class, com.mojang.brigadier.arguments.ArgumentType.class);

            Object root = literalMethod.invoke(null, "healthindicators");

            // /healthindicators offset <double>
            Object offsetLit = literalMethod.invoke(null, "offset");
            Object offsetArg = argumentMethod.invoke(null, "offset", DoubleArgumentType.doubleArg());
            Method executesMethod = offsetArg.getClass().getMethod("executes", com.mojang.brigadier.Command.class);
            executesMethod.invoke(offsetArg, (com.mojang.brigadier.Command<Object>) context -> {
                double val = DoubleArgumentType.getDouble(context, "offset");
                ModConfig.HANDLER.instance().display_offset = val;
                ModConfig.HANDLER.save();
                try {
                    Method getPlayer = context.getSource().getClass().getMethod("getPlayer");
                    Object player = getPlayer.invoke(context.getSource());
                    if (player instanceof ClientPlayerEntity clientPlayer) {
                        ConfigUtils.sendMessage(clientPlayer, Text.literal("Set heart offset to " + Maths.truncate(val, 2)));
                    }
                } catch (Throwable ignored) {}
                return 1;
            });
            Method thenMethod = offsetLit.getClass().getMethod("then", offsetArg.getClass().getSuperclass());
            thenMethod.invoke(offsetLit, offsetArg);
            thenMethod.invoke(root, offsetLit);

            // /healthindicators indicator-type <heart|number>
            Object typeLit = literalMethod.invoke(null, "indicator-type");
            Object typeArg = argumentMethod.invoke(null, "indicator_type", StringArgumentType.string());
            executesMethod.invoke(typeArg, (com.mojang.brigadier.Command<Object>) context -> {
                String str = StringArgumentType.getString(context, "indicator_type");
                HealthDisplayTypeEnum displayType;
                if ("heart".equalsIgnoreCase(str) || "hearts".equalsIgnoreCase(str)) {
                    displayType = HealthDisplayTypeEnum.HEARTS;
                } else if ("number".equalsIgnoreCase(str)) {
                    displayType = HealthDisplayTypeEnum.NUMBER;
                } else {
                    return 1;
                }
                ModConfig.HANDLER.instance().indicator_type = displayType;
                ModConfig.HANDLER.save();
                try {
                    Method getPlayer = context.getSource().getClass().getMethod("getPlayer");
                    Object player = getPlayer.invoke(context.getSource());
                    if (player instanceof ClientPlayerEntity clientPlayer) {
                        ConfigUtils.sendMessage(clientPlayer, Text.literal("Set display type to " + displayType));
                    }
                } catch (Throwable ignored) {}
                return 1;
            });
            thenMethod.invoke(typeLit, typeArg);
            thenMethod.invoke(root, typeLit);

            // /healthindicators (open config)
            executesMethod.invoke(root, (com.mojang.brigadier.Command<Object>) context -> {
                try {
                    Method getClient = context.getSource().getClass().getMethod("getClient");
                    Object client = getClient.invoke(context.getSource());
                    if (client instanceof MinecraftClient mc) {
                        HealthIndicatorsMod.openConfig(mc);
                    }
                } catch (Throwable ignored) {}
                return 1;
            });

            Method regMethod = dispatcherObj.getClass().getMethod("register", root.getClass().getSuperclass());
            regMethod.invoke(dispatcherObj, root);
        } catch (Throwable ignored) {
        }
    }
}
