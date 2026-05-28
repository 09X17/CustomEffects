package com.customeffects.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;

import com.customeffects.CustomEffects;

public class DatabaseManager {

    private Connection connection;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private static final Logger logger = Logger.getLogger("CustomEffects");

    public void connect(File folder) {
        try {
            if (!folder.exists()) folder.mkdirs();

            File db = new File(folder, "data.db");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());

            try (Statement statement = this.connection.createStatement()) {
                statement.execute("PRAGMA journal_mode = WAL;");
                statement.execute("CREATE TABLE IF NOT EXISTS player_effects (" +
                        "uuid TEXT PRIMARY KEY, " +
                        "effect_id TEXT NOT NULL, " +
                        "hex TEXT NOT NULL, " +
                        "last_used BIGINT DEFAULT 0);");
            }
            logger.info("SQLite conectado exitosamente.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error al conectar SQLite", e);
        }
    }

    public void loadPlayerData(UUID uuid) {
        String sql = "SELECT effect_id, hex, last_used FROM player_effects WHERE uuid=?";
        try (PreparedStatement stmt = this.connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    cache.put(uuid, new PlayerData(
                        rs.getString("effect_id"), 
                        rs.getString("hex"), 
                        rs.getLong("last_used")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error al cargar datos de " + uuid, e);
        }
    }

    public void setEffect(UUID uuid, String effectId, CustomEffects plugin) {
        if (effectId == null || effectId.isEmpty()) {
            removeEffect(uuid);
            return;
        }

        String hexValue = "#FFFFFF";
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("effects");
        if (section != null) {
            for (String category : section.getKeys(false)) {
                String path = "effects." + category + "." + effectId + ".hex";
                if (plugin.getConfig().contains(path)) {
                    hexValue = plugin.getConfig().getString(path, "#FFFFFF");
                    break;
                }
            }
        }
        saveEffect(uuid, effectId, hexValue);
    }

    public void saveEffect(UUID uuid, String effect, String hex) {
        long now = System.currentTimeMillis();
        cache.put(uuid, new PlayerData(effect, hex, now));

        String sql = "INSERT OR REPLACE INTO player_effects (uuid, effect_id, hex, last_used) VALUES(?,?,?,?)";
        try (PreparedStatement stmt = this.connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, effect);
            stmt.setString(3, hex);
            stmt.setLong(4, now);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error al guardar efecto para " + uuid, e);
        }
    }

    public void removeEffect(UUID uuid) {
        cache.remove(uuid);
        try (PreparedStatement stmt = this.connection.prepareStatement("DELETE FROM player_effects WHERE uuid=?")) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error al eliminar efecto para " + uuid, e);
        }
    }

    public String getActiveEffect(UUID uuid) {
        PlayerData data = cache.get(uuid);
        return (data != null) ? data.effectId() : null;
    }

    public String getHex(UUID uuid) {
        PlayerData data = cache.get(uuid);
        return (data != null) ? data.hex() : "#FFFFFF";
    }

    public void unloadPlayerData(UUID uuid) {
        cache.remove(uuid);
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Conexión SQLite cerrada correctamente.");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error al cerrar base de datos", e);
        }
    }

    public record PlayerData(String effectId, String hex, long lastUsed) {}
}