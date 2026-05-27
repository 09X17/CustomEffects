package com.customeffects.utils;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.customeffects.CustomEffects;

import me.clip.placeholderapi.PlaceholderAPI;

public class PlaceholderResolver {
    private static final Pattern PAPI_PATTERN = Pattern.compile("%([^%]+)%");
    private static final Pattern EFFECT_PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    private final CustomEffects plugin;

    public PlaceholderResolver(CustomEffects plugin) {
        this.plugin = plugin;
    }

    public String resolvePlaceholders(String text, Player player) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String result = text;

        result = resolveEffectPlaceholders(result, player);

        if (plugin.getConfig().getBoolean("chat.placeholder-expansion.parse-papi-in-format", true)
                && plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            result = PlaceholderAPI.setPlaceholders(player, result);
        }

        result = result.replace("{player}", player.getDisplayName())
                       .replace("{player_name}", player.getName());

        return result;
    }

    public String resolveEffectPlaceholders(String text, Player player) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        UUID uuid = player.getUniqueId();
        String effectId = plugin.getDatabase().getEffect(uuid);
        String hex = plugin.getDatabase().getHex(uuid);

        String effectName = getEffectName(effectId);
        String effectPreview = getEffectPreview(effectId);
        String effectStatus = effectId != null && !effectId.isEmpty() ? "EQUIPPED" : "NONE";

        String result = text;
        result = result.replace("{effect_id}", effectId != null && !effectId.isEmpty() ? effectId : "NONE");
        result = result.replace("{effect_name}", effectName);
        result = result.replace("{effect_preview}", effectPreview);
        result = result.replace("{effect_hex}", hex != null && !hex.isEmpty() ? hex : "#FFFFFF");
        result = result.replace("{effect_status}", effectStatus);

        return result;
    }

    public String resolvePrefix(Player player) {
        String effectId = plugin.getDatabase().getEffect(player.getUniqueId());
        String effectConfigPath = "chat.effects.per-effect-formats." + effectId;

        if (plugin.getConfig().contains(effectConfigPath + ".prefix")) {
            String prefix = plugin.getConfig().getString(effectConfigPath + ".prefix", "");
            return resolvePlaceholders(prefix, player);
        }

        if (!plugin.getConfig().getBoolean("chat.prefix.enabled", true)) {
            return "";
        }

        String format = plugin.getConfig().getString("chat.prefix.format", "[{effect_name}]");
        if (effectId == null || effectId.isEmpty()) {
            return ColorUtils.translate(plugin.getConfig().getString("chat.prefix.no-effect", ""));
        }

        return resolvePlaceholders(format, player);
    }

    public String resolveSuffix(Player player) {
        String effectId = plugin.getDatabase().getEffect(player.getUniqueId());
        String effectConfigPath = "chat.effects.per-effect-formats." + effectId;

        if (plugin.getConfig().contains(effectConfigPath + ".suffix")) {
            String suffix = plugin.getConfig().getString(effectConfigPath + ".suffix", "");
            return resolvePlaceholders(suffix, player);
        }

        if (!plugin.getConfig().getBoolean("chat.suffix.enabled", false)) {
            return "";
        }

        String format = plugin.getConfig().getString("chat.suffix.format", "");
        if (effectId == null || effectId.isEmpty()) {
            return ColorUtils.translate(plugin.getConfig().getString("chat.suffix.no-effect", ""));
        }

        return resolvePlaceholders(format, player);
    }

    public String resolveFormat(Player player, String message) {
        String effectId = plugin.getDatabase().getEffect(player.getUniqueId());
        String effectConfigPath = "chat.effects.per-effect-formats." + effectId;

        if (plugin.getConfig().contains(effectConfigPath + ".format")) {
            String format = plugin.getConfig().getString(effectConfigPath + ".format", "");
            String resolved = resolvePlaceholders(format, player);
            return resolved.replace("{message}", message);
        }

        String format = plugin.getConfig().getString("chat.format", "{prefix} {player} &7» {message}");
        String resolved = resolvePlaceholders(format, player);
        return resolved.replace("{message}", message);
    }

    private String getEffectName(String effectId) {
        if (effectId == null || effectId.isEmpty()) {
            return ColorUtils.translate(plugin.getConfig().getString("messages.no-effect-name", "&cNinguno"));
        }
        return ColorUtils.translate(plugin.getEffectDisplay(effectId));
    }

    private String getEffectPreview(String effectId) {
        if (effectId == null || effectId.isEmpty()) {
            return ColorUtils.translate(plugin.getConfig().getString("messages.no-effect-preview", "&cSin Efecto"));
        }

        var categoriesSection = plugin.getConfig().getConfigurationSection("main-menu.categories");
        if (categoriesSection != null) {
            for (String category : categoriesSection.getKeys(false)) {
                var categoryConfig = plugin.getCategoryConfig(category);
                if (categoryConfig != null && categoryConfig.contains(effectId)) {
                    String preview = categoryConfig.getString(effectId + ".preview", effectId);
                    return ColorUtils.translate(preview);
                }
            }
        }
        return effectId;
    }

    public boolean hasPerEffectFormat(String effectId) {
        return effectId != null && plugin.getConfig().contains("chat.effects.per-effect-formats." + effectId);
    }

    public boolean shouldOverrideFormat(Player player) {
        if (player.hasPermission(plugin.getConfig().getString("chat.effects.bypass-permission", "effectos.chat.bypass"))) {
            return false;
        }
        return plugin.getConfig().getBoolean("chat.effects.override-vault-format", true);
    }
}