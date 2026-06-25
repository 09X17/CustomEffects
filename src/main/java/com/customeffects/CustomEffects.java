package com.customeffects;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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

            InputStream jarResourceStream = this.getResource("config.yml");
            if (jarResourceStream == null) {
                this.getLogger().warning("No se encontró config.yml en el jar.");
                return;
            }

            try (InputStreamReader jarReader = new InputStreamReader(jarResourceStream, StandardCharsets.UTF_8)) {
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

                InputStream readerStream = this.getResource("config.yml");
                if (readerStream == null) {
                    this.getLogger().warning("No se pudo releer config.yml del jar.");
                    return;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(readerStream, StandardCharsets.UTF_8));
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                             new FileOutputStream(configFile, true), StandardCharsets.UTF_8))) {

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
        } catch (IOException e) {
            this.getLogger().log(Level.SEVERE, "Error al actualizar config.yml: {0}", e.getMessage());
        }
    }

    public void loadPluginData() {
        this.categoryEffects.clear();
        this.effectData.clear();

        this.mainMenuTitle = ColorUtils.translate(cfg("main-menu.title", "&8Categorías de Efectos"));
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
                this.getLogger().log(Level.WARNING,
                        "No se encontró el archivo de categoría: categories/{0}.yml", categoryKey);
                continue;
            }

            YamlConfiguration categoryConfig = YamlConfiguration.loadConfiguration(categoryFile);
            List<String> effectIDs = new ArrayList<>();
            Map<String, Map<String, String>> categoryEffectMap = new HashMap<>();

            for (String effectKey : categoryConfig.getKeys(false)) {
                if (!categoryConfig.isConfigurationSection(effectKey)) continue;
                if (!categoryConfig.getBoolean(effectKey + ".enable", true)) continue;

                effectIDs.add(effectKey);

                Map<String, String> effectInfo = new HashMap<>();
                effectInfo.put("display",          cfgSection(categoryConfig, effectKey + ".display", effectKey));
                effectInfo.put("hex",               cfgSection(categoryConfig, effectKey + ".hex", "#FFFFFF"));
                effectInfo.put("permission",        cfgSection(categoryConfig, effectKey + ".permission", ""));
                effectInfo.put("preview",           cfgSection(categoryConfig, effectKey + ".preview", effectKey));
                effectInfo.put("material",          cfgSection(categoryConfig, effectKey + ".material", "PAPER"));
                effectInfo.put("custom-model-data", cfgSection(categoryConfig, effectKey + ".custom-model-data", ""));
                effectInfo.put("slot",              cfgSection(categoryConfig, effectKey + ".slot", ""));
                effectInfo.put("skull-value",       cfgSection(categoryConfig, effectKey + ".skull-value", ""));
                effectInfo.put("has-custom-lore",   categoryConfig.contains(effectKey + ".lore") ? "true" : "false");

                categoryEffectMap.put(effectKey, effectInfo);
                ++totalEfectos;
            }

            this.categoryEffects.put(categoryKey, effectIDs);
            this.effectData.put(categoryKey, categoryEffectMap);
        }

        Logger logger = this.getLogger();
        logger.log(Level.INFO,
                "Se han indexado {0} categorías con un total de {1} efectos.",
                new Object[]{ this.categoryEffects.size(), totalEfectos });
    }

    private void saveCategoryFiles() {
        File categoriesFolder = new File(this.getDataFolder(), "categories");
        if (!categoriesFolder.exists() && !categoriesFolder.mkdirs()) { 
            this.getLogger().severe("No se pudo crear la carpeta 'categories'.");
            return;
        }

        for (String fileName : new String[]{ "rainbows.yml", "basic_colors.yml", "mechanics.yml" }) {
            File file = new File(categoriesFolder, fileName);
            if (!file.exists()) {
                this.saveResource("categories/" + fileName, false);
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
}