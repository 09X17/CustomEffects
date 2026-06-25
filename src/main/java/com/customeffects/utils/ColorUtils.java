package com.customeffects.utils;

import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class ColorUtils {
    
    private static final Pattern HEX_PATTERN = Pattern.compile("(?:&#|#)([0-9a-fA-F]{6})");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    @NotNull
    public static String translate(@NotNull String message) {
        if (message.isEmpty()) return "";
        return LEGACY_SERIALIZER.serialize(toComponent(message));
    }

    @NotNull
    public static Component toComponent(@NotNull String message) {
        if (message.isEmpty()) return Component.empty();
        
        String normalized = message.replace('§', '&');
   
        if (!normalized.contains("&") && normalized.contains("<")) {
            return MINI_MESSAGE.deserialize(normalized);
        }
        
        String converted = HEX_PATTERN.matcher(normalized).replaceAll("<#$1>");
        
        converted = convertLegacyToMiniMessage(converted);
        
        return MINI_MESSAGE.deserialize(converted);
    }
    
    private static String convertLegacyToMiniMessage(String text) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (text.charAt(i) == '<') {
                int closeIndex = text.indexOf('>', i);
                if (closeIndex != -1) {
                    String tag = text.substring(i, closeIndex + 1);
                    if (tag.startsWith("<glyph:") || tag.startsWith("<gradient:") || tag.startsWith("<#")) {
                        result.append(tag);
                        i = closeIndex + 1;
                        continue;
                    }
                }
            }
            if (i < text.length() - 1 && text.charAt(i) == '&') {
                char code = text.charAt(i + 1);
                String mmTag = getMiniMessageTag(code);
                if (mmTag != null) {
                    result.append("<").append(mmTag).append(">");
                    i += 2;
                    continue;
                }
            }
            result.append(text.charAt(i));
            i++;
        }
        return result.toString();
    }
    
    private static String getMiniMessageTag(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "black";
            case '1' -> "dark_blue";
            case '2' -> "dark_green";
            case '3' -> "dark_aqua";
            case '4' -> "dark_red";
            case '5' -> "dark_purple";
            case '6' -> "gold";
            case '7' -> "gray";
            case '8' -> "dark_gray";
            case '9' -> "blue";
            case 'a' -> "green";
            case 'b' -> "aqua";
            case 'c' -> "red";
            case 'd' -> "light_purple";
            case 'e' -> "yellow";
            case 'f' -> "white";
            case 'k' -> "obfuscated";
            case 'l' -> "bold";
            case 'm' -> "strikethrough";
            case 'n' -> "underlined";
            case 'o' -> "italic";
            case 'r' -> "reset";
            default -> null;
        };
    }
    @NotNull
    public static String hexToMiniMessage(@NotNull String hex) {
        String cleanHex = hex.replace("§", "").replace("#", "").replace("&", "");
        return "<#" + cleanHex + ">";
    }

    @NotNull
    public static String applyStyle(@NotNull String text, @NotNull String style) {
        if (style.isEmpty()) return text;

        StringBuilder openTags = new StringBuilder();
        StringBuilder closeTags = new StringBuilder();

        if (style.contains("bold")) {
            openTags.append("<bold>");
            closeTags.insert(0, "</bold>");
        }
        if (style.contains("underline")) {
            openTags.append("<underlined>");
            closeTags.insert(0, "</underlined>");
        }
        if (style.contains("italic")) {
            openTags.append("<italic>");
            closeTags.insert(0, "</italic>");
        }

        return openTags.toString() + text + closeTags.toString();
    }

    @NotNull
    public static String styleToLegacy(@NotNull String style) {
        if (style.isEmpty()) return "";

        StringBuilder codes = new StringBuilder();
        if (style.contains("bold")) codes.append("&l");
        if (style.contains("underline")) codes.append("&n");
        if (style.contains("italic")) codes.append("&o");
        return codes.toString();
    }
}