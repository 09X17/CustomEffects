package com.customeffects;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.customeffects.database.DatabaseManager;
import com.customeffects.editor.RankEditorCommand;
import com.customeffects.editor.WebEditorServer;
import com.customeffects.placeholder.EffectosExpansion;
import com.customeffects.utils.ColorUtils;

public final class CustomEffects extends JavaPlugin {
    private DatabaseManager database;
    private String mainMenuTitle;
    private int mainMenuSize;
    private int subMenuSize;
    private int effectsPerPage;
    private final Map<String, List<String>> categoryEffects = new HashMap<>();
    private final Map<String, Map<String, Map<String, String>>> effectData = new HashMap<>();
    private final Map<String, List<String>> categorySubcategories = new HashMap<>();
    private final Map<String, Map<String, Map<String, String>>> subcategoryData = new HashMap<>();
    private final Map<String, Map<String, List<String>>> subcategoryEffects = new HashMap<>();
    private final List<String> prefixCategories = new ArrayList<>();
    private WebEditorServer webEditorServer;

    public CustomEffects() {
    }

    private String cfg(String path, String def) {
        String value = this.getConfig().getString(path, def);
        return value != null ? value : def;
    }

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.saveCategoryFiles();
        this.updateConfigCommentsSafe();
        this.updateCategoryFiles();
        this.loadPluginData();

        this.database = new DatabaseManager();
        this.database.connect(this.getDataFolder(), this);

        this.getServer().getPluginManager().registerEvents(new InventoryClickListener(this), this);
        this.getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        this.getServer().getPluginManager().registerEvents(new VoucherListener(this), this);

        EffectosCommand commandExecutor = new EffectosCommand(this);
        var command = this.getCommand("effectos");
        if (command != null) {
            command.setExecutor(commandExecutor);
            command.setTabCompleter(commandExecutor);
        } else {
            this.getLogger().severe("El comando 'effectos' no está registrado en plugin.yml");
        }

        FormatoCommand formatoCommand = new FormatoCommand(this);
        var formatoCmd = this.getCommand("formato");
        if (formatoCmd != null) {
            formatoCmd.setExecutor(formatoCommand);
        } else {
            this.getLogger().severe("El comando 'formato' no está registrado en plugin.yml");
        }

        RankEditorCommand rankEditorCommand = new RankEditorCommand(this);
        var rankEditorCmd = this.getCommand("rankeditor");
        if (rankEditorCmd != null) {
            rankEditorCmd.setExecutor(rankEditorCommand);
            rankEditorCmd.setTabCompleter(rankEditorCommand);
        } else {
            this.getLogger().severe("El comando 'rankeditor' no está registrado en plugin.yml");
        }

        if (this.getConfig().getBoolean("rank-editor.enable", true)) {
            int port = this.getConfig().getInt("rank-editor.port", 25580);
            String bind = this.getConfig().getString("rank-editor.bind-address", "0.0.0.0");
            boolean https = this.getConfig().getBoolean("rank-editor.use-https", true);
            this.webEditorServer = new WebEditorServer(this);
            this.webEditorServer.start(port, bind, https);
        }

        if (this.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new EffectosExpansion(this).register();
            this.getLogger().info("PlaceholderAPI conectado correctamente.");
        } else {
            this.getLogger().warning("PlaceholderAPI no encontrado. Los placeholders no funcionarán.");
        }

        this.getLogger().info("CustomEffects habilitado con éxito.");
    }

    @Override
    public void onDisable() {
        if (this.webEditorServer != null) {
            this.webEditorServer.stop();
        }
        if (this.database != null) {
            this.database.close();
        }
        this.getLogger().info("CustomEffects deshabilitado y DB desconectada.");
    }

    private void updateConfigCommentsSafe() {
        try {
            File configFile = new File(this.getDataFolder(), "config.yml");
            if (!configFile.exists())
                return;

            InputStream jarStream = this.getResource("config.yml");
            if (jarStream == null) {
                this.getLogger().warning("No se encontró config.yml en el jar.");
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

            if (missingKeys.isEmpty())
                return;

            this.getLogger().info("Detectadas " + missingKeys.size()
                    + " nuevas opciones. Inyectando en config.yml...");

            for (String key : missingKeys) {
                Object jarValue = jarConfig.get(key);
                userConfig.set(key, jarValue);
            }

            userConfig.save(configFile);
            this.reloadConfig();

            this.getLogger().info("¡config.yml actualizado con " + missingKeys.size() + " nuevas claves!");
        } catch (IOException e) {
            this.getLogger().log(Level.SEVERE, "Error al actualizar config.yml: {0}", e.getMessage());
        }
    }

    public void loadPluginData() {
        this.categoryEffects.clear();
        this.effectData.clear();
        this.categorySubcategories.clear();
        this.subcategoryData.clear();
        this.subcategoryEffects.clear();
        this.prefixCategories.clear();

        this.mainMenuTitle = ColorUtils.translate(cfg("main-menu.title", "&8Categorías de Efectos"));
        this.mainMenuSize = this.getConfig().getInt("main-menu.size", 27);
        this.subMenuSize = this.getConfig().getInt("subcategory-menu.size", 54);
        this.effectsPerPage = this.getConfig().getInt("subcategory-menu.effects-per-page", 45);

        ConfigurationSection categoriesSection = this.getConfig().getConfigurationSection("main-menu.categories");
        if (categoriesSection == null) {
            this.getLogger().warning("No se encontró la sección 'main-menu.categories' en la config.yml");
            return;
        }

        int totalEfectos = 0;
        int totalSubcategorias = 0;
        File categoriesFolder = new File(this.getDataFolder(), "categories");

        for (String categoryKey : categoriesSection.getKeys(false)) {
            String categoryType = cfg("main-menu.categories." + categoryKey + ".type", "effect");
            if ("prefix".equals(categoryType)) {
                prefixCategories.add(categoryKey);
            }

            File categoryFile = new File(categoriesFolder, categoryKey + ".yml");
            if (!categoryFile.exists()) {
                this.getLogger().log(Level.WARNING,
                        "No se encontró el archivo de categoría: categories/{0}.yml", categoryKey);
                continue;
            }

            YamlConfiguration categoryConfig = YamlConfiguration.loadConfiguration(categoryFile);
            List<String> effectIDs = new ArrayList<>();
            Map<String, Map<String, String>> categoryEffectMap = new HashMap<>();

            // Parse subcategories section if present
            ConfigurationSection subcatsSection = categoryConfig.getConfigurationSection("subcategories");
            List<String> subcategoryKeys = new ArrayList<>();
            Map<String, Map<String, String>> subcatDataMap = new HashMap<>();
            Map<String, List<String>> subcatEffectsMap = new HashMap<>();

            if (subcatsSection != null) {
                for (String subcatKey : subcatsSection.getKeys(false)) {
                    if (!subcatsSection.isConfigurationSection(subcatKey))
                        continue;
                    if (!subcatsSection.getBoolean(subcatKey + ".enable", true))
                        continue;

                    subcategoryKeys.add(subcatKey);
                    totalSubcategorias++;

                    Map<String, String> subcatInfo = new HashMap<>();
                    subcatInfo.put("display", cfgSection(subcatsSection, subcatKey + ".display", subcatKey));
                    subcatInfo.put("material", cfgSection(subcatsSection, subcatKey + ".material", "PAPER"));
                    subcatInfo.put("custom-model-data",
                            cfgSection(subcatsSection, subcatKey + ".custom-model-data", ""));
                    subcatInfo.put("slot", cfgSection(subcatsSection, subcatKey + ".slot", ""));
                    subcatInfo.put("skull-value", cfgSection(subcatsSection, subcatKey + ".skull-value", ""));
                    subcatDataMap.put(subcatKey, subcatInfo);
                    subcatEffectsMap.put(subcatKey, new ArrayList<>());
                }
            }

            // Parse effects
            for (String effectKey : categoryConfig.getKeys(false)) {
                if (effectKey.equals("subcategories"))
                    continue;
                if (!categoryConfig.isConfigurationSection(effectKey))
                    continue;
                if (!categoryConfig.getBoolean(effectKey + ".enable", true))
                    continue;

                Map<String, String> effectInfo = new HashMap<>();
                effectInfo.put("display", cfgSection(categoryConfig, effectKey + ".display", effectKey));
                effectInfo.put("hex", cfgSection(categoryConfig, effectKey + ".hex", "#FFFFFF"));
                effectInfo.put("permission", cfgSection(categoryConfig, effectKey + ".permission", ""));
                effectInfo.put("preview", cfgSection(categoryConfig, effectKey + ".preview", effectKey));
                effectInfo.put("material", cfgSection(categoryConfig, effectKey + ".material", "PAPER"));
                effectInfo.put("custom-model-data", cfgSection(categoryConfig, effectKey + ".custom-model-data", ""));
                effectInfo.put("slot", cfgSection(categoryConfig, effectKey + ".slot", ""));
                effectInfo.put("skull-value", cfgSection(categoryConfig, effectKey + ".skull-value", ""));
                effectInfo.put("group", cfgSection(categoryConfig, effectKey + ".group", "all"));
                effectInfo.put("has-custom-lore", categoryConfig.contains(effectKey + ".lore") ? "true" : "false");

                String subcategory = categoryConfig.getString(effectKey + ".subcategory", "");
                if (subcategory != null && !subcategory.isEmpty() && subcatEffectsMap.containsKey(subcategory)) {
                    subcatEffectsMap.get(subcategory).add(effectKey);
                } else {
                    effectIDs.add(effectKey);
                }

                categoryEffectMap.put(effectKey, effectInfo);
                ++totalEfectos;
            }

            this.categoryEffects.put(categoryKey, effectIDs);
            this.effectData.put(categoryKey, categoryEffectMap);
            this.categorySubcategories.put(categoryKey, subcategoryKeys);
            this.subcategoryData.put(categoryKey, subcatDataMap);
            this.subcategoryEffects.put(categoryKey, subcatEffectsMap);
        }

        Logger logger = this.getLogger();
        logger.log(Level.INFO,
                "Se han indexado {0} categorías, {2} subcategorías y un total de {1} efectos.",
                new Object[] { this.categoryEffects.size(), totalEfectos, totalSubcategorias });
    }

    private void saveCategoryFiles() {
        File categoriesFolder = new File(this.getDataFolder(), "categories");
        if (!categoriesFolder.exists() && !categoriesFolder.mkdirs()) {
            this.getLogger().severe("No se pudo crear la carpeta 'categories'.");
            return;
        }

        for (String fileName : new String[] { "rainbows.yml", "basic_colors.yml", "mechanics.yml", "prefixes.yml" }) {
            File file = new File(categoriesFolder, fileName);
            if (!file.exists()) {
                this.saveResource("categories/" + fileName, false);
            }
        }
    }

    private void updateCategoryFiles() {
        File categoriesFolder = new File(this.getDataFolder(), "categories");
        if (!categoriesFolder.exists())
            return;

        for (String fileName : new String[] { "rainbows.yml", "basic_colors.yml", "mechanics.yml", "prefixes.yml" }) {
            try {
                File userFile = new File(categoriesFolder, fileName);
                if (!userFile.exists())
                    continue;

                InputStream jarStream = this.getResource("categories/" + fileName);
                if (jarStream == null)
                    continue;

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

                if (missingKeys.isEmpty())
                    continue;

                for (String key : missingKeys) {
                    userConfig.set(key, jarConfig.get(key));
                }

                userConfig.save(userFile);
                this.getLogger().info("Actualizado categories/" + fileName + " con " + missingKeys.size() + " nuevas claves.");
            } catch (IOException e) {
                this.getLogger().log(Level.WARNING, "Error al actualizar categories/{0}: {1}",
                        new Object[] { fileName, e.getMessage() });
            }
        }
    }

    private static String cfgSection(org.bukkit.configuration.ConfigurationSection section, String path, String def) {
        String value = section.getString(path, def);
        return value != null ? value : def;
    }

    public void loadEffects() {
        this.loadPluginData();
    }

    public DatabaseManager getDatabase() {
        return this.database;
    }

    public List<String> getEffectsByCategory(String category) {
        return this.categoryEffects.getOrDefault(category, new ArrayList<>());
    }

    public boolean isValidCategory(String category) {
        return this.categoryEffects.containsKey(category);
    }

    public String getMainMenuTitle() {
        return this.mainMenuTitle;
    }

    public int getMainMenuSize() {
        return this.mainMenuSize;
    }

    public int getSubMenuSize() {
        return this.subMenuSize;
    }

    public int getEffectsPerPage() {
        return this.effectsPerPage;
    }

    public boolean hasSubcategories(String category) {
        List<String> subcats = this.categorySubcategories.getOrDefault(category, new ArrayList<>());
        return !subcats.isEmpty();
    }

    public List<String> getSubcategories(String category) {
        return this.categorySubcategories.getOrDefault(category, new ArrayList<>());
    }

    public Map<String, String> getSubcategoryInfo(String category, String subcategory) {
        return this.subcategoryData.getOrDefault(category, new HashMap<>())
                .getOrDefault(subcategory, new HashMap<>());
    }

    public List<String> getEffectsBySubcategory(String category, String subcategory) {
        return this.subcategoryEffects.getOrDefault(category, new HashMap<>())
                .getOrDefault(subcategory, new ArrayList<>());
    }

    public String getEffectDisplay(String effectId) {
        for (Map<String, Map<String, String>> categoryMap : this.effectData.values()) {
            if (categoryMap.containsKey(effectId)) {
                return categoryMap.get(effectId).getOrDefault("display", effectId);
            }
        }
        return effectId;
    }

    public String getEffectDisplay(String effectId, String category) {
        if (this.effectData.containsKey(category) && this.effectData.get(category).containsKey(effectId)) {
            return this.effectData.get(category).get(effectId).getOrDefault("display", effectId);
        }
        return effectId;
    }

    public Map<String, String> getEffectInfo(String effectId, String category) {
        if (this.effectData.containsKey(category) && this.effectData.get(category).containsKey(effectId)) {
            return this.effectData.get(category).get(effectId);
        }
        return new HashMap<>();
    }

    public YamlConfiguration getCategoryConfig(String category) {
        File categoryFile = new File(this.getDataFolder(), "categories/" + category + ".yml");
        if (categoryFile.exists()) {
            return YamlConfiguration.loadConfiguration(categoryFile);
        }
        return null;
    }

    public boolean isPrefixCategory(String category) {
        return this.prefixCategories.contains(category);
    }

    public String getPrefixValue(String prefixId, String category) {
        YamlConfiguration categoryConfig = getCategoryConfig(category);
        if (categoryConfig != null) {
            return categoryConfig.getString(prefixId + ".prefix", "");
        }
        return "";
    }

    public List<String> getDefaultLore() {
        return this.getConfig().getStringList("default-lore");
    }

    public WebEditorServer getWebEditorServer() {
        return this.webEditorServer;
    }

    public String getLuckPermsPrefix(Player player) {
        try {
            if (this.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
                return "";
            }
            net.luckperms.api.LuckPerms luckPerms = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null)
                return "";

            String prefixRaw = user.getCachedData().getMetaData().getPrefix();

            return prefixRaw != null && !prefixRaw.isEmpty()
                    ? ColorUtils.translate(prefixRaw)
                    : "";
        } catch (Exception ignored) {
        }
        return "";
    }

    public String getPlayerGroup(Player player) {
        try {
            if (this.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
                return "default";
            }
            net.luckperms.api.LuckPerms luckPerms = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) return "default";
            String primaryGroup = user.getPrimaryGroup();
            return primaryGroup != null ? primaryGroup : "default";
        } catch (Exception e) {
            return "default";
        }
    }

    public boolean canPlayerSeePrefix(Player player, String prefixGroup) {
        if (prefixGroup == null || prefixGroup.isEmpty() || "all".equals(prefixGroup)) {
            return true;
        }
        String playerGroup = getPlayerGroup(player);
        if ("owner".equals(playerGroup)) {
            return true;
        }
        List<String> hierarchy = this.getConfig().getStringList("prefix-system.rank-hierarchy");
        if (hierarchy.isEmpty()) {
            hierarchy = List.of("default", "nova", "nexus", "void", "mod", "admin", "owner");
        }
        int playerIndex = hierarchy.indexOf(playerGroup);
        int prefixIndex = hierarchy.indexOf(prefixGroup);
        if (playerIndex == -1 || prefixIndex == -1) {
            return playerGroup.equals(prefixGroup);
        }
        return playerIndex >= prefixIndex;
    }
}