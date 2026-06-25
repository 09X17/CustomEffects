package com.customeffects.placeholder;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.customeffects.CustomEffects;
import com.customeffects.utils.ColorUtils;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public class EffectosExpansion extends PlaceholderExpansion {
    private final CustomEffects plugin;

    public EffectosExpansion(CustomEffects plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "ampleffects";
    }

    @Override
    public @NotNull String getAuthor() {
        return "09X18";
    }

    @Override
    public @NotNull String getVersion() {
        return "2.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        UUID uuid = player.getUniqueId();
        if (this.plugin.getDatabase().getActiveEffect(uuid) == null) {
            this.plugin.getDatabase().loadPlayerData(uuid);
        }

        String effectId = this.plugin.getDatabase().getActiveEffect(uuid);
        String hex = this.plugin.getDatabase().getHex(uuid);
        String style = this.plugin.getDatabase().getStyle(uuid);

        switch (params.toLowerCase()) {
            case "hex":
                return hex != null && !hex.isEmpty() ? hex : "#FFFFFF";
            case "effect":
                return effectId != null && !effectId.isEmpty() ? effectId : "NONE";
            case "has_effect":
                return effectId != null && !effectId.isEmpty() ? "true" : "false";
            case "format":
                String s1 = ColorUtils.styleToLegacy(style != null ? style : "");
                return hex != null && !hex.isEmpty()
                        ? ColorUtils.translate(hex + s1 + player.getName())
                        : ColorUtils.translate("&f" + s1 + player.getName());
            case "styled":
                return ColorUtils.styleToLegacy(style != null ? style : "");
            case "minimessage":
            case "styled_minimessage":
                if (hex != null && !hex.isEmpty()) {
                    String cleanHex = hex.replace("§", "").replace("#", "").replace("&", "");
                    return ColorUtils.applyStyle("<#" + cleanHex + ">" + player.getName(), style != null ? style : "");
                }
                return ColorUtils.applyStyle("<white>" + player.getName(), style != null ? style : "");
            case "style":
                if (style == null || style.isEmpty()) return "Sin Formato";
                return switch (style) {
                    case "bold" -> "Bold";
                    case "underline" -> "Underline";
                    case "italic" -> "Italic";
                    case "bold_underline" -> "Bold+Underline";
                    case "bold_italic" -> "Bold+Italic";
                    case "underline_italic" -> "Underline+Italic";
                    case "bold_underline_italic" -> "Bold+Underline+Italic";
                    default -> style;
                };
            case "style_code":
                return ColorUtils.styleToLegacy(style != null ? style : "");
            case "name":
                if (effectId == null || effectId.isEmpty()) return "Ninguno";
                return ColorUtils.translate(this.plugin.getEffectDisplay(effectId));
            case "preview":
                return getEffectPreview(effectId);
            case "status":
                return effectId != null && !effectId.isEmpty() ? "EQUIPPED" : "NONE";
            case "hex_minimessage":
                if (hex != null && !hex.isEmpty()) {
                    return ColorUtils.hexToMiniMessage(hex);
                }
                return "<white>";
            case "decorated_name":
                String s2 = ColorUtils.styleToLegacy(style != null ? style : "");
                return hex != null && !hex.isEmpty()
                        ? ColorUtils.translate(hex + s2 + player.getName() + "&r")
                        : player.getName();
            default:
                return null;
        }
    }

    private String getEffectPreview(String effectId) {
        if (effectId == null || effectId.isEmpty()) {
            return "Sin Efecto";
        }
        var categoriesSection = this.plugin.getConfig().getConfigurationSection("main-menu.categories");
        if (categoriesSection != null) {
            for (String category : categoriesSection.getKeys(false)) {
                var categoryConfig = this.plugin.getCategoryConfig(category);
                if (categoryConfig != null && categoryConfig.contains(effectId)) {
                    String preview = categoryConfig.getString(effectId + ".preview", effectId);
                    return ColorUtils.translate(preview != null ? preview : effectId);
                }
            }
        }
        return effectId;
    }
}
