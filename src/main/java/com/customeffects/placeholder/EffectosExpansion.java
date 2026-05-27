package com.customeffects.placeholder;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
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

    public @NotNull String getIdentifier() {
        return "effectos";
    }

    public @NotNull String getAuthor() {
        return this.plugin.getDescription().getAuthors().isEmpty() ? "09X18" : (String)this.plugin.getDescription().getAuthors().get(0);
    }

    public @NotNull String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    public boolean persist() {
        return true;
    }

    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) {
            return "";
        }
        
        String effectId = this.plugin.getDatabase().getEffect(player.getUniqueId());
        String hex = this.plugin.getDatabase().getHex(player.getUniqueId());
        
        if (identifier.equalsIgnoreCase("hex")) {
            return hex != null && !hex.isEmpty() ? hex : "#FFFFFF";
        } else if (identifier.equalsIgnoreCase("effect")) {
            return effectId != null && !effectId.isEmpty() ? effectId : "NONE";
        } else if (identifier.equalsIgnoreCase("has_effect")) {
            return effectId != null && !effectId.isEmpty() ? "true" : "false";
        } else if (identifier.equalsIgnoreCase("format")) {
            return hex != null && !hex.isEmpty() ? ColorUtils.translate(hex + player.getName()) : ColorUtils.translate("&f" + player.getName());
        } else if (identifier.equalsIgnoreCase("minimessage")) {
            if (hex != null && !hex.isEmpty()) {
                String cleanHex = hex.replace("§", "").replace("#", "").replace("&", "");
                return "<#" + cleanHex + ">" + player.getName();
            } else {
                return "<white>" + player.getName();
            }
        } else if (identifier.equalsIgnoreCase("decorated_name")) {
            return hex != null && !hex.isEmpty() ? ColorUtils.translate(hex + player.getName() + "&r") : player.getName();
        } else if (identifier.equalsIgnoreCase("luckperms_prefix")) {
            String luckPrefix = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%luckperms_prefix%");
            if (luckPrefix == null || luckPrefix.isEmpty()) {
                return "";
            }
            if (hex != null && !hex.isEmpty()) {
                String cleanPrefix = stripAllFormatting(luckPrefix);
                return ColorUtils.translate(hex + cleanPrefix + "&r");
            }
            return luckPrefix;
        } else if (identifier.equalsIgnoreCase("name")) {
            return getEffectName(effectId);
        } else if (identifier.equalsIgnoreCase("preview")) {
            return getEffectPreview(effectId);
        } else if (identifier.equalsIgnoreCase("status")) {
            return effectId != null && !effectId.isEmpty() ? "EQUIPPED" : "NONE";
        } else if (identifier.equalsIgnoreCase("hex_minimessage")) {
            if (hex != null && !hex.isEmpty()) {
                return ColorUtils.hexToMiniMessage(hex);
            } else {
                return "<white>";
            }
        } else if (identifier.equalsIgnoreCase("effect_category")) {
            return this.getEffectCategory(effectId);
        } else if (identifier.equalsIgnoreCase("permission_node")) {
            return this.getEffectPermission(effectId);
        } else if (identifier.equalsIgnoreCase("has_permission")) {
            if (effectId != null && !effectId.isEmpty()) {
                String perm = this.getEffectPermission(effectId);
                if (perm != null && !perm.isEmpty()) {
                    return String.valueOf(player.hasPermission(perm));
                }
            }
            return "false";
        } else if (identifier.startsWith("other_")) {
            String[] parts = identifier.split("_", 3);
            if (parts.length >= 3) {
                String targetPlayerName = parts[1];
                String subIdentifier = parts[2];
                Player targetPlayer = this.plugin.getServer().getPlayerExact(targetPlayerName);
                if (targetPlayer != null) {
                    return this.onPlaceholderRequest(targetPlayer, subIdentifier);
                }
            }
        }

        return null;
    }

    private String getEffectName(String effectId) {
        if (effectId == null || effectId.isEmpty()) {
            return ColorUtils.translate(this.plugin.getConfig().getString("messages.no-effect-name", "&cNinguno"));
        }
        return ColorUtils.translate(this.plugin.getEffectDisplay(effectId));
    }

    private String getEffectPreview(String effectId) {
        if (effectId == null || effectId.isEmpty()) {
            return ColorUtils.translate(this.plugin.getConfig().getString("messages.no-effect-preview", "&cSin Efecto"));
        }

        ConfigurationSection categoriesSection = this.plugin.getConfig().getConfigurationSection("main-menu.categories");
        if (categoriesSection != null) {
            for (String category : categoriesSection.getKeys(false)) {
                YamlConfiguration categoryConfig = this.plugin.getCategoryConfig(category);
                if (categoryConfig != null && categoryConfig.contains(effectId)) {
                    String preview = categoryConfig.getString(effectId + ".preview", effectId);
                    return ColorUtils.translate(preview);
                }
            }
        }
        return effectId;
    }

    private String getEffectCategory(String effectId) {
        if (effectId == null || effectId.isEmpty()) {
            return "NONE";
        }

        ConfigurationSection categoriesSection = this.plugin.getConfig().getConfigurationSection("main-menu.categories");
        if (categoriesSection != null) {
            for (String category : categoriesSection.getKeys(false)) {
                YamlConfiguration categoryConfig = this.plugin.getCategoryConfig(category);
                if (categoryConfig != null && categoryConfig.contains(effectId)) {
                    return category;
                }
            }
        }
        return "UNKNOWN";
    }

    private String getEffectPermission(String effectId) {
        if (effectId == null || effectId.isEmpty()) {
            return "";
        }

        ConfigurationSection categoriesSection = this.plugin.getConfig().getConfigurationSection("main-menu.categories");
        if (categoriesSection != null) {
            for (String category : categoriesSection.getKeys(false)) {
                YamlConfiguration categoryConfig = this.plugin.getCategoryConfig(category);
                if (categoryConfig != null && categoryConfig.contains(effectId)) {
                    return categoryConfig.getString(effectId + ".permission", "");
                }
            }
        }
        return "";
    }

    private String stripAllFormatting(String text) {
        if (text == null) return "";
        text = text.replaceAll("<gradient:#[A-Fa-f0-9]{6}:(#[A-Fa-f0-9]{6})(?::(#[A-Fa-f0-9]{6}))?>", "");
        text = text.replaceAll("</gradient>", "");
        text = text.replaceAll("<gradient:[^>]*>", "");
        text = text.replaceAll("#[A-Fa-f0-9]{6}", "");
        text = text.replaceAll("&#([A-Fa-f0-9]{6})", "");
        text = text.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
        text = text.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
        text = text.replaceAll("§x(§[0-9a-fA-F]){6}", "");
        return text;
    }
}
