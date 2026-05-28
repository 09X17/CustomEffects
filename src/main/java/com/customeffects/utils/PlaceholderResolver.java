package com.customeffects.utils;

import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.customeffects.CustomEffects;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class PlaceholderResolver {
    private final CustomEffects plugin;

    public PlaceholderResolver(CustomEffects plugin) {
        this.plugin = plugin;
    }

    private String cfg(String path, String def) {
        String value = plugin.getConfig().getString(path, def);
        return value != null ? value : def;
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
        String displayName = PlainTextComponentSerializer.plainText()
                .serialize(player.displayName());
        result = result.replace("{player}", displayName)
                .replace("{player_name}", player.getName());
        return result;
    }

    public String resolveEffectPlaceholders(String text, Player player) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        UUID uuid = player.getUniqueId();
        String effectId = plugin.getDatabase().getActiveEffect(uuid);
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
        String effectId = plugin.getDatabase().getActiveEffect(player.getUniqueId());
        String effectConfigPath = "chat.effects.per-effect-formats." + effectId;

        if (plugin.getConfig().contains(effectConfigPath + ".prefix")) {
            String prefix = cfg(effectConfigPath + ".prefix", "");
            return resolvePlaceholders(prefix, player);
        }

        if (!plugin.getConfig().getBoolean("chat.prefix.enabled", true)) {
            return "";
        }

        if (effectId == null || effectId.isEmpty()) {
            return ColorUtils.translate(cfg("chat.prefix.no-effect", ""));
        }

        String format = cfg("chat.prefix.format", "[{effect_name}]");
        return resolvePlaceholders(format, player);
    }

    public String resolveSuffix(Player player) {
        String effectId = plugin.getDatabase().getActiveEffect(player.getUniqueId());
        String effectConfigPath = "chat.effects.per-effect-formats." + effectId;

        if (plugin.getConfig().contains(effectConfigPath + ".suffix")) {
            String suffix = cfg(effectConfigPath + ".suffix", "");
            return resolvePlaceholders(suffix, player);
        }

        if (!plugin.getConfig().getBoolean("chat.suffix.enabled", false)) {
            return "";
        }

        if (effectId == null || effectId.isEmpty()) {
            return ColorUtils.translate(cfg("chat.suffix.no-effect", ""));
        }

        String format = cfg("chat.suffix.format", "");
        return resolvePlaceholders(format, player);
    }

    public String resolveFormat(Player player, String message) {
        String effectId = plugin.getDatabase().getActiveEffect(player.getUniqueId());
        String effectConfigPath = "chat.effects.per-effect-formats." + effectId;

        if (plugin.getConfig().contains(effectConfigPath + ".format")) {
            String format = cfg(effectConfigPath + ".format", "");
            String resolved = resolvePlaceholders(format, player);
            return resolved.replace("{message}", message);
        }

        String format = cfg("chat.format", "{prefix} {player} &7» {message}");
        String resolved = resolvePlaceholders(format, player);
        return resolved.replace("{message}", message);
    }

    private String getEffectName(String effectId) {
        if (effectId == null || effectId.isEmpty()) {
            return ColorUtils.translate(cfg("messages.no-effect-name", "&cNinguno"));
        }
        String display = plugin.getEffectDisplay(effectId);
        return ColorUtils.translate(display != null ? display : effectId);
    }

    private String getEffectPreview(String effectId) {
        if (effectId == null || effectId.isEmpty()) {
            return ColorUtils.translate(cfg("messages.no-effect-preview", "&cSin Efecto"));
        }

        ConfigurationSection categoriesSection = plugin.getConfig()
                .getConfigurationSection("main-menu.categories");
        if (categoriesSection != null) {
            for (String category : categoriesSection.getKeys(false)) {
                var categoryConfig = plugin.getCategoryConfig(category);
                if (categoryConfig != null && categoryConfig.contains(effectId)) {
                    String preview = categoryConfig.getString(effectId + ".preview", effectId);
                    return ColorUtils.translate(preview != null ? preview : effectId);
                }
            }
        }
        return effectId;
    }

    public boolean hasPerEffectFormat(String effectId) {
        return effectId != null && plugin.getConfig()
                .contains("chat.effects.per-effect-formats." + effectId);
    }

    public boolean shouldOverrideFormat(Player player) {
        String permission = cfg("chat.effects.bypass-permission", "effectos.chat.bypass");

        if (player.hasPermission(permission)) {
            return false;
        }

        return plugin.getConfig().getBoolean("chat.effects.override-vault-format", true);
    }
}