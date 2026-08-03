package com.customeffects.services;

import com.customeffects.models.Category;
import com.customeffects.models.Effect;
import com.customeffects.models.Subcategory;
import com.customeffects.models.VoucherConfig;
import com.customeffects.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DataManager {
    private final JavaPlugin plugin;
    private final Map<String, Category> categories = new HashMap<>();
    private String mainMenuTitle;
    private int mainMenuSize;
    private int subMenuSize;
    private int effectsPerPage;

    public DataManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        categories.clear();
        loadConfiguration();
        loadCategories();
    }

    private void loadConfiguration() {
        mainMenuTitle = ColorUtils.translate(cfg("main-menu.title", "&8Categorías de Efectos"));
        mainMenuSize = plugin.getConfig().getInt("main-menu.size", 27);
        subMenuSize = plugin.getConfig().getInt("subcategory-menu.size", 54);
        effectsPerPage = plugin.getConfig().getInt("subcategory-menu.effects-per-page", 45);
    }

    private void loadCategories() {
        ConfigurationSection categoriesSection = plugin.getConfig().getConfigurationSection("main-menu.categories");
        if (categoriesSection == null) {
            plugin.getLogger().warning("No se encontró la sección 'main-menu.categories' en la config.yml");
            return;
        }

        int totalEffects = 0;
        int totalSubcategories = 0;
        File categoriesFolder = new File(plugin.getDataFolder(), "categories");

        for (String categoryKey : categoriesSection.getKeys(false)) {
            File categoryFile = new File(categoriesFolder, categoryKey + ".yml");
            if (!categoryFile.exists()) {
                plugin.getLogger().log(Level.WARNING, "No se encontró el archivo de categoría: categories/{0}.yml", categoryKey);
                continue;
            }

            YamlConfiguration categoryConfig = YamlConfiguration.loadConfiguration(categoryFile);
            String path = "main-menu.categories." + categoryKey + ".";
            
            boolean isPrefix = "prefix".equals(cfg(path + "type", "effect"));
            boolean enabled = plugin.getConfig().getBoolean(path + "enable", true);
            String display = cfg(path + "display", categoryKey);
            String displaySubmenu = cfg(path + "display-submenu", display);
            Material material = Material.matchMaterial(cfg(path + "material", "BOOK"));
            if (material == null) material = Material.BOOK;
            int customModelData = plugin.getConfig().getInt(path + "custom-model-data", 0);
            int slot = plugin.getConfig().getInt(path + "slot", 0);
            String skullValue = cfg(path + "skull-value", "");
            List<String> lore = plugin.getConfig().getStringList(path + "lore");

            List<Subcategory> subcategories = loadSubcategories(categoryConfig, categoryKey);
            totalSubcategories += subcategories.size();

            List<Effect> effects = loadEffects(categoryConfig, categoryKey, subcategories);
            totalEffects += effects.size();

            Category category = new Category(
                categoryKey, display, displaySubmenu, material, customModelData,
                slot, skullValue, enabled, isPrefix, lore, subcategories, effects
            );
            categories.put(categoryKey, category);
        }

        Logger logger = plugin.getLogger();
        logger.log(Level.INFO, "Se han indexado {0} categorías, {1} subcategorías y {2} efectos.",
                new Object[] { categories.size(), totalSubcategories, totalEffects });
    }

    private List<Subcategory> loadSubcategories(YamlConfiguration categoryConfig, String categoryKey) {
        List<Subcategory> subcategories = new ArrayList<>();
        ConfigurationSection subcatsSection = categoryConfig.getConfigurationSection("subcategories");
        if (subcatsSection == null) return subcategories;

        for (String subcatKey : subcatsSection.getKeys(false)) {
            if (!subcatsSection.isConfigurationSection(subcatKey)) continue;
            if (!subcatsSection.getBoolean(subcatKey + ".enable", true)) continue;

            String display = cfgSection(subcatsSection, subcatKey + ".display", subcatKey);
            Material material = Material.matchMaterial(cfgSection(subcatsSection, subcatKey + ".material", "PAPER"));
            if (material == null) material = Material.PAPER;
            int customModelData = parseInt(cfgSection(subcatsSection, subcatKey + ".custom-model-data", "0"));
            int slot = parseInt(cfgSection(subcatsSection, subcatKey + ".slot", "0"));
            String skullValue = cfgSection(subcatsSection, subcatKey + ".skull-value", "");
            List<String> lore = subcatsSection.getStringList(subcatKey + ".lore");

            subcategories.add(new Subcategory(subcatKey, display, material, customModelData, slot, skullValue, true, lore, new ArrayList<>()));
        }
        return subcategories;
    }

    private List<Effect> loadEffects(YamlConfiguration categoryConfig, String categoryKey, List<Subcategory> subcategories) {
        List<Effect> effects = new ArrayList<>();
        Map<String, List<Effect>> subcategoryEffectsMap = new HashMap<>();
        subcategories.forEach(s -> subcategoryEffectsMap.put(s.getId(), new ArrayList<>()));

        for (String effectKey : categoryConfig.getKeys(false)) {
            if (effectKey.equals("subcategories")) continue;
            if (!categoryConfig.isConfigurationSection(effectKey)) continue;
            if (!categoryConfig.getBoolean(effectKey + ".enable", true)) continue;

            String display = cfgSection(categoryConfig, effectKey + ".display", effectKey);
            String hex = cfgSection(categoryConfig, effectKey + ".hex", "#FFFFFF");
            String prefix = cfgSection(categoryConfig, effectKey + ".prefix", "");
            List<String> prefixFrames = categoryConfig.contains(effectKey + ".prefix.frames") 
                    ? categoryConfig.getStringList(effectKey + ".prefix.frames") 
                    : new ArrayList<>();
            int prefixSpeed = parseInt(cfgSection(categoryConfig, effectKey + ".prefix.speed", "10"));
            boolean prefixSmooth = categoryConfig.getBoolean(effectKey + ".prefix.smooth", false);
            int prefixTransitionFrames = parseInt(cfgSection(categoryConfig, effectKey + ".prefix.transition-frames", "5"));
            String permission = cfgSection(categoryConfig, effectKey + ".permission", "");
            String preview = cfgSection(categoryConfig, effectKey + ".preview", effectKey);
            Material material = Material.matchMaterial(cfgSection(categoryConfig, effectKey + ".material", "PAPER"));
            if (material == null) material = Material.PAPER;
            int customModelData = parseInt(cfgSection(categoryConfig, effectKey + ".custom-model-data", "0"));
            Map<String, Integer> rankModelData = loadRankModelData(categoryConfig, effectKey);
            int slot = parseInt(cfgSection(categoryConfig, effectKey + ".slot", "0"));
            String skullValue = cfgSection(categoryConfig, effectKey + ".skull-value", "");
            String group = cfgSection(categoryConfig, effectKey + ".group", "all");
            List<String> lore = categoryConfig.contains(effectKey + ".lore") 
                    ? categoryConfig.getStringList(effectKey + ".lore") 
                    : new ArrayList<>();

            VoucherConfig voucherConfig = loadVoucherConfig(categoryConfig, effectKey);
            Effect effect = new Effect(effectKey, display, hex, prefix, prefixFrames, prefixSpeed, 
                    prefixSmooth, prefixTransitionFrames,
                    permission, preview, material, customModelData, rankModelData, slot, skullValue, group, true, lore, voucherConfig);

            String subcategory = categoryConfig.getString(effectKey + ".subcategory", "");
            if (subcategory != null && !subcategory.isEmpty() && subcategoryEffectsMap.containsKey(subcategory)) {
                subcategoryEffectsMap.get(subcategory).add(effect);
            } else {
                effects.add(effect);
            }
        }

        subcategories.forEach(s -> {
            List<Effect> subEffects = subcategoryEffectsMap.get(s.getId());
            if (subEffects != null) {
                s.getEffects().addAll(subEffects);
            }
        });

        return effects;
    }

    private VoucherConfig loadVoucherConfig(YamlConfiguration categoryConfig, String effectId) {
        String path = effectId + ".voucher";
        if (categoryConfig.contains(path)) {
            Material material = Material.valueOf(cfgSection(categoryConfig, path + ".material", "PAPER"));
            String displayName = cfgSection(categoryConfig, path + ".displayname", "&d&lVoucher");
            List<String> lore = categoryConfig.getStringList(path + ".lore");
            int customModelData = parseInt(cfgSection(categoryConfig, path + ".custom-model-data", "0"));
            return new VoucherConfig(material, displayName, lore, customModelData);
        }

        ConfigurationSection global = plugin.getConfig().getConfigurationSection("global-voucher");
        if (global == null) return null;

        Material material = Material.valueOf(global.getString("material", "PAPER"));
        String displayName = global.getString("displayname", "&d&lVoucher");
        List<String> lore = global.getStringList("lore");
        int customModelData = global.getInt("custom-model-data", 0);
        return new VoucherConfig(material, displayName, lore, customModelData);
    }

    private Map<String, Integer> loadRankModelData(YamlConfiguration categoryConfig, String effectKey) {
        Map<String, Integer> rankModelData = new HashMap<>();
        String path = effectKey + ".model-data";
        ConfigurationSection section = categoryConfig.getConfigurationSection(path);
        if (section != null) {
            for (String rank : section.getKeys(false)) {
                int cmd = section.getInt(rank, 0);
                if (cmd > 0) {
                    rankModelData.put(rank, cmd);
                }
            }
        }
        return rankModelData;
    }

    public void saveCategoryFiles() {
        File categoriesFolder = new File(plugin.getDataFolder(), "categories");
        if (!categoriesFolder.exists() && !categoriesFolder.mkdirs()) {
            plugin.getLogger().severe("No se pudo crear la carpeta 'categories'.");
            return;
        }

        for (String fileName : new String[] { "rainbows.yml", "basic_colors.yml", "mechanics.yml", "prefixes.yml", "friends_menu.yml" }) {
            File file = new File(categoriesFolder, fileName);
            if (!file.exists()) {
                plugin.saveResource("categories/" + fileName, false);
            }
        }
    }

    public void updateCategoryFiles() {
        File categoriesFolder = new File(plugin.getDataFolder(), "categories");
        if (!categoriesFolder.exists()) return;

        for (String fileName : new String[] { "rainbows.yml", "basic_colors.yml", "mechanics.yml", "prefixes.yml", "friends_menu.yml" }) {
            try {
                File userFile = new File(categoriesFolder, fileName);
                if (!userFile.exists()) continue;

                InputStream jarStream = plugin.getResource("categories/" + fileName);
                if (jarStream == null) continue;

                YamlConfiguration jarConfig;
                try (InputStreamReader reader = new InputStreamReader(jarStream, StandardCharsets.UTF_8)) {
                    jarConfig = YamlConfiguration.loadConfiguration(reader);
                }

                YamlConfiguration userConfig = YamlConfiguration.loadConfiguration(userFile);
                List<String> missingKeys = new ArrayList<>();
                for (String key : jarConfig.getKeys(true)) {
                    if (!userConfig.contains(key)) {
                        missingKeys.add(key);
                    }
                }

                if (missingKeys.isEmpty()) continue;

                for (String key : missingKeys) {
                    userConfig.set(key, jarConfig.get(key));
                }

                userConfig.save(userFile);
                plugin.getLogger().info("Actualizado categories/" + fileName + " con " + missingKeys.size() + " nuevas claves.");
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Error al actualizar categories/{0}: {1}",
                        new Object[] { fileName, e.getMessage() });
            }
        }
    }

    public void updateConfigCommentsSafe() {
        try {
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            if (!configFile.exists()) return;

            InputStream jarStream = plugin.getResource("config.yml");
            if (jarStream == null) {
                plugin.getLogger().warning("No se encontró config.yml en el jar.");
                return;
            }

            YamlConfiguration jarConfig;
            try (InputStreamReader reader = new InputStreamReader(jarStream, StandardCharsets.UTF_8)) {
                jarConfig = YamlConfiguration.loadConfiguration(reader);
            }

            YamlConfiguration userConfig = YamlConfiguration.loadConfiguration(configFile);
            List<String> missingKeys = new ArrayList<>();
            for (String key : jarConfig.getKeys(true)) {
                if (!userConfig.contains(key)) {
                    missingKeys.add(key);
                }
            }

            if (missingKeys.isEmpty()) return;

            plugin.getLogger().info("Detectadas " + missingKeys.size() + " nuevas opciones. Inyectando en config.yml...");

            for (String key : missingKeys) {
                userConfig.set(key, jarConfig.get(key));
            }

            userConfig.save(configFile);
            plugin.reloadConfig();
            plugin.getLogger().info("¡config.yml actualizado con " + missingKeys.size() + " nuevas claves!");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al actualizar config.yml: {0}", e.getMessage());
        }
    }

    private String cfg(String path, String def) {
        String value = plugin.getConfig().getString(path, def);
        return value != null ? value : def;
    }

    private static String cfgSection(ConfigurationSection section, String path, String def) {
        String value = section.getString(path, def);
        return value != null ? value : def;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public Category getCategory(String categoryId) {
        return categories.get(categoryId);
    }

    public Collection<Category> getAllCategories() {
        return categories.values();
    }

    public boolean isValidCategory(String categoryId) {
        return categories.containsKey(categoryId);
    }

    public String getMainMenuTitle() { return mainMenuTitle; }
    public int getMainMenuSize() { return mainMenuSize; }
    public int getSubMenuSize() { return subMenuSize; }
    public int getEffectsPerPage() { return effectsPerPage; }
}