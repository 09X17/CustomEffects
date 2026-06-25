package com.customeffects;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.customeffects.utils.ColorUtils;
import com.customeffects.utils.SkullUtils;

import net.kyori.adventure.text.Component;

public class MenuCreator {

    public MenuCreator() {
    }

    public static void openMainMenu(Player player, CustomEffects plugin) {
        int size = plugin.getMainMenuSize();
        Component title = ColorUtils.toComponent(plugin.getMainMenuTitle());
        Inventory inv = Bukkit.createInventory(null, size, title);
        ConfigurationSection categories = plugin.getConfig().getConfigurationSection("main-menu.categories");
        if (categories != null) {
            for (String key : categories.getKeys(false)) {
                String path = "main-menu.categories." + key + ".";
                if (!plugin.getConfig().getBoolean(path + "enable", true)) {
                    continue;
                }
                String materialStr = plugin.getConfig().getString(path + "material", "BOOK");
                if (materialStr == null) {
                    materialStr = "BOOK";
                }
                Material mat = Material.matchMaterial(materialStr);
                if (mat == null) {
                    mat = Material.BOOK;
                }

                ItemStack item = new ItemStack(mat);

                if (mat == Material.PLAYER_HEAD || mat == Material.PLAYER_WALL_HEAD) {
                    String skullValue = plugin.getConfig().getString(path + "skull-value", "");
                    if (skullValue != null && !skullValue.isBlank()) {
                        SkullUtils.applySkullTexture(item, skullValue);
                    }
                }

                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    String displayName = plugin.getConfig().getString(path + "display", key);
                    meta.displayName(ColorUtils.toComponent(displayName != null ? displayName : key));

                    if (plugin.getConfig().contains(path + "custom-model-data")) {
                        meta.setCustomModelData(plugin.getConfig().getInt(path + "custom-model-data"));
                    }

                    List<String> lore = plugin.getConfig().getStringList(path + "lore");
                    List<Component> translatedLore = lore.stream()
                            .map(ColorUtils::toComponent)
                            .collect(Collectors.toList());

                    NamespacedKey nKey = new NamespacedKey(plugin, "category_id");
                    meta.getPersistentDataContainer().set(nKey, PersistentDataType.STRING, key);
                    meta.lore(translatedLore);
                    item.setItemMeta(meta);
                }

                int slot = plugin.getConfig().getInt(path + "slot", 0);
                if (slot >= 0 && slot < size) {
                    inv.setItem(slot, item);
                }
            }
        }

        player.openInventory(inv);

    }

    public static void openCategoryMenu(Player player, CustomEffects plugin, String categoryId, int page) {
        ConfigurationSection config = plugin.getConfig();
        UUID uuid = player.getUniqueId();
        String activeEffect = plugin.getDatabase().getActiveEffect(uuid);
        String displayCategory = config.getString("main-menu.categories." + categoryId + ".display-submenu",
                config.getString("main-menu.categories." + categoryId + ".display", categoryId));
        Component menuTitle = ColorUtils.toComponent(displayCategory + " &8- Pág. " + (page + 1));
        int size = plugin.getSubMenuSize();
        Inventory inv = Bukkit.createInventory(null, size, menuTitle);
        List<String> effects = plugin.getEffectsByCategory(categoryId);
        int start = page * 45;
        int end = Math.min(start + 45, effects.size());
        String equippedMsg = config.getString("messages.equipped", "&e&n✦ ᴇǫᴜɪᴘᴀᴅᴏ");
        String clickMsg = config.getString("messages.click-to-equip", "&a&n✔ ᴄʟɪᴄᴋ ᴘᴀʀᴀ ᴇǫᴜɪᴘᴀʀ");
        String lockedMsg = config.getString("messages.locked", "&c&n✘ ʙʟᴏǫᴜᴇᴀᴅᴏ");

        YamlConfiguration categoryConfig = plugin.getCategoryConfig(categoryId);
        List<String> defaultLore = plugin.getDefaultLore();
        int autoSlot = 0;

        for (int i = start; i < end; ++i) {
            String effectId = effects.get(i);

            if (categoryConfig == null || !categoryConfig.contains(effectId)) {
                continue;
            }

            if (!categoryConfig.getBoolean(effectId + ".enable", true)) {
                continue;
            }

            String materialStr = categoryConfig.getString(effectId + ".material", "PAPER");
            if (materialStr == null)
                materialStr = "PAPER";
            Material mat = Material.matchMaterial(materialStr);
            if (mat == null) {
                mat = Material.PAPER;
            }

            ItemStack item = new ItemStack(mat);

            if (mat == Material.PLAYER_HEAD || mat == Material.PLAYER_WALL_HEAD) {
                String skullValue = categoryConfig.getString(effectId + ".skull-value", "");
                if (skullValue != null && !skullValue.isEmpty()) {
                    SkullUtils.applySkullTexture(item, skullValue);
                }
            }

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String display = categoryConfig.getString(effectId + ".display");
                if (display == null) {
                    display = effectId;
                }
                meta.displayName(ColorUtils.toComponent(display));

                if (categoryConfig.contains(effectId + ".custom-model-data")) {
                    meta.setCustomModelData(categoryConfig.getInt(effectId + ".custom-model-data"));
                }

                String hex = categoryConfig.getString(effectId + ".hex", "#FFFFFF");
                String preview = categoryConfig.getString(effectId + ".preview", effectId);
                String currentStatus;
                if (effectId.equalsIgnoreCase(activeEffect)) {
                    currentStatus = equippedMsg;
                } else {
                    String perm = categoryConfig.getString(effectId + ".permission");
                    if (perm == null || perm.isEmpty()) {
                        currentStatus = clickMsg;
                    } else if (!player.hasPermission(perm)) {
                        currentStatus = lockedMsg;
                    } else {
                        currentStatus = clickMsg;
                    }
                }

                List<String> rawLore;
                if (categoryConfig.contains(effectId + ".lore")) {
                    rawLore = categoryConfig.getStringList(effectId + ".lore");
                } else {
                    rawLore = defaultLore;
                }

                List<Component> finalLore = new ArrayList<>();
                if (rawLore != null) {
                    for (String line : rawLore) {
                        String formattedLine = line.replace("{hex}", hex).replace("{preview}", preview)
                                .replace("{status}", currentStatus);
                        finalLore.add(ColorUtils.toComponent(formattedLine));
                    }
                }

                NamespacedKey nKey = new NamespacedKey(plugin, "effect_id");
                meta.getPersistentDataContainer().set(nKey, PersistentDataType.STRING, effectId);
                meta.lore(finalLore);
                item.setItemMeta(meta);
            }

            int targetSlot;
            if (categoryConfig.contains(effectId + ".slot")) {
                targetSlot = categoryConfig.getInt(effectId + ".slot");
            } else {
                targetSlot = autoSlot++;
            }

            if (targetSlot >= 0 && targetSlot < 45) {
                inv.setItem(targetSlot, item);
            }
        }

        inv.setItem(config.getInt("navigation.back-main.slot", 45),
                createNavigationItem(config, "navigation.back-main", -1, -1));
        inv.setItem(config.getInt("reset-item.slot", 49), createNavigationItem(config, "reset-item", -1, -1));

        int formatSlot = config.getInt("format-button.slot", 46);
        String formatMatStr = config.getString("format-button.material", "PAPER");
        Material formatMat = Material.matchMaterial(formatMatStr);
        if (formatMat == null) formatMat = Material.PAPER;
        ItemStack formatItem = new ItemStack(formatMat);
        ItemMeta formatMeta = formatItem.getItemMeta();
        if (formatMeta != null) {
            String formatDisplay = config.getString("format-button.display", "&b✦ ғᴏʀᴍᴀᴛᴏ ᴅᴇ ᴛᴇxᴛᴏ");
            formatMeta.displayName(ColorUtils.toComponent(formatDisplay));
            if (config.contains("format-button.custom-model-data")) {
                formatMeta.setCustomModelData(config.getInt("format-button.custom-model-data"));
            }
            List<String> formatLore = config.getStringList("format-button.lore");
            List<Component> formatTranslatedLore = new ArrayList<>();
            for (String line : formatLore) {
                formatTranslatedLore.add(ColorUtils.toComponent(line));
            }
            formatMeta.lore(formatTranslatedLore);
            NamespacedKey formatKey = new NamespacedKey(plugin, "open_format");
            formatMeta.getPersistentDataContainer().set(formatKey, PersistentDataType.STRING, categoryId);
            formatItem.setItemMeta(formatMeta);
        }
        if (formatSlot >= 0 && formatSlot < size) {
            inv.setItem(formatSlot, formatItem);
        }
        if (page > 0) {
            inv.setItem(config.getInt("navigation.previous-page.slot", 48),
                    createNavigationItem(config, "navigation.previous-page", -1, page));
        }

        if (end < effects.size()) {
            inv.setItem(config.getInt("navigation.next-page.slot", 50),
                    createNavigationItem(config, "navigation.next-page", page + 2, -1));
        }

        player.openInventory(inv);
    }

    @SuppressWarnings("null")
    public static void openFormatMenu(Player player, CustomEffects plugin, String effectId, String categoryId) {
        ConfigurationSection config = plugin.getConfig();
        String title = config.getString("format-menu.title", "&8&lFORMATOS &7- &fEstilo de Texto");
        int size = config.getInt("format-menu.size", 27);
        Component menuTitle = ColorUtils.toComponent(title);
        Inventory inv = Bukkit.createInventory(null, size, menuTitle);

        String currentStyle = plugin.getDatabase().getStyle(player.getUniqueId());
        ConfigurationSection items = config.getConfigurationSection("format-menu.items");

        if (items != null) {
            for (String key : items.getKeys(false)) {
                String path = "format-menu.items." + key + ".";
                String materialStr = config.getString(path + "material", "PAPER");
                if (materialStr == null) materialStr = "PAPER";
                Material mat = Material.matchMaterial(materialStr);
                if (mat == null) mat = Material.PAPER;

                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    String display = config.getString(path + "display", key);
                    String style = config.getString(path + "style", "");
                    boolean isActive = (style != null && style.equals(currentStyle))
                            || (style.isEmpty() && (currentStyle == null || currentStyle.isEmpty()));

                    String activeIndicator = isActive ? " &a✔" : "";
                    meta.displayName(ColorUtils.toComponent((display != null ? display : key) + activeIndicator));

                    if (config.contains(path + "custom-model-data")) {
                        meta.setCustomModelData(config.getInt(path + "custom-model-data"));
                    }

                    List<String> lore = config.getStringList(path + "lore");
                    List<Component> translatedLore = new ArrayList<>();
                    for (String line : lore) {
                        translatedLore.add(ColorUtils.toComponent(line));
                    }
                    meta.lore(translatedLore);

                    NamespacedKey styleKey = new NamespacedKey(plugin, "format_style");
                    meta.getPersistentDataContainer().set(styleKey, PersistentDataType.STRING, style != null ? style : "");

                    NamespacedKey effectKey = new NamespacedKey(plugin, "pending_effect");
                    meta.getPersistentDataContainer().set(effectKey, PersistentDataType.STRING, effectId != null ? effectId : "");

                    NamespacedKey categoryKey = new NamespacedKey(plugin, "pending_category");
                    meta.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING, categoryId != null ? categoryId : "");

                    item.setItemMeta(meta);
                }

                int slot = config.getInt(path + "slot", 0);
                if (slot >= 0 && slot < size) {
                    inv.setItem(slot, item);
                }
            }
        }

        player.openInventory(inv);
    }

    private static ItemStack createNavigationItem(ConfigurationSection config, String path, int nextPg, int prevPg) {
        String matStr = config.getString(path + ".material");
        if (matStr == null)
            matStr = "BARRIER";
        Material mat = Material.matchMaterial(matStr);
        if (mat == null)
            mat = Material.BARRIER;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String display = config.getString(path + ".display", path); 
            if (display == null) display = path;
            meta.displayName(ColorUtils.toComponent(display));

            if (config.contains(path + ".custom-model-data")) {
                meta.setCustomModelData(config.getInt(path + ".custom-model-data"));
            }

            List<String> lore = config.getStringList(path + ".lore");
            List<Component> finalLore = new ArrayList<>();
            for (String line : lore) { 
                String parsed = line
                        .replace("{next}", String.valueOf(nextPg))
                        .replace("{prev}", String.valueOf(prevPg));
                finalLore.add(ColorUtils.toComponent(parsed));
            }

            meta.lore(finalLore);
            item.setItemMeta(meta);
        }

        return item;
    }
}
