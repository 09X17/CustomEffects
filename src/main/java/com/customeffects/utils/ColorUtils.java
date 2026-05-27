package com.customeffects.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;

public class ColorUtils {
    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    public ColorUtils() {
    }

    public static String translate(String message) {
        if (message == null) {
            return "";
        } else {
            for(Matcher matcher = HEX_PATTERN.matcher(message); matcher.find(); matcher = HEX_PATTERN.matcher(message)) {
                String color = message.substring(matcher.start(), matcher.end());
                message = message.replace(color, ChatColor.of(color).toString());
            }

            return ChatColor.translateAlternateColorCodes('&', message);
        }
    }

    public static String toMiniMessage(String hexColor, String text) {
        if (hexColor == null || hexColor.isEmpty()) {
            return text;
        }
        String cleanHex = hexColor.replace("§", "").replace("#", "").replace("&", "");
        return "<#" + cleanHex + ">" + text + "</#>";
    }

    public static String fromMiniMessage(String miniMessageText) {
        if (miniMessageText == null || miniMessageText.isEmpty()) {
            return "";
        }
        try {
            Component component = MINI_MESSAGE.deserialize(miniMessageText);
            return LEGACY_SERIALIZER.serialize(component);
        } catch (Exception e) {
            return translate(miniMessageText);
        }
    }

    public static Component toComponent(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        try {
            return MINI_MESSAGE.deserialize(message);
        } catch (Exception e) {
            String translated = translate(message);
            return LEGACY_SERIALIZER.deserialize(translated);
        }
    }

    public static String hexToMiniMessage(String hex) {
        if (hex == null || hex.isEmpty()) {
            return "<white>";
        }
        String cleanHex = hex.replace("§", "").replace("#", "").replace("&", "");
        return "<#" + cleanHex + ">";
    }
}