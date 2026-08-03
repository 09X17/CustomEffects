package com.customeffects;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;

public class MenuCreator {

    public MenuCreator() {
    }

    private static String resolvePlaceholders(Player player, String text) {
        if (text == null || text.isEmpty()) return text;
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
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
                    meta.displayName(ColorUtils.toComponent(resolvePlaceholders(player, displayName != null ? displayName : key)));

                    if (plugin.getConfig().contains(path + "custom-model-data")) {
                        meta.setCustomModelData(plugin.getConfig().getInt(path + "custom-model-data"));
                    }

                    List<String> lore = plugin.getConfig().getStringList(path + "lore");
                    List<Component> translatedLore = new ArrayList<>();
                    for (String line : lore) {
                        translatedLore.add(ColorUtils.toComponent(resolvePlaceholders(player, line)));
                    }

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
        if (plugin.hasSubcategories(categoryId)) {
            openSubcategoryMenu(player, plugin, categoryId);
            return;
        }
        openCategoryEffectsMenu(player, plugin, categoryId, null, page);
    }

    public static void openCategoryMenu(Player player, CustomEffects plugin, String categoryId, String subcategoryId, int page) {
        openCategoryEffectsMenu(player, plugin, categoryId, subcategoryId, page);
    }

    private static void openSubcategoryMenu(Player player, CustomEffects plugin, String categoryId) {
        ConfigurationSection config = plugin.getConfig();
        String displayCategory = config.getString("main-menu.categories." + categoryId + ".display-submenu",
                config.getString("main-menu.categories." + categoryId + ".display", categoryId));
        Component menuTitle = ColorUtils.toComponent(displayCategory + " &8- Subcategorías");

        int size = plugin.getSubMenuSize();
        Inventory inv = Bukkit.createInventory(null, size, menuTitle);

        List<String> subcategories = plugin.getSubcategories(categoryId);
        YamlConfiguration categoryConfig = plugin.getCategoryConfig(categoryId);

        for (String subcatId : subcategories) {
            Map<String, String> subcatInfo = plugin.getSubcategoryInfo(categoryId, subcatId);
            String materialStr = subcatInfo.getOrDefault("material", "PAPER");
            Material mat = Material.matchMaterial(materialStr);
            if (mat == null) mat = Material.PAPER;

            ItemStack item = new ItemStack(mat);
            if (mat == Material.PLAYER_HEAD || mat == Material.PLAYER_WALL_HEAD) {
                String skullValue = subcatInfo.getOrDefault("skull-value", "");
                if (!skullValue.isEmpty()) {
                    SkullUtils.applySkullTexture(item, skullValue);
                }
            }

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(ColorUtils.toComponent(resolvePlaceholders(player, subcatInfo.getOrDefault("display", subcatId))));

                String customModelDataStr = subcatInfo.getOrDefault("custom-model-data", "");
                if (!customModelDataStr.isEmpty()) {
                    try {
                        meta.setCustomModelData(Integer.parseInt(customModelDataStr));
                    } catch (NumberFormatException ignored) {}
                }

                List<String> rawLore = categoryConfig != null
                        ? categoryConfig.getStringList("subcategories." + subcatId + ".lore")
                        : new ArrayList<>();
                List<Component> finalLore = new ArrayList<>();
                for (String line : rawLore) {
                    finalLore.add(ColorUtils.toComponent(resolvePlaceholders(player, line)));
                }
                meta.lore(finalLore);

                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "category_id"),
                        PersistentDataType.STRING, categoryId);
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "subcategory_id"),
                        PersistentDataType.STRING, subcatId);
                item.setItemMeta(meta);
            }

            String slotStr = subcatInfo.getOrDefault("slot", "0");
            int slot = 0;
            try { slot = Integer.parseInt(slotStr); } catch (NumberFormatException ignored) {}
            if (slot >= 0 && slot < size) {
                inv.setItem(slot, item);
            }
        }

        if (config.getBoolean("navigation.back-main.enable", true)) {
            inv.setItem(config.getInt("navigation.back-main.slot", 45),
                    createNavigationItem(config, "navigation.back-main", -1, -1));
        }

        player.openInventory(inv);
    }

    private static void openCategoryEffectsMenu(Player player, CustomEffects plugin, String categoryId, String subcategoryId, int page) {
        ConfigurationSection config = plugin.getConfig();
        UUID uuid = player.getUniqueId();
        boolean isPrefixCategory = plugin.isPrefixCategory(categoryId);
        String activeEffect = isPrefixCategory ? plugin.getDatabase().getPrefix(uuid) : plugin.getDatabase().getActiveEffect(uuid);

        String displayTitle;
        if (subcategoryId != null && !subcategoryId.isEmpty()) {
            Map<String, String> subcatInfo = plugin.getSubcategoryInfo(categoryId, subcategoryId);
            displayTitle = subcatInfo.getOrDefault("display", subcategoryId);
        } else {
            displayTitle = config.getString("main-menu.categories." + categoryId + ".display-submenu",
                    config.getString("main-menu.categories." + categoryId + ".display", categoryId));
        }

        String titleSuffix = subcategoryId != null && !subcategoryId.isEmpty()
                ? " &8\u2502" + categoryId + ":" + subcategoryId
                : " &8\u2502" + categoryId;
        Component menuTitle = ColorUtils.toComponent(displayTitle + " &8- P\u00e1g. " + (page + 1) + titleSuffix);

        int size = plugin.getSubMenuSize();
        Inventory inv = Bukkit.createInventory(null, size, menuTitle);

        List<String> effects;
        if (subcategoryId != null && !subcategoryId.isEmpty()) {
            effects = plugin.getEffectsBySubcategory(categoryId, subcategoryId);
        } else {
            effects = plugin.getEffectsByCategory(categoryId);
        }

        if (isPrefixCategory) {
            effects = new ArrayList<>(effects);
            effects.removeIf(effectId -> {
                YamlConfiguration catConfig = plugin.getCategoryConfig(categoryId);
                if (catConfig == null) return false;
                String prefixGroup = catConfig.getString(effectId + ".group", "all");
                return !plugin.canPlayerSeePrefix(player, prefixGroup);
            });
        }

        int effectsPerPage = plugin.getEffectsPerPage();
        int start = page * effectsPerPage;
        int end = Math.min(start + effectsPerPage, effects.size());

        String equippedMsg = config.getString("messages.equipped", "&e&n*\u1107 \u1230\u1211\u1260\u1298");
        String clickMsg = config.getString("messages.click-to-equip", "&a&n\u2714 \u1240\u1238\u1239 \u124a\u122a\u124a \u1230\u1260\u123a\u1260\u1237");
        String lockedMsg = config.getString("messages.locked", "&c&n\u2718 \u1260\u1235\u123c\u1260\u1248\u1230\u1235");

        YamlConfiguration categoryConfig = plugin.getCategoryConfig(categoryId);
        List<String> defaultLore = plugin.getDefaultLore();
        int autoSlot = 0;

        // 1. Cargar Efectos/Prefijos
        for (int i = start; i < end; ++i) {
            String effectId = effects.get(i);
            if (categoryConfig == null || !categoryConfig.contains(effectId)
                    || !categoryConfig.getBoolean(effectId + ".enable", true))
                continue;

            String materialStr = categoryConfig.getString(effectId + ".material", "PAPER");
            Material mat = Material.matchMaterial(materialStr != null ? materialStr : "PAPER");
            if (mat == null)
                mat = Material.PAPER;

            ItemStack item = new ItemStack(mat);
            if (mat == Material.PLAYER_HEAD || mat == Material.PLAYER_WALL_HEAD) {
                String skullValue = categoryConfig.getString(effectId + ".skull-value", "");
                if (skullValue != null && !skullValue.isEmpty())
                    SkullUtils.applySkullTexture(item, skullValue);
            }

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(ColorUtils.toComponent(resolvePlaceholders(player, categoryConfig.getString(effectId + ".display", effectId))));
                if (categoryConfig.contains(effectId + ".custom-model-data"))
                    meta.setCustomModelData(categoryConfig.getInt(effectId + ".custom-model-data"));

                String currentStatus;
                if (isPrefixCategory) {
                    String prefixValue = categoryConfig.getString(effectId + ".prefix", "");
                    boolean isEquipped = prefixValue.equals(activeEffect) || (prefixValue.isEmpty() && (activeEffect == null || activeEffect.isEmpty()));
                    currentStatus = isEquipped ? equippedMsg
                            : (player.hasPermission(categoryConfig.getString(effectId + ".permission", "")) ? clickMsg
                                    : lockedMsg);
                } else {
                    currentStatus = effectId.equalsIgnoreCase(activeEffect) ? equippedMsg
                            : (player.hasPermission(categoryConfig.getString(effectId + ".permission", "")) ? clickMsg
                                    : lockedMsg);
                }

                String hex = categoryConfig.getString(effectId + ".hex", "#FFFFFF");
                String preview = categoryConfig.getString(effectId + ".preview", effectId);
                List<String> rawLore = categoryConfig.contains(effectId + ".lore")
                        ? categoryConfig.getStringList(effectId + ".lore")
                        : defaultLore;
                List<Component> finalLore = new ArrayList<>();
                for (String line : rawLore) {
                    String processed = line.replace("{hex}", hex).replace("{preview}", preview)
                            .replace("{status}", currentStatus);
                    finalLore.add(ColorUtils.toComponent(resolvePlaceholders(player, processed)));
                }

                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "effect_id"), PersistentDataType.STRING,
                        effectId);
                meta.lore(finalLore);
                item.setItemMeta(meta);
            }
            int targetSlot = categoryConfig.getInt(effectId + ".slot", autoSlot++);
            if (targetSlot >= 0 && targetSlot < size)
                inv.setItem(targetSlot, item);
        }

        // 2. Cargar Formatos (solo para categorías de efectos, no prefijos)
        if (!isPrefixCategory) {
            String currentStyle = plugin.getDatabase().getStyle(player.getUniqueId());
            ConfigurationSection formatItems = config.getConfigurationSection("format-menu.items");
            if (formatItems != null) {
                for (String key : formatItems.getKeys(false)) {
                    String path = "format-menu.items." + key + ".";
                    if (!config.getBoolean(path + "enable", true))
                        continue;

                    ItemStack item = new ItemStack(Material.matchMaterial(config.getString(path + "material", "PAPER")));
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        String style = config.getString(path + "style", "");
                        boolean isActive = (style != null && style.equals(currentStyle))
                                || (style.isEmpty() && (currentStyle == null || currentStyle.isEmpty()));
                        meta.displayName(
                                ColorUtils.toComponent(config.getString(path + "display", key) + (isActive ? " &a\u2714" : "")));
                        if (config.contains(path + "custom-model-data"))
                            meta.setCustomModelData(config.getInt(path + "custom-model-data"));

                        List<String> lore = config.getStringList(path + "lore");
                        List<Component> translatedLore = new ArrayList<>();
                        for (String line : lore)
                            translatedLore.add(ColorUtils.toComponent(line));
                        meta.lore(translatedLore);

                        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "format_style"),
                                PersistentDataType.STRING, style);
                        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "pending_effect"),
                                PersistentDataType.STRING, "");
                        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "pending_category"),
                                PersistentDataType.STRING, categoryId);
                        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "pending_subcategory"),
                                PersistentDataType.STRING, subcategoryId != null ? subcategoryId : "");
                        item.setItemMeta(meta);
                    }
                    int slot = config.getInt(path + "slot", 0);
                    if (slot >= 0 && slot < size)
                        inv.setItem(slot, item);
                }
            }
        }

        // 3. Navegacion (con soporte para enable)
        if (config.getBoolean("navigation.back-main.enable", true)) {
            inv.setItem(config.getInt("navigation.back-main.slot", 45),
                    createNavigationItem(config, "navigation.back-main", -1, -1));
        }
        if (!isPrefixCategory && config.getBoolean("reset-item.enable", true)) {
            inv.setItem(config.getInt("reset-item.slot", 49), createNavigationItem(config, "reset-item", -1, -1));
        }
        if (!isPrefixCategory && config.getBoolean("format-button.enable", false)) {
            inv.setItem(config.getInt("format-button.slot", 46), createNavigationItem(config, "format-button", -1, -1));
        }
        if (page > 0 && config.getBoolean("navigation.previous-page.enable", true)) {
            inv.setItem(config.getInt("navigation.previous-page.slot", 48),
                    createNavigationItem(config, "navigation.previous-page", -1, page));
        }
        if (end < effects.size() && config.getBoolean("navigation.next-page.enable", true)) {
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
                if (materialStr == null)
                    materialStr = "PAPER";
                Material mat = Material.matchMaterial(materialStr);
                if (mat == null)
                    mat = Material.PAPER;

                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    String display = config.getString(path + "display", key);
                    String style = config.getString(path + "style", "");
                    boolean isActive = (style != null && style.equals(currentStyle))
                            || (style.isEmpty() && (currentStyle == null || currentStyle.isEmpty()));

                    String activeIndicator = isActive ? " &a\u2714" : "";
                    meta.displayName(ColorUtils.toComponent(resolvePlaceholders(player, (display != null ? display : key) + activeIndicator)));

                    if (config.contains(path + "custom-model-data")) {
                        meta.setCustomModelData(config.getInt(path + "custom-model-data"));
                    }

                    List<String> lore = config.getStringList(path + "lore");
                    List<Component> translatedLore = new ArrayList<>();
                    for (String line : lore) {
                        translatedLore.add(ColorUtils.toComponent(resolvePlaceholders(player, line)));
                    }
                    meta.lore(translatedLore);

                    NamespacedKey styleKey = new NamespacedKey(plugin, "format_style");
                    meta.getPersistentDataContainer().set(styleKey, PersistentDataType.STRING,
                            style != null ? style : "");

                    NamespacedKey effectKey = new NamespacedKey(plugin, "pending_effect");
                    meta.getPersistentDataContainer().set(effectKey, PersistentDataType.STRING,
                            effectId != null ? effectId : "");

                    NamespacedKey categoryKey = new NamespacedKey(plugin, "pending_category");
                    meta.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING,
                            categoryId != null ? categoryId : "");

                    item.setItemMeta(meta);
                }

                int slot = config.getInt(path + ".slot", 0);
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
            if (display == null)
                display = path;
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
