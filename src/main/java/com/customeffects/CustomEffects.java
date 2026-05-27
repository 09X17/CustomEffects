package com.customeffects;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.customeffects.database.DatabaseManager;
import com.customeffects.placeholder.EffectosExpansion;
import com.customeffects.utils.ColorUtils;

public final class CustomEffects extends JavaPlugin {
    private DatabaseManager database;
    private String mainMenuTitle;
    private int mainMenuSize;
    private int subMenuSize;
    private final Map<String, List<String>> categoryEffects = new HashMap<>();
    private final Map<String, Map<String, Map<String, String>>> effectData = new HashMap<>();
    private final Map<Player, String> editingPlayers = new HashMap<>();

    public CustomEffects() {
    }

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.saveCategoryFiles();
        this.updateConfigCommentsSafe();
        this.loadPluginData();
        
        this.database = new DatabaseManager(this.getLogger());
        this.database.connect(this.getDataFolder());
        
        // Registro de Listeners 
        this.getServer().getPluginManager().registerEvents(new InventoryClickListener(this), this);
        this.getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        this.getServer().getPluginManager().registerEvents(new VoucherListener(this), this);
        this.getServer().getPluginManager().registerEvents(new EditorListener(this, this.editingPlayers), this);
        
        // Registro de Comando Principal
        EffectosCommand commandExecutor = new EffectosCommand(this);
        if (this.getCommand("effectos") != null) {
            this.getCommand("effectos").setExecutor(commandExecutor);
            this.getCommand("effectos").setTabCompleter(commandExecutor);
        }

        // Integración con PlaceholderAPI
        if (this.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new EffectosExpansion(this).register();
            this.getLogger().info("PlaceholderAPI conectado correctamente.");
        } else {
            this.getLogger().warning("PlaceholderAPI no encontrado. Los placeholders no funcionarán.");
        }

        this.getLogger().info("CustomEffects habilitado con éxito.");
    }

    @Override
    public void onDisable() {
        if (this.database != null) {
            this.database.close();
        }
        this.getLogger().info("CustomEffects deshabilitado y DB desconectada.");
    }

    private void updateConfigCommentsSafe() {
        try {
            File configFile = new File(this.getDataFolder(), "config.yml");
            if (!configFile.exists()) return;

            FileConfiguration userConfig = this.getConfig();

            try (InputStreamReader jarReader = new InputStreamReader(this.getResource("config.yml"), StandardCharsets.UTF_8)) {
                YamlConfiguration jarConfig = YamlConfiguration.loadConfiguration(jarReader);
                
                boolean necesitaActualizacion = false;
                
                for (String key : jarConfig.getKeys(true)) {
                    if (!userConfig.contains(key)) {
                        necesitaActualizacion = true;
                        break;
                    }
                }
                
                if (!necesitaActualizacion) return;
                
                this.getLogger().info("Detectadas nuevas opciones en la actualización. Inyectando sin alterar comentarios...");

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(this.getResource("config.yml"), StandardCharsets.UTF_8));
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFile, true), StandardCharsets.UTF_8))) {
                    
                    String line;
                    String currentSection = "";
                    boolean escribiendoNuevaSeccion = false;

                    writer.newLine();
                    writer.write("# =========================================================");
                    writer.newLine();
                    writer.write("# NUEVAS OPCIONES AÑADIDAS AUTOMÁTICAMENTE EN LA ACTUALIZACIÓN");
                    writer.newLine();
                    writer.write("# =========================================================");
                    writer.newLine();

                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        
                        if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                            if (escribiendoNuevaSeccion) {
                                writer.write(line);
                                writer.newLine();
                            }
                            continue;
                        }

                        if (line.matches("^[a-zA-Z_-]+:.*")) {
                            currentSection = line.split(":")[0];
                            escribiendoNuevaSeccion = !userConfig.contains(currentSection);
                        } else if (trimmed.contains(":") && !escribiendoNuevaSeccion) {

                            String fullPath = currentSection + "." + trimmed.split(":")[0];
                            if (userConfig.contains(fullPath)) {
                                escribiendoNuevaSeccion = false;
                            } else {
                                String buildPath = line.contains(":") ? line.split(":")[0].trim() : "";
                                escribiendoNuevaSeccion = !userConfig.contains(buildPath);
                            }
                        }

                        if (escribiendoNuevaSeccion) {
                            writer.write(line);
                            writer.newLine();
                        }
                    }
                }
                
                this.reloadConfig();
                this.getLogger().info("¡Archivo config.yml actualizado con éxito!");
            }
        } catch (Exception e) {
            this.getLogger().severe("No se pudo procesar la actualización automática de la config: " + e.getMessage());
        }
    }

    public void loadPluginData() {
        this.categoryEffects.clear();
        this.effectData.clear();
        this.mainMenuTitle = ColorUtils.translate(this.getConfig().getString("main-menu.title", "&8Categorías de Efectos"));
        this.mainMenuSize = this.getConfig().getInt("main-menu.size", 27);
        this.subMenuSize = this.getConfig().getInt("subcategory-menu.size", 54);
        
        ConfigurationSection categoriesSection = this.getConfig().getConfigurationSection("main-menu.categories");
        if (categoriesSection == null) {
            this.getLogger().warning("No se encontró la sección 'main-menu.categories' en la config.yml");
            return;
        }

        int totalEfectos = 0;
        File categoriesFolder = new File(this.getDataFolder(), "categories");

        for (String categoryKey : categoriesSection.getKeys(false)) {
            File categoryFile = new File(categoriesFolder, categoryKey + ".yml");
            if (!categoryFile.exists()) {
                this.getLogger().warning("No se encontró el archivo de categoría: categories/" + categoryKey + ".yml");
                continue;
            }

            YamlConfiguration categoryConfig = YamlConfiguration.loadConfiguration(categoryFile);
            List<String> effectIDs = new ArrayList<>();
            Map<String, Map<String, String>> categoryEffectMap = new HashMap<>();

            for (String effectKey : categoryConfig.getKeys(false)) {
                if (categoryConfig.isConfigurationSection(effectKey)) {
                    if (!categoryConfig.getBoolean(effectKey + ".enable", true)) {
                        continue;
                    }

                    effectIDs.add(effectKey);
                    Map<String, String> effectInfo = new HashMap<>();
                    effectInfo.put("display", categoryConfig.getString(effectKey + ".display", effectKey));
                    effectInfo.put("hex", categoryConfig.getString(effectKey + ".hex", "#FFFFFF"));
                    effectInfo.put("permission", categoryConfig.getString(effectKey + ".permission", ""));
                    effectInfo.put("preview", categoryConfig.getString(effectKey + ".preview", effectKey));
                    effectInfo.put("material", categoryConfig.getString(effectKey + ".material", "PAPER"));
                    effectInfo.put("custom-model-data", categoryConfig.getString(effectKey + ".custom-model-data", ""));
                    effectInfo.put("slot", categoryConfig.getString(effectKey + ".slot", ""));
                    effectInfo.put("skull-value", categoryConfig.getString(effectKey + ".skull-value", ""));
                    
                    if (categoryConfig.contains(effectKey + ".lore")) {
                        effectInfo.put("has-custom-lore", "true");
                    } else {
                        effectInfo.put("has-custom-lore", "false");
                    }
                    
                    categoryEffectMap.put(effectKey, effectInfo);
                    ++totalEfectos;
                }
            }

            this.categoryEffects.put(categoryKey, effectIDs);
            this.effectData.put(categoryKey, categoryEffectMap);
        }

        Logger logger = this.getLogger();
        int totalCategorias = this.categoryEffects.size();
        logger.info("Se han indexado " + totalCategorias + " categorías con un total de " + totalEfectos + " efectos.");
    }

    private void saveCategoryFiles() {
        File categoriesFolder = new File(this.getDataFolder(), "categories");
        if (!categoriesFolder.exists()) {
            categoriesFolder.mkdirs();
        }

        String[] categoryFiles = {"rainbows.yml", "basic_colors.yml", "mechanics.yml"};
        for (String fileName : categoryFiles) {
            File file = new File(categoriesFolder, fileName);
            if (!file.exists()) {
                this.saveResource("categories/" + fileName, false);
            }
        }
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

    public List<String> getDefaultLore() {
        return this.getConfig().getStringList("default-lore");
    }

    public Map<Player, String> getEditingPlayers() {
        return this.editingPlayers;
    }
}