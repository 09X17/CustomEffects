package com.customeffects.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;

import java.util.logging.Logger;

import com.customeffects.CustomEffects;

public class DatabaseManager {
    private Connection connection;
    private final Map<UUID, PlayerData> cache = new HashMap();
    private final Logger logger;

    public DatabaseManager(Logger logger) {
        this.logger = logger;
    }

    public void connect(File folder) {
        try {
            if (!folder.exists()) {
                folder.mkdirs();
            }

            File db = new File(folder, "data.db");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());

            try (PreparedStatement statement = this.connection.prepareStatement("CREATE TABLE IF NOT EXISTS player_effects (uuid TEXT PRIMARY KEY,effect_id TEXT NOT NULL,hex TEXT NOT NULL,last_used BIGINT DEFAULT 0);")) {
                statement.executeUpdate();
            }

            this.logger.info("SQLite conectado y sistema de caché listo.");
        } catch (Exception e) {
            this.logger.severe("Error al conectar SQLite: " + e.getMessage());
        }

    }

    public void loadPlayerData(UUID uuid) {
        try (PreparedStatement statement = this.connection.prepareStatement("SELECT effect_id, hex, last_used FROM player_effects WHERE uuid=?")) {
            statement.setString(1, uuid.toString());

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    this.cache.put(uuid, new PlayerData(rs.getString("effect_id"), rs.getString("hex"), rs.getLong("last_used")));
                }
            }
        } catch (Exception e) {
            this.logger.warning("Error al cargar datos del jugador " + uuid + ": " + e.getMessage());
        }

    }

    public void unloadPlayerData(UUID uuid) {
        this.cache.remove(uuid);
    }

    public void setEffect(UUID uuid, String effectId, CustomEffects plugin) {
        if (effectId != null && !effectId.isEmpty()) {
            String hexValue = "#FFFFFF";
            ConfigurationSection section = plugin.getConfig().getConfigurationSection("effects");
            if (section != null) {
                for(String category : section.getKeys(false)) {
                    String path = "effects." + category + "." + effectId + ".hex";
                    if (plugin.getConfig().contains(path)) {
                        hexValue = plugin.getConfig().getString(path, "#FFFFFF");
                        break;
                    }
                }
            }

            this.saveEffect(uuid, effectId, hexValue);
        } else {
            this.removeEffect(uuid);
        }
    }

    public void saveEffect(UUID uuid, String effect, String hex) {
        long now = System.currentTimeMillis();
        this.cache.put(uuid, new PlayerData(effect, hex, now));

        try (PreparedStatement statement = this.connection.prepareStatement("INSERT OR REPLACE INTO player_effects (uuid,effect_id,hex,last_used) VALUES(?,?,?,?)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, effect);
            statement.setString(3, hex);
            statement.setLong(4, now);
            statement.executeUpdate();
        } catch (Exception e) {
            this.logger.warning("Error al guardar efecto para " + uuid + ": " + e.getMessage());
        }

    }

    public void removeEffect(UUID uuid) {
        this.cache.remove(uuid);

        try (PreparedStatement statement = this.connection.prepareStatement("DELETE FROM player_effects WHERE uuid=?")) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (Exception e) {
            this.logger.warning("Error al eliminar efecto de " + uuid + ": " + e.getMessage());
        }

    }

    public String getActiveEffect(UUID uuid) {
        PlayerData data = (PlayerData)this.cache.get(uuid);
        if (data != null) {
            return data.effectId().isEmpty() ? null : data.effectId();
        } else {
            try {
                String var6;
                try (PreparedStatement statement = this.connection.prepareStatement("SELECT effect_id FROM player_effects WHERE uuid=?")) {
                    statement.setString(1, uuid.toString());

                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) {
                            return null;
                        }

                        String effectId = rs.getString("effect_id");
                        var6 = effectId != null && !effectId.isEmpty() ? effectId : null;
                    }
                }

                return var6;
            } catch (Exception e) {
                this.logger.warning("Error al obtener efecto activo de " + uuid + ": " + e.getMessage());
                return null;
            }
        }
    }

    public String getHex(UUID uuid) {
        PlayerData data = (PlayerData)this.cache.get(uuid);
        return data != null ? data.hex() : "#FFFFFF";
    }

    public String getEffect(UUID uuid) {
        PlayerData data = (PlayerData)this.cache.get(uuid);
        return data != null ? data.effectId() : "";
    }

    public boolean hasEffect(UUID uuid) {
        return this.cache.containsKey(uuid);
    }

    public long getLastUsed(UUID uuid) {
        PlayerData data = (PlayerData)this.cache.get(uuid);
        return data != null ? data.lastUsed() : 0L;
    }

    public void close() {
        try {
            if (this.connection != null && !this.connection.isClosed()) {
                this.connection.close();
                this.logger.info("SQLite cerrado de manera limpia.");
            }
        } catch (Exception e) {
            this.logger.severe("Error al cerrar SQLite: " + e.getMessage());
        }

    }

    public static record PlayerData(String effectId, String hex, long lastUsed) {
    }
}
