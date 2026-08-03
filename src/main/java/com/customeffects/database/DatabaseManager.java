package com.customeffects.database;

import java.io.File;
import java.sql.Connection;
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
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DatabaseManager {

    private HikariDataSource dataSource;
    private boolean useMySQL = false;
    private String tablePrefix = "ce_";
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private static final Logger logger = Logger.getLogger("CustomEffects");

    public void connect(File folder, CustomEffects plugin) {
        ConfigurationSection dbConfig = plugin.getConfig().getConfigurationSection("database");
        String type = (dbConfig != null) ? dbConfig.getString("type", "sqlite") : "sqlite";

        HikariConfig config = new HikariConfig();
        config.setPoolName("CustomEffectsPool");
        config.setMaximumPoolSize(5);

        if ("mysql".equalsIgnoreCase(type)) {
            useMySQL = true;
            ConfigurationSection mysql = dbConfig.getConfigurationSection("mysql");
            String host = mysql != null ? mysql.getString("host", "localhost") : "localhost";
            int port = mysql != null ? mysql.getInt("port", 3306) : 3306;
            String database = mysql != null ? mysql.getString("database", "customeffects") : "customeffects";
            String username = mysql != null ? mysql.getString("username", "root") : "root";
            String password = mysql != null ? mysql.getString("password", "") : "";
            this.tablePrefix = mysql != null ? mysql.getString("table-prefix", "ce_") : "ce_";

            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?autoReconnect=true&useSSL=false&characterEncoding=utf8");
            config.setUsername(username);
            config.setPassword(password);
            config.addDataSourceProperty("cachePrepStmts", "true");
        } else {
            useMySQL = false;
            if (!folder.exists()) folder.mkdirs();
            config.setJdbcUrl("jdbc:sqlite:" + new File(folder, "data.db").getAbsolutePath());
            config.setConnectionTestQuery("SELECT 1");
        }

        this.dataSource = new HikariDataSource(config);
        setupTables();
    }

    private void setupTables() {
        String sql = useMySQL 
            ? "CREATE TABLE IF NOT EXISTS " + tablePrefix + "player_effects (" +
              "uuid VARCHAR(36) PRIMARY KEY, effect_id TEXT NOT NULL, hex VARCHAR(10) NOT NULL, " +
              "style VARCHAR(50) DEFAULT '', prefix VARCHAR(100) DEFAULT '', last_used BIGINT DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
            : "CREATE TABLE IF NOT EXISTS player_effects (" +
              "uuid TEXT PRIMARY KEY, effect_id TEXT NOT NULL, hex TEXT NOT NULL, " +
              "style TEXT DEFAULT '', prefix TEXT DEFAULT '', last_used BIGINT DEFAULT 0);";
        
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            try {
                stmt.execute("ALTER TABLE " + getTableName() + " ADD COLUMN prefix " + 
                    (useMySQL ? "VARCHAR(100) DEFAULT ''" : "TEXT DEFAULT ''"));
            } catch (SQLException ignored) {}
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error configurando tablas", e);
        }
    }

    private String getTableName() {
        return useMySQL ? tablePrefix + "player_effects" : "player_effects";
    }

    public void loadPlayerData(UUID uuid) {
        try (Connection conn = dataSource.getConnection()) {
            boolean hasPrefix = false;
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, getTableName(), "prefix")) {
                hasPrefix = rs.next();
            }

            String sql = hasPrefix
                ? "SELECT effect_id, hex, style, prefix, last_used FROM " + getTableName() + " WHERE uuid=?"
                : "SELECT effect_id, hex, style, last_used FROM " + getTableName() + " WHERE uuid=?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String effectId = rs.getString("effect_id");
                        String hex = rs.getString("hex");
                        String style = rs.getString("style");
                        String prefix = hasPrefix ? rs.getString("prefix") : "";
                        long lastUsed = rs.getLong("last_used");
                        cache.put(uuid, new PlayerData(
                            effectId != null ? effectId : "",
                            hex != null ? hex : "#FFFFFF",
                            style != null ? style : "",
                            prefix != null ? prefix : "",
                            lastUsed));
                    } else {
                        String insertSql = "INSERT INTO " + getTableName() + " (uuid, effect_id, hex, style, prefix, last_used) VALUES(?,?,?,?,?,?)";
                        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                            insertStmt.setString(1, uuid.toString());
                            insertStmt.setString(2, "");
                            insertStmt.setString(3, "#FFFFFF");
                            insertStmt.setString(4, "");
                            insertStmt.setString(5, "");
                            insertStmt.setLong(6, System.currentTimeMillis());
                            insertStmt.executeUpdate();
                            cache.put(uuid, new PlayerData("", "#FFFFFF", "", "", System.currentTimeMillis()));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error al cargar datos de " + uuid, e);
        }
    }

    public void saveEffect(UUID uuid, String effect, String hex, String style) {
        String prefix = getPrefix(uuid);
        cache.put(uuid, new PlayerData(effect, hex, style != null ? style : "", prefix != null ? prefix : "", System.currentTimeMillis()));
        
        String sql = useMySQL 
            ? "INSERT INTO " + getTableName() + " (uuid, effect_id, hex, style, prefix, last_used) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE effect_id=VALUES(effect_id), hex=VALUES(hex), style=VALUES(style), prefix=VALUES(prefix), last_used=VALUES(last_used)"
            : "INSERT OR REPLACE INTO " + getTableName() + " (uuid, effect_id, hex, style, prefix, last_used) VALUES(?,?,?,?,?,?)";

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, effect);
            stmt.setString(3, hex);
            stmt.setString(4, style != null ? style : "");
            stmt.setString(5, prefix != null ? prefix : "");
            stmt.setLong(6, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error al guardar efecto para " + uuid, e);
        }
    }

    public void saveEffect(UUID uuid, String effect, String hex) {
        saveEffect(uuid, effect, hex, getStyle(uuid));
    }

    public void removeEffect(UUID uuid) {
        cache.remove(uuid);
        try (Connection conn = dataSource.getConnection(); 
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM " + getTableName() + " WHERE uuid=?")) {
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

    public String getStyle(UUID uuid) {
        PlayerData data = cache.get(uuid);
        return (data != null && data.style() != null) ? data.style() : "";
    }

    public void saveStyle(UUID uuid, String style) {
        PlayerData data = cache.get(uuid);
        saveEffect(uuid, (data != null) ? data.effectId() : "", (data != null) ? data.hex() : "#FFFFFF", style);
    }

    public String getPrefix(UUID uuid) {
        PlayerData data = cache.get(uuid);
        return (data != null && data.prefix() != null) ? data.prefix() : "";
    }

    public void savePrefix(UUID uuid, String prefix) {
        PlayerData data = cache.get(uuid);
        String effectId = (data != null) ? data.effectId() : "";
        String hex = (data != null) ? data.hex() : "#FFFFFF";
        String style = (data != null) ? data.style() : "";
        cache.put(uuid, new PlayerData(effectId, hex, style, prefix != null ? prefix : "", System.currentTimeMillis()));
        
        String sql = useMySQL 
            ? "INSERT INTO " + getTableName() + " (uuid, effect_id, hex, style, prefix, last_used) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE prefix=VALUES(prefix), last_used=VALUES(last_used)"
            : "INSERT OR REPLACE INTO " + getTableName() + " (uuid, effect_id, hex, style, prefix, last_used) VALUES(?,?,?,?,?,?)";

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, effectId);
            stmt.setString(3, hex);
            stmt.setString(4, style);
            stmt.setString(5, prefix != null ? prefix : "");
            stmt.setLong(6, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error al guardar prefijo para " + uuid, e);
        }
    }

    public void unloadPlayerData(UUID uuid) {
        cache.remove(uuid);
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Conexión a la base de datos cerrada correctamente.");
        }
    }

    public boolean isMySQL() {
        return useMySQL;
    }

    public record PlayerData(String effectId, String hex, String style, String prefix, long lastUsed) {}
}