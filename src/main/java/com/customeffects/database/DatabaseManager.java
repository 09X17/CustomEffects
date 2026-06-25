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
    private boolean useMySQL = false;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private String tablePrefix = "ce_";
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private static final Logger logger = Logger.getLogger("CustomEffects");

    public void connect(File folder, CustomEffects plugin) {
        ConfigurationSection dbConfig = plugin.getConfig().getConfigurationSection("database");
        String type = "sqlite";
        if (dbConfig != null) {
            type = dbConfig.getString("type", "sqlite");
        }

        if ("mysql".equalsIgnoreCase(type)) {
            connectMySQL(dbConfig);
        } else {
            connectSQLite(folder);
        }
    }

    private void connectSQLite(File folder) {
        try {
            if (!folder.exists()) folder.mkdirs();

            File db = new File(folder, "data.db");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
            this.useMySQL = false;

            try (Statement statement = this.connection.createStatement()) {
                statement.execute("PRAGMA journal_mode = WAL;");
                statement.execute("CREATE TABLE IF NOT EXISTS player_effects (" +
                        "uuid TEXT PRIMARY KEY, " +
                        "effect_id TEXT NOT NULL, " +
                        "hex TEXT NOT NULL, " +
                        "style TEXT DEFAULT '', " +
                        "last_used BIGINT DEFAULT 0);");
            }

            try (Statement statement = this.connection.createStatement()) {
                ResultSet rs = statement.executeQuery("PRAGMA table_info(player_effects);");
                boolean hasStyle = false;
                while (rs.next()) {
                    if ("style".equals(rs.getString("name"))) {
                        hasStyle = true;
                        break;
                    }
                }
                if (!hasStyle) {
                    statement.execute("ALTER TABLE player_effects ADD COLUMN style TEXT DEFAULT '';");
                }
            }
            logger.info("SQLite conectado exitosamente.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error al conectar SQLite", e);
        }
    }

    private void connectMySQL(ConfigurationSection dbConfig) {
        try {
            this.mysqlHost = "localhost";
            this.mysqlPort = 3306;
            this.mysqlDatabase = "customeffects";
            this.mysqlUsername = "root";
            this.mysqlPassword = "";

            if (dbConfig != null) {
                ConfigurationSection mysql = dbConfig.getConfigurationSection("mysql");
                if (mysql != null) {
                    this.mysqlHost = mysql.getString("host", "localhost");
                    this.mysqlPort = mysql.getInt("port", 3306);
                    this.mysqlDatabase = mysql.getString("database", "customeffects");
                    this.mysqlUsername = mysql.getString("username", "root");
                    this.mysqlPassword = mysql.getString("password", "");
                    this.tablePrefix = mysql.getString("table-prefix", "ce_");
                }
            }

            String url = "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase 
                    + "?useSSL=false&autoReconnect=true&characterEncoding=utf8";
            this.connection = DriverManager.getConnection(url, mysqlUsername, mysqlPassword);
            this.useMySQL = true;

            try (Statement statement = this.connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS " + tablePrefix + "player_effects (" +
                        "uuid VARCHAR(36) PRIMARY KEY, " +
                        "effect_id TEXT NOT NULL, " +
                        "hex VARCHAR(10) NOT NULL, " +
                        "style VARCHAR(50) DEFAULT '', " +
                        "last_used BIGINT DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            }

            logger.info("MySQL conectado exitosamente a " + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error al conectar MySQL, fallback a SQLite", e);
            connectSQLite(new File("plugins/CustomEffects"));
        }
    }

    private Connection getConnection() throws SQLException {
        if (useMySQL && (connection == null || connection.isClosed())) {
            String url = "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase 
                    + "?useSSL=false&autoReconnect=true&characterEncoding=utf8";
            connection = DriverManager.getConnection(url, mysqlUsername, mysqlPassword);
        }
        return connection;
    }

    private String getTableName() {
        return useMySQL ? tablePrefix + "player_effects" : "player_effects";
    }

    public void loadPlayerData(UUID uuid) {
        String sql = "SELECT effect_id, hex, style, last_used FROM " + getTableName() + " WHERE uuid=?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    cache.put(uuid, new PlayerData(
                        rs.getString("effect_id"), 
                        rs.getString("hex"),
                        rs.getString("style"),
                        rs.getLong("last_used")
                    ));
                } else {
                    cache.put(uuid, new PlayerData("", "#FFFFFF", "", System.currentTimeMillis()));
                    String insertSql = useMySQL
                            ? "INSERT IGNORE INTO " + getTableName() + " (uuid, effect_id, hex, style, last_used) VALUES(?,?,?,?,?)"
                            : "INSERT OR IGNORE INTO " + getTableName() + " (uuid, effect_id, hex, style, last_used) VALUES(?,?,?,?,?)";
                    try (PreparedStatement insertStmt = getConnection().prepareStatement(insertSql)) {
                        insertStmt.setString(1, uuid.toString());
                        insertStmt.setString(2, "");
                        insertStmt.setString(3, "#FFFFFF");
                        insertStmt.setString(4, "");
                        insertStmt.setLong(5, System.currentTimeMillis());
                        insertStmt.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error al cargar datos de " + uuid, e);
        }
    }

    public void saveEffect(UUID uuid, String effect, String hex, String style) {
        long now = System.currentTimeMillis();
        cache.put(uuid, new PlayerData(effect, hex, style != null ? style : "", now));

        String sql = useMySQL
                ? "INSERT INTO " + getTableName() + " (uuid, effect_id, hex, style, last_used) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE effect_id=VALUES(effect_id), hex=VALUES(hex), style=VALUES(style), last_used=VALUES(last_used)"
                : "INSERT OR REPLACE INTO " + getTableName() + " (uuid, effect_id, hex, style, last_used) VALUES(?,?,?,?,?)";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, effect);
            stmt.setString(3, hex);
            stmt.setString(4, style != null ? style : "");
            stmt.setLong(5, now);
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
        try (PreparedStatement stmt = getConnection().prepareStatement("DELETE FROM " + getTableName() + " WHERE uuid=?")) {
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
        String effectId = (data != null) ? data.effectId() : "";
        String hex = (data != null) ? data.hex() : "#FFFFFF";
        saveEffect(uuid, effectId, hex, style);
    }

    public void unloadPlayerData(UUID uuid) {
        cache.remove(uuid);
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                if (useMySQL) {
                    logger.info("Conexión MySQL cerrada correctamente.");
                } else {
                    logger.info("Conexión SQLite cerrada correctamente.");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error al cerrar base de datos", e);
        }
    }

    public boolean isMySQL() {
        return useMySQL;
    }

    public record PlayerData(String effectId, String hex, String style, long lastUsed) {}
}
