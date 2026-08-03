package com.customeffects.placeholder;

import com.customeffects.CustomEffects;
import com.customeffects.models.Category;
import com.customeffects.models.Effect;
import com.customeffects.services.DataManager;
import com.customeffects.services.VoucherService;
import com.customeffects.utils.ColorUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class EffectosExpansion extends PlaceholderExpansion {
    private final CustomEffects plugin;
    private final DataManager dataManager;
    private final VoucherService voucherService;

    public EffectosExpansion(CustomEffects plugin) {
        this.plugin = plugin;
        this.dataManager = plugin.getDataManager();
        this.voucherService = plugin.getVoucherService();
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
        if (plugin.getDatabase().getActiveEffect(uuid) == null
                && plugin.getDatabase().getPrefix(uuid) == null) {
            plugin.getDatabase().loadPlayerData(uuid);
        }

        String effectId = plugin.getDatabase().getActiveEffect(uuid);
        String hex = plugin.getDatabase().getHex(uuid);
        String style = plugin.getDatabase().getStyle(uuid);
        String prefix = plugin.getDatabase().getPrefix(uuid);

        return switch (params.toLowerCase()) {
            case "hex" -> hex != null && !hex.isEmpty() ? hex : "#FFFFFF";
            case "effect" -> effectId != null && !effectId.isEmpty() ? effectId : "NONE";
            case "has_effect" -> effectId != null && !effectId.isEmpty() ? "true" : "false";
            case "prefix" -> {
                // Verificar si el jugador tiene un prefix animado
                if (plugin.getAnimatedPrefixService().isAnimated(uuid)) {
                    Effect animatedEffect = plugin.getAnimatedPrefixService().getAnimatedEffect(uuid);
                    if (animatedEffect != null) {
                        int currentFrame = plugin.getAnimatedPrefixService().getCurrentFrame(uuid);
                        String framePrefix = animatedEffect.getPrefixFrame(currentFrame);
                        yield ColorUtils.translate(framePrefix);
                    }
                }
                if (prefix != null && !prefix.isEmpty()) {
                    yield ColorUtils.translate(prefix);
                }
                yield plugin.getLuckPermsPrefix(player);
            }
            case "has_prefix" -> {
                if (prefix != null && !prefix.isEmpty()) {
                    yield "true";
                }
                String luckPermsPrefix = plugin.getLuckPermsPrefix(player);
                yield luckPermsPrefix != null && !luckPermsPrefix.isEmpty() ? "true" : "false";
            }
            case "format" -> {
                String s1 = ColorUtils.styleToLegacy(style != null ? style : "");
                yield hex != null && !hex.isEmpty()
                        ? ColorUtils.translate(hex + s1 + player.getName())
                        : ColorUtils.translate("&f" + s1 + player.getName());
            }
            case "styled" -> ColorUtils.styleToLegacy(style != null ? style : "");
            case "minimessage", "styled_minimessage" -> {
                if (hex != null && !hex.isEmpty()) {
                    String cleanHex = hex.replace("§", "").replace("#", "").replace("&", "");
                    yield ColorUtils.applyStyle("<#" + cleanHex + ">" + player.getName(), style != null ? style : "");
                }
                yield ColorUtils.applyStyle("<white>" + player.getName(), style != null ? style : "");
            }
            case "style" -> {
                if (style == null || style.isEmpty()) yield "Sin Formato";
                yield switch (style) {
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
            case "style_code" -> ColorUtils.styleToLegacy(style != null ? style : "");
            case "name" -> {
                if (effectId == null || effectId.isEmpty()) yield "Ninguno";
                yield voucherService.getEffectDisplay(effectId);
            }
            case "preview" -> getEffectPreview(effectId);
            case "status" -> effectId != null && !effectId.isEmpty() ? "EQUIPPED" : "NONE";
            case "hex_minimessage" -> {
                if (hex != null && !hex.isEmpty()) {
                    yield ColorUtils.hexToMiniMessage(hex);
                }
                yield "<white>";
            }
            case "decorated_name" -> {
                String s2 = ColorUtils.styleToLegacy(style != null ? style : "");
                yield hex != null && !hex.isEmpty()
                        ? ColorUtils.translate(hex + s2 + player.getName() + "&r")
                        : player.getName();
            }
            default -> null;
        };
    }

    private String getEffectPreview(String effectId) {
        if (effectId == null || effectId.isEmpty()) {
            return "Sin Efecto";
        }
        for (Category category : dataManager.getAllCategories()) {
            Effect effect = category.getEffectById(effectId);
            if (effect != null) {
                return ColorUtils.translate(effect.getPreview());
            }
        }
        return effectId;
    }

    private String getEffectName(String effectId) {
        if (effectId == null || effectId.isEmpty()) {
            return "Ninguno";
        }
        for (Category category : dataManager.getAllCategories()) {
            Effect effect = category.getEffectById(effectId);
            if (effect != null) {
                return ColorUtils.translate(effect.getDisplay());
            }
        }
        return effectId;
    }
}