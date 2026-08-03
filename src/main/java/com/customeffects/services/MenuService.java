package com.customeffects.services;

import com.customeffects.CustomEffects;
import com.customeffects.models.Category;
import com.customeffects.models.Effect;
import com.customeffects.models.Subcategory;
import com.customeffects.utils.ColorUtils;
import com.customeffects.utils.SkullUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MenuService {
    private final CustomEffects plugin;
    private final DataManager dataManager;

    public MenuService(CustomEffects plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    public void openMainMenu(Player player) {
        ConfigurationSection config = plugin.getConfig();
        int size = dataManager.getMainMenuSize();
        Component title = ColorUtils.toComponent(dataManager.getMainMenuTitle());
        Inventory inv = Bukkit.createInventory(null, size, title);

        // Agregar cabezas decorativas con la skin del jugador
        addPlayerHeads(inv, player, config, size);

        for (Category category : dataManager.getAllCategories()) {
            if (!category.isEnabled()) continue;
            if (!player.hasPermission("effectos.category." + category.getId()) && 
                !player.hasPermission("effectos.admin")) continue;

            ItemStack item = createCategoryItem(player, category);
            if (category.getSlot() >= 0 && category.getSlot() < size) {
                inv.setItem(category.getSlot(), item);
            }
        }

        addFillerItems(inv, size);
        player.openInventory(inv);
    }

    private void addPlayerHeads(Inventory inv, Player player, ConfigurationSection config, int size) {
        List<?> decorativeList = config.getList("main-menu.decorative-items");
        if (decorativeList == null || decorativeList.isEmpty()) return;

        for (Object obj : decorativeList) {
            if (!(obj instanceof Map<?, ?> map)) continue;

            int slot = map.get("slot") instanceof Number ? ((Number) map.get("slot")).intValue() : -1;
            if (slot < 0 || slot >= size) continue;

            String materialStr = map.get("material") instanceof String ? (String) map.get("material") : "PLAYER_HEAD";
            String display = map.get("display") instanceof String ? (String) map.get("display") : " ";
            int customModelData = map.get("custom-model-data") instanceof Number ? ((Number) map.get("custom-model-data")).intValue() : 0;
            String itemModel = map.get("item-model") instanceof String ? (String) map.get("item-model") : "";
            String skullValue = map.get("skull-value") instanceof String ? (String) map.get("skull-value") : "";
            List<String> lore = map.get("lore") instanceof List ? (List<String>) map.get("lore") : new ArrayList<>();

            Material material = getMaterial(materialStr);
            ItemStack item = new ItemStack(material);

            // Si es PLAYER_HEAD, aplicar skin
            if (material == Material.PLAYER_HEAD || material == Material.PLAYER_WALL_HEAD) {
                if (skullValue != null && !skullValue.isEmpty()) {
                    SkullUtils.applySkullTexture(item, skullValue);
                } else {
                    SkullUtils.applyPlayerSkin(item, player);
                }
            }

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(ColorUtils.toComponent(resolvePlaceholders(player, display)));

                // item_model tiene prioridad sobre custom_model_data (1.21.4+)
                if (itemModel != null && !itemModel.isEmpty()) {
                    NamespacedKey modelKey = NamespacedKey.fromString(itemModel);
                    if (modelKey != null) {
                        meta.setItemModel(modelKey);
                    }
                } else if (customModelData > 0) {
                    meta.setCustomModelData(customModelData);
                }

                List<Component> finalLore = new ArrayList<>();
                for (String line : lore) {
                    finalLore.add(ColorUtils.toComponent(resolvePlaceholders(player, line)));
                }
                meta.lore(finalLore);

                item.setItemMeta(meta);
            }

            inv.setItem(slot, item);
        }
    }

    private ItemStack createCategoryItem(Player player, Category category) {
        ItemStack item = new ItemStack(category.getMaterial());
        
        if (isHeadMaterial(category.getMaterial())) {
            if (category.getSkullValue() != null && !category.getSkullValue().isEmpty()) {
                SkullUtils.applySkullTexture(item, category.getSkullValue());
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.toComponent(resolvePlaceholders(player, category.getDisplay())));
            
            if (category.getCustomModelData() > 0) {
                meta.setCustomModelData(category.getCustomModelData());
            }

            List<Component> lore = new ArrayList<>();
            for (String line : category.getLore()) {
                lore.add(ColorUtils.toComponent(resolvePlaceholders(player, line)));
            }
            meta.lore(lore);

            setPersistentData(meta, "category_id", category.getId());
            item.setItemMeta(meta);
        }

        return item;
    }

    public void openCategoryMenu(Player player, String categoryId, int page) {
        Category category = dataManager.getCategory(categoryId);
        if (category == null) {
            player.sendMessage(ColorUtils.translate("&cCategoría no encontrada."));
            return;
        }

        if (category.hasSubcategories()) {
            openSubcategoryMenu(player, category);
        } else {
            openEffectsMenu(player, category, null, page);
        }
    }

    public void openCategoryMenu(Player player, String categoryId, String subcategoryId, int page) {
        Category category = dataManager.getCategory(categoryId);
        if (category == null) {
            player.sendMessage(ColorUtils.translate("&cCategoría no encontrada."));
            return;
        }
        openEffectsMenu(player, category, subcategoryId, page);
    }

    private void openSubcategoryMenu(Player player, Category category) {
        ConfigurationSection config = plugin.getConfig();
        String titleFormat = config.getString("subcategory-menu.title-format", "{category} &8- Subcategorías");
        boolean showSuffix = config.getBoolean("subcategory-menu.show-suffix", false);
        // El sufijo oculto permite al listener detectar la categoría
        String hiddenSuffix = showSuffix ? " &8|" + category.getId() : "&0&8|" + category.getId();
        String displayTitle = titleFormat.replace("{category}", category.getDisplaySubmenu()) + hiddenSuffix;
        Component menuTitle = ColorUtils.toComponent(displayTitle);

        int size = dataManager.getSubMenuSize();
        Inventory inv = Bukkit.createInventory(null, size, menuTitle);

        for (Subcategory subcategory : category.getSubcategories()) {
            if (!subcategory.isEnabled()) continue;

            ItemStack item = createSubcategoryItem(player, category, subcategory);
            if (subcategory.getSlot() >= 0 && subcategory.getSlot() < size) {
                inv.setItem(subcategory.getSlot(), item);
            }
        }

        addNavigationItem(inv, config, "navigation.back-main", -1, -1);
        addFillerItems(inv, size);
        player.openInventory(inv);
    }

    private ItemStack createSubcategoryItem(Player player, Category category, Subcategory subcategory) {
        ItemStack item = new ItemStack(subcategory.getMaterial());

        if (isHeadMaterial(subcategory.getMaterial())) {
            if (subcategory.getSkullValue() != null && !subcategory.getSkullValue().isEmpty()) {
                SkullUtils.applySkullTexture(item, subcategory.getSkullValue());
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.toComponent(resolvePlaceholders(player, subcategory.getDisplay())));

            if (subcategory.getCustomModelData() > 0) {
                meta.setCustomModelData(subcategory.getCustomModelData());
            }

            List<Component> lore = new ArrayList<>();
            for (String line : subcategory.getLore()) {
                lore.add(ColorUtils.toComponent(resolvePlaceholders(player, line)));
            }
            meta.lore(lore);

            setPersistentData(meta, "category_id", category.getId());
            setPersistentData(meta, "subcategory_id", subcategory.getId());
            item.setItemMeta(meta);
        }

        return item;
    }

    private void openEffectsMenu(Player player, Category category, String subcategoryId, int page) {
        ConfigurationSection config = plugin.getConfig();
        UUID uuid = player.getUniqueId();
        boolean isPrefixCategory = category.isPrefixCategory();
        
        String activeEffect;
        if (isPrefixCategory) {
            activeEffect = plugin.getDatabase().getPrefix(uuid);
        } else {
            activeEffect = plugin.getDatabase().getActiveEffect(uuid);
        }

        String displayTitle = getEffectsMenuTitle(category, subcategoryId);
        String titleSuffix = buildTitleSuffix(category, subcategoryId);
        
        String titleFormat = config.getString("effects-menu.title-format", "{title} &8- Pág. {page}{suffix}");
        boolean showSuffix = config.getBoolean("effects-menu.show-suffix", false);
        
        // Si show-suffix es false, el sufijo se agrega con color invisible para que el listener pueda leerlo
        String finalTitle = titleFormat
                .replace("{title}", displayTitle)
                .replace("{page}", String.valueOf(page + 1))
                .replace("{suffix}", showSuffix ? titleSuffix : "&0" + titleSuffix);
        Component menuTitle = ColorUtils.toComponent(finalTitle);

        int size = dataManager.getSubMenuSize();
        Inventory inv = Bukkit.createInventory(null, size, menuTitle);

        List<Effect> effects = getEffectsForMenu(category, subcategoryId, player, isPrefixCategory, config);

        int effectsPerPage = dataManager.getEffectsPerPage();
        int start = page * effectsPerPage;
        if (start < 0) start = 0;
        int end = Math.min(start + effectsPerPage, effects.size());
        if (start >= effects.size() && !effects.isEmpty()) {
            start = 0;
            end = Math.min(effectsPerPage, effects.size());
        }

        String equippedMsg = config.getString("messages.equipped", "&e&n* équipé");
        String clickMsg = config.getString("messages.click-to-equip", "&a&n✔ Clic para equipar");
        String lockedMsg = config.getString("messages.locked", "&c&n✘ Bloqueado");

        List<String> defaultLore = plugin.getDefaultLore();
        int autoSlot = 0;

        for (int i = start; i < end; i++) {
            Effect effect = effects.get(i);
            ItemStack item = createEffectItem(player, effect, activeEffect, isPrefixCategory, equippedMsg, clickMsg, lockedMsg, defaultLore);
            
            int targetSlot = effect.getSlot() >= 0 ? effect.getSlot() : autoSlot++;
            if (targetSlot >= 0 && targetSlot < size) {
                inv.setItem(targetSlot, item);
            }
        }

        if (!isPrefixCategory) {
            addFormatItems(inv, player, category, subcategoryId);
        }

        addNavigationItems(inv, config, isPrefixCategory, page, effects.size(), start, end);
        addFillerItems(inv, size);
        player.openInventory(inv);
    }

    private String getEffectsMenuTitle(Category category, String subcategoryId) {
        if (subcategoryId != null && !subcategoryId.isEmpty()) {
            Subcategory subcategory = category.getSubcategoryById(subcategoryId);
            return subcategory != null ? subcategory.getDisplay() : subcategoryId;
        }
        return category.getDisplaySubmenu();
    }

    private String buildTitleSuffix(Category category, String subcategoryId) {
        if (subcategoryId != null && !subcategoryId.isEmpty()) {
            return " &8|" + category.getId() + ":" + subcategoryId;
        }
        return " &8|" + category.getId();
    }

    private List<Effect> getEffectsForMenu(Category category, String subcategoryId, Player player, boolean isPrefixCategory, ConfigurationSection config) {
        List<Effect> effects;
        if (subcategoryId != null && !subcategoryId.isEmpty()) {
            Subcategory subcategory = category.getSubcategoryById(subcategoryId);
            effects = subcategory != null ? new ArrayList<>(subcategory.getEffects()) : new ArrayList<>();
        } else {
            effects = new ArrayList<>(category.getEffects());
        }

        if (isPrefixCategory) {
            List<String> hierarchy = config.getStringList("prefix-system.rank-hierarchy");
            String playerGroup = plugin.getPlayerGroup(player);
            effects.removeIf(e -> !e.isGroupVisible(playerGroup, hierarchy));
            
            String currentPrefix = plugin.getDatabase().getPrefix(player.getUniqueId());
            if (currentPrefix != null && !currentPrefix.isEmpty()) {
                String luckPermsPrefix = plugin.getLuckPermsPrefix(player);
                boolean prefixBelongsToEffect = effects.stream().anyMatch(e -> currentPrefix.equals(e.getPrefix()));
                boolean prefixIsLuckPermsDefault = currentPrefix.equals(luckPermsPrefix);
                
                if (!prefixBelongsToEffect && !prefixIsLuckPermsDefault) {
                    plugin.getDatabase().savePrefix(player.getUniqueId(), "");
                }
            }
        }

        return effects;
    }

    private ItemStack createEffectItem(Player player, Effect effect, String activeEffect, boolean isPrefixCategory,
                                       String equippedMsg, String clickMsg, String lockedMsg, List<String> defaultLore) {
        ItemStack item = new ItemStack(effect.getMaterial());

        if (isHeadMaterial(effect.getMaterial())) {
            if (effect.getSkullValue() != null && !effect.getSkullValue().isEmpty()) {
                SkullUtils.applySkullTexture(item, effect.getSkullValue());
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.toComponent(resolvePlaceholders(player, effect.getDisplay())));

            // Usar custom-model-data por rango si está configurado
            String playerGroup = plugin.getPlayerGroup(player);
            int cmd = effect.getCustomModelDataForRank(playerGroup);
            if (cmd > 0) {
                meta.setCustomModelData(cmd);
            }

            String currentStatus = calculateStatus(player, effect, activeEffect, isPrefixCategory, equippedMsg, clickMsg, lockedMsg);

            List<String> rawLore = effect.getLore().isEmpty() ? defaultLore : effect.getLore();
            List<Component> finalLore = new ArrayList<>();
            for (String line : rawLore) {
                String processed = line
                        .replace("{hex}", effect.getHex())
                        .replace("{preview}", effect.getPreview())
                        .replace("{status}", currentStatus)
                        .replace("{effect_name}", effect.getDisplay())
                        .replace("{permission}", effect.getPermission());
                finalLore.add(ColorUtils.toComponent(resolvePlaceholders(player, processed)));
            }
            meta.lore(finalLore);

            setPersistentData(meta, "effect_id", effect.getId());
            item.setItemMeta(meta);
        }

        return item;
    }

    private String calculateStatus(Player player, Effect effect, String activeEffect, boolean isPrefixCategory,
                                   String equippedMsg, String clickMsg, String lockedMsg) {
        if (isPrefixCategory) {
            String prefixValue = effect.getPrefix();
            boolean isEquipped = prefixValue.equals(activeEffect) || 
                    (prefixValue.isEmpty() && (activeEffect == null || activeEffect.isEmpty()));
            return isEquipped ? equippedMsg : 
                    (player.hasPermission(effect.getPermission()) ? clickMsg : lockedMsg);
        } else {
            return effect.getId().equalsIgnoreCase(activeEffect) ? equippedMsg : 
                    (player.hasPermission(effect.getPermission()) ? clickMsg : lockedMsg);
        }
    }

    private void addFormatItems(Inventory inv, Player player, Category category, String subcategoryId) {
        ConfigurationSection config = plugin.getConfig();
        String currentStyle = plugin.getDatabase().getStyle(player.getUniqueId());
        ConfigurationSection formatItems = config.getConfigurationSection("format-menu.items");
        if (formatItems == null) return;

        int size = dataManager.getSubMenuSize();
        for (String key : formatItems.getKeys(false)) {
            String path = "format-menu.items." + key + ".";
            if (!config.getBoolean(path + "enable", true)) continue;

            Material material = getMaterial(config.getString(path + "material", "PAPER"));

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String style = config.getString(path + "style", "");
                boolean isActive = isStyleActive(style, currentStyle);
                
                String display = config.getString(path + "display", key);
                meta.displayName(ColorUtils.toComponent(display + (isActive ? " &a✔" : "")));
                
                if (config.contains(path + "custom-model-data")) {
                    meta.setCustomModelData(config.getInt(path + "custom-model-data"));
                }

                List<String> lore = config.getStringList(path + "lore");
                List<Component> translatedLore = new ArrayList<>();
                for (String line : lore) {
                    translatedLore.add(ColorUtils.toComponent(line));
                }
                meta.lore(translatedLore);

                setPersistentData(meta, "format_style", style);
                setPersistentData(meta, "pending_effect", "");
                setPersistentData(meta, "pending_category", category.getId());
                setPersistentData(meta, "pending_subcategory", subcategoryId != null ? subcategoryId : "");
                item.setItemMeta(meta);
            }

            int slot = config.getInt(path + "slot", 0);
            if (slot >= 0 && slot < size) {
                inv.setItem(slot, item);
            }
        }
    }

    private void addNavigationItems(Inventory inv, ConfigurationSection config, boolean isPrefixCategory,
                                   int page, int totalEffects, int start, int end) {
        addNavigationItem(inv, config, "navigation.back-main", -1, -1);
        
        if (!isPrefixCategory) {
            addNavigationItemIfEnabled(inv, config, "reset-item", -1, -1);
            addNavigationItemIfEnabled(inv, config, "format-button", -1, -1);
        }
        
        if (page > 0) {
            addNavigationItemIfEnabled(inv, config, "navigation.previous-page", -1, page);
        }
        
        if (end < totalEffects) {
            addNavigationItemIfEnabled(inv, config, "navigation.next-page", page + 2, -1);
        }
    }

    private void addNavigationItemIfEnabled(Inventory inv, ConfigurationSection config, String path, int nextPg, int prevPg) {
        if (config.getBoolean(path + ".enable", true)) {
            int slot = config.getInt(path + ".slot", 0);
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, createNavigationItem(config, path, nextPg, prevPg));
            }
        }
    }

    private void addNavigationItem(Inventory inv, ConfigurationSection config, String path, int nextPg, int prevPg) {
        if (config.getBoolean(path + ".enable", true)) {
            int slot = config.getInt(path + ".slot", 45);
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, createNavigationItem(config, path, nextPg, prevPg));
            }
        }
    }

    private ItemStack createNavigationItem(ConfigurationSection config, String path, int nextPg, int prevPg) {
        Material material = getMaterial(config.getString(path + ".material", "BARRIER"));

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String display = config.getString(path + ".display", path);
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

    public void openFormatMenu(Player player, String effectId, String categoryId) {
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
                if (!config.getBoolean(path + "enable", true)) continue;

                Material material = getMaterial(config.getString(path + "material", "PAPER"));

                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    String display = config.getString(path + "display", key);
                    String style = config.getString(path + "style", "");
                    boolean isActive = isStyleActive(style, currentStyle);

                    meta.displayName(ColorUtils.toComponent(resolvePlaceholders(player, 
                            (display != null ? display : key) + (isActive ? " &a✔" : ""))));

                    if (config.contains(path + "custom-model-data")) {
                        meta.setCustomModelData(config.getInt(path + "custom-model-data"));
                    }

                    List<String> lore = config.getStringList(path + "lore");
                    List<Component> translatedLore = new ArrayList<>();
                    for (String line : lore) {
                        translatedLore.add(ColorUtils.toComponent(resolvePlaceholders(player, line)));
                    }
                    meta.lore(translatedLore);

                    setPersistentData(meta, "format_style", style != null ? style : "");
                    setPersistentData(meta, "pending_effect", effectId != null ? effectId : "");
                    setPersistentData(meta, "pending_category", categoryId != null ? categoryId : "");
                    item.setItemMeta(meta);
                }

                int slot = config.getInt(path + "slot", 0);
                if (slot >= 0 && slot < size) {
                    inv.setItem(slot, item);
                }
            }
        }

        addFillerItems(inv, size);
        player.openInventory(inv);
    }

    private void addFillerItems(Inventory inv, int size) {
        ConfigurationSection config = plugin.getConfig();
        if (!config.getBoolean("filler-items.enable", false)) return;

        Material fillerMaterial = getMaterial(config.getString("filler-items.material", "GRAY_STAINED_GLASS_PANE"));
        String fillerDisplay = config.getString("filler-items.display", " ");
        int fillerCustomModelData = config.getInt("filler-items.custom-model-data", 0);

        ItemStack filler = new ItemStack(fillerMaterial);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(ColorUtils.toComponent(fillerDisplay));
            if (fillerCustomModelData > 0) {
                fillerMeta.setCustomModelData(fillerCustomModelData);
            }
            filler.setItemMeta(fillerMeta);
        }

        List<Integer> occupiedSlots = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (inv.getItem(i) != null) {
                occupiedSlots.add(i);
            }
        }

        for (int i = 0; i < size; i++) {
            if (!occupiedSlots.contains(i)) {
                inv.setItem(i, filler.clone());
            }
        }
    }

    private boolean isHeadMaterial(Material material) {
        return material == Material.PLAYER_HEAD || material == Material.PLAYER_WALL_HEAD;
    }

    private Material getMaterial(String materialName) {
        Material material = Material.matchMaterial(materialName);
        return material != null ? material : Material.PAPER;
    }

    private boolean isStyleActive(String style, String currentStyle) {
        if (style == null || style.isEmpty()) {
            return currentStyle == null || currentStyle.isEmpty();
        }
        return style.equals(currentStyle);
    }

    private void setPersistentData(ItemMeta meta, String key, String value) {
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, key), PersistentDataType.STRING, value);
    }

    private String resolvePlaceholders(Player player, String text) {
        if (text == null || text.isEmpty()) return text;
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }
}