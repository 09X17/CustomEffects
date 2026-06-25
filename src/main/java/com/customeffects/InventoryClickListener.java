package com.customeffects;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import com.customeffects.utils.ColorUtils;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class InventoryClickListener implements Listener {
    private final CustomEffects plugin;

    public InventoryClickListener(CustomEffects plugin) {
        this.plugin = plugin;
    }

    private static String stripColors(String text) {
        if (text == null) return null;
        return PlainTextComponentSerializer.plainText().serialize(
            LegacyComponentSerializer.legacySection().deserialize(text)
        );
    }

    private Sound getSound(String configPath, Sound defaultSound) {
        String soundStr = this.plugin.getConfig().getString(configPath);
        if (soundStr != null && !soundStr.isEmpty()) {
            try {
                return Sound.valueOf(soundStr);
            } catch (IllegalArgumentException e) {
                this.plugin.getLogger().warning("Sonido inválido en config: " + configPath + " = " + soundStr);
            }
        }
        return defaultSound;
    }

    private void playSound(Player player, String configPath, Sound defaultSound, float volume, float pitch) {
        Sound sound = this.getSound(configPath, defaultSound);
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() != null) {
            String title = LegacyComponentSerializer.legacySection().serialize(event.getView().title());
            String mainTitle = this.plugin.getMainMenuTitle();
            boolean isMainMenu = title.equals(mainTitle);
            boolean isSubMenu = title.contains("Pág.");
            boolean isFormatMenu = title.contains("FORMATOS");

            if (isMainMenu || isSubMenu || isFormatMenu) {
                HumanEntity whoClicked = event.getWhoClicked();

                event.setCancelled(true);

                if (whoClicked instanceof Player) {
                    Player player = (Player) whoClicked;
                    ItemStack item = event.getCurrentItem();

                    if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
                        if (isMainMenu) {
                            NamespacedKey key = new NamespacedKey(this.plugin, "category_id");
                            String categoryId = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);

                            if (categoryId != null) {
                                this.playSound(player, "sounds.click", Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
                                final String finalCategoryId = categoryId;
                                Bukkit.getScheduler().runTask(this.plugin, () -> MenuCreator.openCategoryMenu(player, this.plugin, finalCategoryId, 0));
                            }
                        } else if (isFormatMenu) {
                            this.handleFormatMenuClick(player, item);
                        } else if (isSubMenu) {
                            this.handleSubMenuClick(player, title, event.getSlot(), item);
                        }
                    }
                }
            }
        }
    }

    private void handleFormatMenuClick(Player player, ItemStack clickedItem) {
        NamespacedKey styleKey = new NamespacedKey(this.plugin, "format_style");
        String style = clickedItem.getItemMeta().getPersistentDataContainer().get(styleKey, PersistentDataType.STRING);
        if (style == null) return;

        NamespacedKey effectKey = new NamespacedKey(this.plugin, "pending_effect");
        String pendingEffect = clickedItem.getItemMeta().getPersistentDataContainer().get(effectKey, PersistentDataType.STRING);

        NamespacedKey categoryKey = new NamespacedKey(this.plugin, "pending_category");
        String pendingCategory = clickedItem.getItemMeta().getPersistentDataContainer().get(categoryKey, PersistentDataType.STRING);

        UUID uuid = player.getUniqueId();
        ConfigurationSection config = this.plugin.getConfig();

        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            this.plugin.getDatabase().saveStyle(uuid, style);

            if (pendingEffect != null && !pendingEffect.isEmpty()) {
                YamlConfiguration categoryConfig = this.plugin.getCategoryConfig(pendingCategory);
                String hex = "#FFFFFF";
                if (categoryConfig != null) {
                    hex = categoryConfig.getString(pendingEffect + ".hex", "#FFFFFF");
                }
                this.plugin.getDatabase().saveEffect(uuid, pendingEffect, hex);
            }

            Bukkit.getScheduler().runTask(this.plugin, () -> {
                if (pendingEffect != null && !pendingEffect.isEmpty()) {
                    String effectDisplay = this.plugin.getEffectDisplay(pendingEffect, pendingCategory);
                    String successMsg = config.getString("messages.equip-success", "%effectos_prefix%&a¡Efecto &f{effect} &aequipado con éxito!")
                            .replace("{effect}", effectDisplay);
                    player.sendMessage(ColorUtils.translate(resolvePrefix(successMsg)));
                    this.playSound(player, "sounds.equip", Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
                } else {
                    if (style.isEmpty()) {
                        String msg = config.getString("format-menu.messages.style-reset", "%effectos_prefix%&cFormato eliminado.");
                        player.sendMessage(ColorUtils.translate(resolvePrefix(msg)));
                    } else {
                        String msg = config.getString("format-menu.messages.style-set", "%effectos_prefix%&aFormato cambiado a: &f{style}");
                        String displayStyle = getStyleDisplayName(style);
                        player.sendMessage(ColorUtils.translate(resolvePrefix(msg).replace("{style}", displayStyle)));
                    }
                    this.playSound(player, "format-menu.sounds.select", Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
                }
                player.closeInventory();
            });
        });
    }

    private String getStyleDisplayName(String style) {
        return switch (style) {
            case "bold" -> "Bold (Negrita)";
            case "underline" -> "Underline (Subrayado)";
            case "italic" -> "Italic (Cursiva)";
            case "bold_underline" -> "Bold + Underline";
            case "bold_italic" -> "Bold + Italic";
            case "underline_italic" -> "Underline + Italic";
            case "bold_underline_italic" -> "Bold + Underline + Italic";
            default -> "Sin Formato";
        };
    }

    private void handleSubMenuClick(Player player, String title, int slot, ItemStack clickedItem) {
        ConfigurationSection config = this.plugin.getConfig();
        UUID uuid = player.getUniqueId();
        String categoryId = null;
        ConfigurationSection categories = config.getConfigurationSection("main-menu.categories");
        
        if (categories != null) {
            for (String key : categories.getKeys(false)) {
                String display = ColorUtils.translate(config.getString("main-menu.categories." + key + ".display", key));
                String cleanDisplay = stripColors(display);
                String cleanTitle = stripColors(title);
                if (cleanTitle.contains(cleanDisplay) || cleanDisplay.contains(cleanTitle.split(" - ")[0])) {
                    categoryId = key;
                    break;
                }
            }
        }

        if (categoryId == null && categories != null) {
            categoryId = categories.getKeys(false).iterator().next();
        }

        if (categoryId != null) {
            int page = 0;

            try {
                if (title.contains("Pág. ")) {
                    String[] parts = title.split("Pág. ");
                    page = Integer.parseInt(parts[1].replaceAll("[^0-9]", "").trim()) - 1;
                }
            } catch (Exception var23) {
                page = 0;
            }

            final String finalCategoryId = categoryId;
            final int finalPage = page;

            int backSlot = config.getInt("navigation.back-main.slot", 45);
            int formatSlot = config.getInt("format-button.slot", 46);
            if (slot == backSlot) {
                this.playSound(player, "sounds.back", Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, 1.0F, 1.0F);
                Bukkit.getScheduler().runTask(this.plugin, () -> MenuCreator.openMainMenu(player, this.plugin));
            } else if (slot == formatSlot) {
                this.playSound(player, "sounds.click", Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
                Bukkit.getScheduler().runTask(this.plugin, () -> MenuCreator.openFormatMenu(player, this.plugin, null, finalCategoryId));
            } else {
                int resetSlot = config.getInt("reset-item.slot", 49);
                if (slot == resetSlot) {
                    Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
                        this.plugin.getDatabase().saveEffect(uuid, "", "");
                        Bukkit.getScheduler().runTask(this.plugin, () -> {
                            String msg = config.getString("messages.effect-reset-success", "%effectos_prefix%&cHas removido tu efecto activo.");
                            player.sendMessage(ColorUtils.translate(resolvePrefix(msg)));
                            this.playSound(player, "sounds.reset", Sound.BLOCK_NOTE_BLOCK_BASS, 1.0F, 0.6F);
                            MenuCreator.openCategoryMenu(player, this.plugin, finalCategoryId, finalPage);
                        });
                    });
                } else {
                    int prevSlot = config.getInt("navigation.previous-page.slot", 48);
                    if (slot == prevSlot && page > 0) {
                        this.playSound(player, "sounds.page-turn", Sound.ITEM_BOOK_PAGE_TURN, 1.0F, 1.0F);
                        int prevPage = page - 1;
                        Bukkit.getScheduler().runTask(this.plugin, () -> MenuCreator.openCategoryMenu(player, this.plugin, finalCategoryId, prevPage));
                    } else {
                        int nextSlot = config.getInt("navigation.next-page.slot", 50);
                        List<String> effectList = this.plugin.getEffectsByCategory(categoryId);
                        if (slot == nextSlot && (page + 1) * 45 < effectList.size()) {
                            this.playSound(player, "sounds.page-turn", Sound.ITEM_BOOK_PAGE_TURN, 1.0F, 1.0F);
                            int nextPage = page + 1;
                            Bukkit.getScheduler().runTask(this.plugin, () -> MenuCreator.openCategoryMenu(player, this.plugin, finalCategoryId, nextPage));
                        } else {
                            if (slot >= 0 && slot < 45) {
                                NamespacedKey nKey = new NamespacedKey(this.plugin, "effect_id");
                                String effectId = clickedItem.getItemMeta().getPersistentDataContainer().get(nKey, PersistentDataType.STRING);
                                if (effectId == null) {
                                    return;
                                }

                                String activeEffect = this.plugin.getDatabase().getActiveEffect(uuid);
                                if (effectId.equalsIgnoreCase(activeEffect)) {
                                    String msg = config.getString("messages.already-equipped-chat", "%effectos_prefix%&eYa tienes este efecto equipado.");
                                    player.sendMessage(ColorUtils.translate(resolvePrefix(msg)));
                                    this.playSound(player, "sounds.already-equipped", Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.0F);
                                    return;
                                }

                                YamlConfiguration categoryConfig = this.plugin.getCategoryConfig(finalCategoryId);
                                if (categoryConfig == null || !categoryConfig.contains(effectId)) {
                                    return;
                                }

                                String permission = categoryConfig.getString(effectId + ".permission", "");
                                if (!permission.isEmpty() && !player.hasPermission(permission)) {
                                    String msg = config.getString("messages.no-permission", "%effectos_prefix%&cNo tienes permiso.");
                                    player.sendMessage(ColorUtils.translate(resolvePrefix(msg)));
                                    this.playSound(player, "sounds.no-permission", Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                                    return;
                                }

                                this.playSound(player, "sounds.click", Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
                                Bukkit.getScheduler().runTask(this.plugin, () -> MenuCreator.openFormatMenu(player, this.plugin, effectId, finalCategoryId));
                            }
                        }
                    }
                }
            }
        }
    }

    private String resolvePrefix(String msg) {
        String prefix = this.plugin.getConfig().getString("prefix", "");
        return msg.replace("%effectos_prefix%", prefix);
    }
}