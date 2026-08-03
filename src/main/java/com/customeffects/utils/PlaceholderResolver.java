package com.customeffects.utils;

import java.util.UUID;

import org.bukkit.entity.Player;

import com.customeffects.CustomEffects;
import com.customeffects.models.Category;
import com.customeffects.models.Effect;
import com.customeffects.services.DataManager;
import com.customeffects.services.VoucherService;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class PlaceholderResolver {
    private final CustomEffects plugin;
    private final DataManager dataManager;
    private final VoucherService voucherService;

    public PlaceholderResolver(CustomEffects plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getDataManager();
        this.voucherService = plugin.getVoucherService();
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
        String style = plugin.getDatabase().getStyle(uuid);
        String prefix = plugin.getDatabase().getPrefix(uuid);

        String effectName = getEffectName(effectId);
        String effectPreview = getEffectPreview(effectId);
        String effectStatus = effectId != null && !effectId.isEmpty() ? "EQUIPPED" : "NONE";
        String styleName = getStyleName(style);

        String result = text;
        result = result.replace("{effect_id}", effectId != null && !effectId.isEmpty() ? effectId : "NONE");
        result = result.replace("{effect_name}", effectName);
        result = result.replace("{effect_preview}", effectPreview);
        result = result.replace("{effect_hex}", hex != null && !hex.isEmpty() ? hex : "#FFFFFF");
        result = result.replace("{effect_status}", effectStatus);
        result = result.replace("{style}", styleName);
        result = result.replace("{style_code}", ColorUtils.styleToLegacy(style != null ? style : ""));
        result = result.replace("{prefix}", prefix != null ? prefix : "");

        return result;
    }

    public String resolvePrefix(Player player) {
        UUID uuid = player.getUniqueId();
        String dbPrefix = plugin.getDatabase().getPrefix(uuid);

        if (dbPrefix != null && !dbPrefix.isEmpty()) {
            String translated = ColorUtils.translate(dbPrefix);
            return translated + " "; 
        }

        if (plugin.getConfig().getBoolean("prefix-system.use-luckperms-default", true)) {
            String luckpermsPrefix = getLuckPermsPrefix(player);
            if (luckpermsPrefix != null && !luckpermsPrefix.isEmpty()) {
                return ColorUtils.translate(luckpermsPrefix);
            }
        }

        String effectId = plugin.getDatabase().getActiveEffect(uuid);
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

    private String getLuckPermsPrefix(Player player) {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
                return "";
            }
            net.luckperms.api.LuckPerms luckPerms = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                return "";
            }
            net.luckperms.api.node.types.MetaNode metaNode = user.getNodes().stream()
                    .filter(node -> node instanceof net.luckperms.api.node.types.MetaNode)
                    .map(node -> (net.luckperms.api.node.types.MetaNode) node)
                    .filter(node -> node.getMetaKey().equals("prefix"))
                    .findFirst()
                    .orElse(null);
            if (metaNode != null && metaNode.getMetaValue() != null) {
                return metaNode.getMetaValue();
            }
        } catch (Exception ignored) {
        }
        return "";
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
        String display = voucherService.getEffectDisplay(effectId);
        return ColorUtils.translate(display != null ? display : effectId);
    }

    private String getEffectPreview(String effectId) {
        if (effectId == null || effectId.isEmpty()) {
            return ColorUtils.translate(cfg("messages.no-effect-preview", "&cSin Efecto"));
        }

        for (Category category : dataManager.getAllCategories()) {
            Effect effect = category.getEffectById(effectId);
            if (effect != null) {
                return ColorUtils.translate(effect.getPreview());
            }
        }
        return effectId;
    }

    private String getStyleName(String style) {
        if (style == null || style.isEmpty())
            return "Sin Formato";
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