package com.customeffects;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.customeffects.utils.ColorUtils;
import com.customeffects.utils.SkullUtils;

public class MenuCreator {
    private static final int ITEMS_PER_PAGE = 45;

    public MenuCreator() {
    }

    public static void openMainMenu(Player player, CustomEffects plugin) {
        int size = plugin.getMainMenuSize();
        String title = plugin.getMainMenuTitle();
        Inventory inv = Bukkit.createInventory((InventoryHolder)null, size, title);
        ConfigurationSection categories = plugin.getConfig().getConfigurationSection("main-menu.categories");
        if (categories != null) {
            for(String key : categories.getKeys(false)) {
                String path = "main-menu.categories." + key + ".";
                if (!plugin.getConfig().getBoolean(path + "enable", true)) {
                    continue;
                }
                String materialStr = plugin.getConfig().getString(path + "material", "BOOK");
                Material mat = Material.matchMaterial(materialStr);
                if (mat == null) {
                    mat = Material.BOOK;
                }

                ItemStack item = new ItemStack(mat);

                if (mat == Material.PLAYER_HEAD || mat == Material.PLAYER_WALL_HEAD) {
                    String skullValue = plugin.getConfig().getString(path + "skull-value", "");
                    if (!skullValue.isEmpty()) {
                        SkullUtils.applySkullTexture(item, skullValue);
                    }
                }

                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ColorUtils.translate(plugin.getConfig().getString(path + "display", key)));
                    if (plugin.getConfig().contains(path + "custom-model-data")) {
                        meta.setCustomModelData(plugin.getConfig().getInt(path + "custom-model-data"));
                    }

                    List<String> lore = plugin.getConfig().getStringList(path + "lore");
                    List<String> translatedLore = new ArrayList();
                    if (lore != null) {
                        for(String line : lore) {
                            translatedLore.add(ColorUtils.translate(line));
                        }
                    }

                    NamespacedKey nKey = new NamespacedKey(plugin, "category_id");
                    meta.getPersistentDataContainer().set(nKey, PersistentDataType.STRING, key);
                    meta.setLore(translatedLore);
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
        String displayCategory = config.getString("main-menu.categories." + categoryId + ".display", categoryId);
        String menuTitle = ColorUtils.translate(displayCategory + " &8- Pág. " + (page + 1));
        int size = plugin.getSubMenuSize();
        Inventory inv = Bukkit.createInventory((InventoryHolder)null, size, menuTitle);
        List<String> effects = plugin.getEffectsByCategory(categoryId);
        int start = page * 45;
        int end = Math.min(start + 45, effects.size());
        String equippedMsg = config.getString("messages.equipped", "&e&n✦ ᴇǫᴜɪᴘᴀᴅᴏ");
        String clickMsg = config.getString("messages.click-to-equip", "&a&n✔ ᴄʟɪᴄᴋ ᴘᴀʀᴀ ᴇǫᴜɪᴘᴀʀ");
        String lockedMsg = config.getString("messages.locked", "&c&n✘ ʙʟᴏǫᴜᴇᴀᴅᴏ");
        
        YamlConfiguration categoryConfig = plugin.getCategoryConfig(categoryId);
        List<String> defaultLore = plugin.getDefaultLore();
        int autoSlot = 0;

        for(int i = start; i < end; ++i) {
            String effectId = (String)effects.get(i);
            
            if (categoryConfig == null || !categoryConfig.contains(effectId)) {
                continue;
            }

            if (!categoryConfig.getBoolean(effectId + ".enable", true)) {
                continue;
            }

            String materialStr = categoryConfig.getString(effectId + ".material", "PAPER");
            Material mat = Material.matchMaterial(materialStr);
            if (mat == null) {
                mat = Material.PAPER;
            }

            ItemStack item = new ItemStack(mat);

            if (mat == Material.PLAYER_HEAD || mat == Material.PLAYER_WALL_HEAD) {
                String skullValue = categoryConfig.getString(effectId + ".skull-value", "");
                if (!skullValue.isEmpty()) {
                    SkullUtils.applySkullTexture(item, skullValue);
                }
            }

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String display = categoryConfig.getString(effectId + ".display", effectId);
                meta.setDisplayName(ColorUtils.translate(display));
                
                if (categoryConfig.contains(effectId + ".custom-model-data")) {
                    meta.setCustomModelData(categoryConfig.getInt(effectId + ".custom-model-data"));
                }

                String hex = categoryConfig.getString(effectId + ".hex", "#FFFFFF");
                String preview = categoryConfig.getString(effectId + ".preview", effectId);
                String currentStatus;
                if (effectId.equalsIgnoreCase(activeEffect)) {
                    currentStatus = equippedMsg;
                } else {
                    String perm = categoryConfig.getString(effectId + ".permission", "");
                    if (!perm.isEmpty() && !player.hasPermission(perm)) {
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
                
                List<String> finalLore = new ArrayList();
                if (rawLore != null) {
                    for(String line : rawLore) {
                        String formattedLine = line.replace("{hex}", hex).replace("{preview}", preview).replace("{status}", currentStatus);
                        finalLore.add(ColorUtils.translate(formattedLine));
                    }
                }

                NamespacedKey nKey = new NamespacedKey(plugin, "effect_id");
                meta.getPersistentDataContainer().set(nKey, PersistentDataType.STRING, effectId);
                meta.setLore(finalLore);
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

        inv.setItem(config.getInt("navigation.back-main.slot", 45), createNavigationItem(config, "navigation.back-main", -1, -1));
        inv.setItem(config.getInt("reset-item.slot", 49), createNavigationItem(config, "reset-item", -1, -1));
        if (page > 0) {
            inv.setItem(config.getInt("navigation.previous-page.slot", 48), createNavigationItem(config, "navigation.previous-page", -1, page));
        }

        if (end < effects.size()) {
            inv.setItem(config.getInt("navigation.next-page.slot", 50), createNavigationItem(config, "navigation.next-page", page + 2, -1));
        }

        player.openInventory(inv);
    }

    public static void openEditor(Player player, CustomEffects plugin) {
        int size = plugin.getMainMenuSize();
        ConfigurationSection editorConfig = plugin.getConfig().getConfigurationSection("editor");
        String title = ColorUtils.translate(editorConfig != null ? editorConfig.getString("title", "&8&lEDITOR &7- Menú Principal") : "&8&lEDITOR &7- Menú Principal");
        Inventory inv = Bukkit.createInventory((InventoryHolder) null, size, title);

        ConfigurationSection categories = plugin.getConfig().getConfigurationSection("main-menu.categories");
        if (categories != null) {
            for (String key : categories.getKeys(false)) {
                String path = "main-menu.categories." + key + ".";
                if (!plugin.getConfig().getBoolean(path + "enable", true)) {
                    continue;
                }

                String materialStr = plugin.getConfig().getString(path + "material", "BOOK");
                Material mat = Material.matchMaterial(materialStr);
                if (mat == null) mat = Material.BOOK;

                ItemStack item = new ItemStack(mat);

                if (mat == Material.PLAYER_HEAD || mat == Material.PLAYER_WALL_HEAD) {
                    String skullValue = plugin.getConfig().getString(path + "skull-value", "");
                    if (!skullValue.isEmpty()) {
                        SkullUtils.applySkullTexture(item, skullValue);
                    }
                }

                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ColorUtils.translate(plugin.getConfig().getString(path + "display", key)));
                    if (plugin.getConfig().contains(path + "custom-model-data")) {
                        meta.setCustomModelData(plugin.getConfig().getInt(path + "custom-model-data"));
                    }

                    List<String> lore = plugin.getConfig().getStringList(path + "lore");
                    List<String> translatedLore = new ArrayList<>();
                    if (lore != null) {
                        for (String line : lore) {
                            translatedLore.add(ColorUtils.translate(line));
                        }
                    }

                    NamespacedKey nKey = new NamespacedKey(plugin, "category_id");
                    meta.getPersistentDataContainer().set(nKey, PersistentDataType.STRING, key);
                    meta.setLore(translatedLore);
                    item.setItemMeta(meta);
                }

                int slot = plugin.getConfig().getInt(path + "slot", 0);
                if (slot >= 0 && slot < size) {
                    inv.setItem(slot, item);
                }
            }
        }

        ItemStack blocker = createBlockerItem(plugin);
        for (int i = 0; i < size; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, blocker.clone());
            }
        }

        player.openInventory(inv);
    }

    public static void openCategoryEditor(Player player, CustomEffects plugin, String categoryId, int page) {
        ConfigurationSection config = plugin.getConfig();
        String displayCategory = config.getString("main-menu.categories." + categoryId + ".display", categoryId);
        String cleanDisplay = ColorUtils.translate(displayCategory);
        String menuTitle = cleanDisplay + " &8- EDITOR &7Pág. " + (page + 1);
        int size = plugin.getSubMenuSize();
        Inventory inv = Bukkit.createInventory((InventoryHolder) null, size, ColorUtils.translate(menuTitle));

        List<String> effects = plugin.getEffectsByCategory(categoryId);
        int start = page * 45;
        int end = Math.min(start + 45, effects.size());

        YamlConfiguration categoryConfig = plugin.getCategoryConfig(categoryId);
        int autoSlot = 0;

        for (int i = start; i < end; i++) {
            String effectId = effects.get(i);

            if (categoryConfig == null || !categoryConfig.contains(effectId)) continue;
            if (!categoryConfig.getBoolean(effectId + ".enable", true)) continue;

            String materialStr = categoryConfig.getString(effectId + ".material", "PAPER");
            Material mat = Material.matchMaterial(materialStr);
            if (mat == null) mat = Material.PAPER;

            ItemStack item = new ItemStack(mat);

            if (mat == Material.PLAYER_HEAD || mat == Material.PLAYER_WALL_HEAD) {
                String skullValue = categoryConfig.getString(effectId + ".skull-value", "");
                if (!skullValue.isEmpty()) {
                    SkullUtils.applySkullTexture(item, skullValue);
                }
            }

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String display = categoryConfig.getString(effectId + ".display", effectId);
                meta.setDisplayName(ColorUtils.translate(display));

                if (categoryConfig.contains(effectId + ".custom-model-data")) {
                    meta.setCustomModelData(categoryConfig.getInt(effectId + ".custom-model-data"));
                }

                NamespacedKey nKey = new NamespacedKey(plugin, "effect_id");
                meta.getPersistentDataContainer().set(nKey, PersistentDataType.STRING, effectId);
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

        ItemStack blocker = createBlockerItem(plugin);
        for (int i = 0; i < 45; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, blocker.clone());
            }
        }

        player.openInventory(inv);
    }

    public static ItemStack createBlockerItem(CustomEffects plugin) {
        ConfigurationSection blockerConfig = plugin.getConfig().getConfigurationSection("editor.blocker");
        String matStr = "GRAY_STAINED_GLASS_PANE";
        int cmd = 0;
        String display = "&c&lOCUPADO";
        List<String> lore = new ArrayList<>();

        if (blockerConfig != null) {
            matStr = blockerConfig.getString("material", "GRAY_STAINED_GLASS_PANE");
            if (blockerConfig.contains("custom-model-data")) {
                cmd = blockerConfig.getInt("custom-model-data");
            }
            display = blockerConfig.getString("display", "&c&lOCUPADO");
            lore = blockerConfig.getStringList("lore");
        }

        Material mat = Material.matchMaterial(matStr);
        if (mat == null) mat = Material.GRAY_STAINED_GLASS_PANE;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtils.translate(display));
            if (cmd != 0) {
                meta.setCustomModelData(cmd);
            }
            List<String> translatedLore = new ArrayList<>();
            for (String line : lore) {
                translatedLore.add(ColorUtils.translate(line));
            }
            meta.setLore(translatedLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createNavigationItem(ConfigurationSection config, String path, int nextPg, int prevPg) {
        String matStr = config.getString(path + ".material", "BARRIER");
        Material mat = Material.matchMaterial(matStr);
        if (mat == null) {
            mat = Material.BARRIER;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtils.translate(config.getString(path + ".display")));
            if (config.contains(path + ".custom-model-data")) {
                meta.setCustomModelData(config.getInt(path + ".custom-model-data"));
            }

            List<String> lore = config.getStringList(path + ".lore");
            List<String> finalLore = new ArrayList();
            if (lore != null) {
                for(String line : lore) {
                    String parsed = line.replace("{next}", String.valueOf(nextPg)).replace("{prev}", String.valueOf(prevPg));
                    finalLore.add(ColorUtils.translate(parsed));
                }
            }

            meta.setLore(finalLore);
            item.setItemMeta(meta);
        }

        return item;
    }
}
