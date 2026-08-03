package com.customeffects.database;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
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
    private final Map<UUID, List<UUID>> friendCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<UUID>> requestCache = new ConcurrentHashMap<>();
    private static final Logger logger = Logger.getLogger("CustomEffects");

    public void connect(File folder, CustomEffects plugin) {
        ConfigurationSection dbConfig = plugin.getConfig().getConfigurationSection("database");
        if (dbConfig == null) {
            throw new IllegalStateException("Missing 'database' section in config.yml");
        }

        String type = dbConfig.getString("type", "sqlite");

        HikariConfig config = new HikariConfig();
        config.setPoolName("CustomEffectsPool");
        config.setMaximumPoolSize(5);

        if ("mysql".equalsIgnoreCase(type)) {
            useMySQL = true;

            ConfigurationSection mysql = dbConfig.getConfigurationSection("mysql");
            if (mysql == null) {
                throw new IllegalStateException("Missing 'mysql' section in database.yml");
            }

            String host = mysql.getString("host", "localhost");
            int port = mysql.getInt("port", 3306);
            String database = mysql.getString("database", "customeffects");
            String username = mysql.getString("username", "root");
            String password = mysql.getString("password", "");
            this.tablePrefix = mysql.getString("table-prefix", "ce_");

            config.setJdbcUrl(
                    "jdbc:mysql://" + host + ":" + port + "/" + database
                            + "?autoReconnect=true&useSSL=false&characterEncoding=utf8");
            config.setUsername(username);
            config.setPassword(password);
            config.addDataSourceProperty("cachePrepStmts", "true");

        } else {
            useMySQL = false;

            if (!folder.exists()) {
                folder.mkdirs();
            }

            config.setJdbcUrl("jdbc:sqlite:" + new File(folder, "data.db").getAbsolutePath());
            config.setConnectionTestQuery("SELECT 1");
        }
        this.dataSource = new HikariDataSource(config);
        setupTables();
    }

    private void setupTables() {
        String playerEffectsSql = useMySQL
                ? "CREATE TABLE IF NOT EXISTS " + tablePrefix + "player_effects (" +
                        "uuid VARCHAR(36) PRIMARY KEY, effect_id VARCHAR(100) NOT NULL, hex VARCHAR(10) NOT NULL, " +
                        "style VARCHAR(50) DEFAULT '', prefix VARCHAR(100) DEFAULT '', " +
                        "last_used BIGINT DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                : "CREATE TABLE IF NOT EXISTS player_effects (" +
                        "uuid TEXT PRIMARY KEY, effect_id TEXT NOT NULL, hex TEXT NOT NULL, " +
                        "style TEXT DEFAULT '', prefix TEXT DEFAULT '', " +
                        "last_used BIGINT DEFAULT 0);";

        String friendshipsSql = useMySQL
                ? "CREATE TABLE IF NOT EXISTS " + tablePrefix + "friendships (" +
                        "player_uuid VARCHAR(36) NOT NULL, friend_uuid VARCHAR(36) NOT NULL, " +
                        "created_at BIGINT DEFAULT 0, UNIQUE(player_uuid, friend_uuid), " +
                        "PRIMARY KEY(player_uuid, friend_uuid)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                : "CREATE TABLE IF NOT EXISTS friendships (" +
                        "player_uuid TEXT NOT NULL, friend_uuid TEXT NOT NULL, " +
                        "created_at BIGINT DEFAULT 0, UNIQUE(player_uuid, friend_uuid));";

        String friendRequestsSql = useMySQL
                ? "CREATE TABLE IF NOT EXISTS " + tablePrefix + "friend_requests (" +
                        "sender_uuid VARCHAR(36) NOT NULL, receiver_uuid VARCHAR(36) NOT NULL, " +
                        "created_at BIGINT DEFAULT 0, UNIQUE(sender_uuid, receiver_uuid), " +
                        "PRIMARY KEY(sender_uuid, receiver_uuid)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"
                : "CREATE TABLE IF NOT EXISTS friend_requests (" +
                        "sender_uuid TEXT NOT NULL, receiver_uuid TEXT NOT NULL, " +
                        "created_at BIGINT DEFAULT 0, UNIQUE(sender_uuid, receiver_uuid));";

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(playerEffectsSql);
            stmt.execute(friendshipsSql);
            stmt.execute(friendRequestsSql);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error configurando tablas", e);
        }

        migrateColumn("prefix", "VARCHAR(100) DEFAULT ''", "TEXT DEFAULT ''");
    }

    private void migrateColumn(String column, String mysqlType, String sqliteType) {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            if (!columnExists(stmt, column)) {
                stmt.execute("ALTER TABLE " + getTableName() + " ADD COLUMN " + column + " " +
                        (useMySQL ? mysqlType : sqliteType));
            }
        } catch (SQLException ignored) {
        }
    }

    private boolean columnExists(Statement stmt, String column) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("SELECT " + column + " FROM " + getTableName() + " LIMIT 1")) {
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private String getTableName() {
        return useMySQL ? tablePrefix + "player_effects" : "player_effects";
    }

    private String getFriendsTableName() {
        return useMySQL ? tablePrefix + "friendships" : "friendships";
    }

    private String getRequestsTableName() {
        return useMySQL ? tablePrefix + "friend_requests" : "friend_requests";
    }

    // ==================== PLAYER DATA ====================

    public void loadPlayerData(UUID uuid) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT effect_id, hex, style, prefix, last_used FROM " + getTableName() + " WHERE uuid=?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        cache.put(uuid, readPlayerData(rs));
                    } else {
                        String insertSql = "INSERT INTO " + getTableName()
                                + " (uuid, effect_id, hex, style, prefix, last_used) VALUES(?,?,?,?,?,?)";
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

    private PlayerData readPlayerData(ResultSet rs) throws SQLException {
        return new PlayerData(
                safeString(rs, "effect_id"),
                safeString(rs, "hex", "#FFFFFF"),
                safeString(rs, "style"),
                safeString(rs, "prefix"),
                rs.getLong("last_used"));
    }

    private String safeString(ResultSet rs, String column) throws SQLException {
        String val = rs.getString(column);
        return val != null ? val : "";
    }

    private String safeString(ResultSet rs, String column, String defaultVal) throws SQLException {
        String val = rs.getString(column);
        return val != null ? val : defaultVal;
    }

    public void saveEffect(UUID uuid, String effect, String hex, String style) {
        PlayerData data = cache.get(uuid);
        String prefix = (data != null) ? data.prefix() : "";
        cache.put(uuid, new PlayerData(effect, hex, style != null ? style : "", prefix, System.currentTimeMillis()));
        savePlayerData(uuid);
    }

    public void saveEffect(UUID uuid, String effect, String hex) {
        saveEffect(uuid, effect, hex, getStyle(uuid));
    }

    public void savePrefix(UUID uuid, String prefix) {
        PlayerData data = cache.get(uuid);
        if (data != null) {
            cache.put(uuid, new PlayerData(data.effectId(), data.hex(), data.style(),
                    prefix != null ? prefix : "", System.currentTimeMillis()));
        }
        savePlayerData(uuid);
    }

    public void saveStyle(UUID uuid, String style) {
        PlayerData data = cache.get(uuid);
        if (data != null) {
            cache.put(uuid, new PlayerData(data.effectId(), data.hex(), style != null ? style : "",
                    data.prefix(), System.currentTimeMillis()));
        }
        savePlayerData(uuid);
    }

    private void savePlayerData(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) return;

        String sql = useMySQL
                ? "INSERT INTO " + getTableName()
                        + " (uuid, effect_id, hex, style, prefix, last_used) VALUES(?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE effect_id=VALUES(effect_id), hex=VALUES(hex), style=VALUES(style), prefix=VALUES(prefix), last_used=VALUES(last_used)"
                : "INSERT OR REPLACE INTO " + getTableName()
                        + " (uuid, effect_id, hex, style, prefix, last_used) VALUES(?,?,?,?,?,?)";

        try (Connection conn = dataSource.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, data.effectId());
            stmt.setString(3, data.hex());
            stmt.setString(4, data.style());
            stmt.setString(5, data.prefix());
            stmt.setLong(6, data.lastUsed());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error al guardar datos para " + uuid, e);
        }
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

    public String getPrefix(UUID uuid) {
        PlayerData data = cache.get(uuid);
        return (data != null && data.prefix() != null) ? data.prefix() : "";
    }

    public void unloadPlayerData(UUID uuid) {
        cache.remove(uuid);
        friendCache.remove(uuid);
        requestCache.remove(uuid);
    }

    // ==================== FRIENDSHIPS ====================

    public List<UUID> getFriends(UUID uuid) {
        List<UUID> friends = friendCache.get(uuid);
        if (friends != null) return new ArrayList<>(friends);

        friends = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                        "SELECT friend_uuid FROM " + getFriendsTableName() + " WHERE player_uuid=?")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    friends.add(UUID.fromString(rs.getString("friend_uuid")));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error al cargar amigos de " + uuid, e);
        }
        friendCache.put(uuid, friends);
        return new ArrayList<>(friends);
    }

    public void addFriend(UUID player, UUID friend) {
        long now = System.currentTimeMillis();
        String sql = useMySQL
                ? "INSERT INTO " + getFriendsTableName() + " (player_uuid, friend_uuid, created_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE created_at=VALUES(created_at)"
                : "INSERT OR REPLACE INTO " + getFriendsTableName() + " (player_uuid, friend_uuid, created_at) VALUES(?,?,?)";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, player.toString());
            stmt.setString(2, friend.toString());
            stmt.setLong(3, now);
            stmt.executeUpdate();

            stmt.setString(1, friend.toString());
            stmt.setString(2, player.toString());
            stmt.setLong(3, now);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error al agregar amigo", e);
        }

        List<UUID> playerFriends = friendCache.computeIfAbsent(player, k -> new ArrayList<>());
        if (!playerFriends.contains(friend)) playerFriends.add(friend);
        List<UUID> friendFriends = friendCache.computeIfAbsent(friend, k -> new ArrayList<>());
        if (!friendFriends.contains(player)) friendFriends.add(player);
    }

    public void removeFriend(UUID player, UUID friend) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM " + getFriendsTableName() + " WHERE (player_uuid=? AND friend_uuid=?) OR (player_uuid=? AND friend_uuid=?)")) {
            stmt.setString(1, player.toString());
            stmt.setString(2, friend.toString());
            stmt.setString(3, friend.toString());
            stmt.setString(4, player.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error al eliminar amigo", e);
        }

        List<UUID> playerFriends = friendCache.get(player);
        if (playerFriends != null) playerFriends.remove(friend);
        List<UUID> friendFriends = friendCache.get(friend);
        if (friendFriends != null) friendFriends.remove(player);
    }

    public boolean areFriends(UUID player, UUID friend) {
        return getFriends(player).contains(friend);
    }

    // ==================== FRIEND REQUESTS ====================

    public List<UUID> getPendingRequests(UUID uuid) {
        List<UUID> requests = requestCache.get(uuid);
        if (requests != null) return new ArrayList<>(requests);

        requests = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                        "SELECT sender_uuid FROM " + getRequestsTableName() + " WHERE receiver_uuid=?")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    requests.add(UUID.fromString(rs.getString("sender_uuid")));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error al cargar solicitudes de " + uuid, e);
        }
        requestCache.put(uuid, requests);
        return new ArrayList<>(requests);
    }

    public void sendRequest(UUID sender, UUID receiver) {
        String sql = useMySQL
                ? "INSERT INTO " + getRequestsTableName() + " (sender_uuid, receiver_uuid, created_at) VALUES(?,?,?) ON DUPLICATE KEY UPDATE created_at=VALUES(created_at)"
                : "INSERT OR REPLACE INTO " + getRequestsTableName() + " (sender_uuid, receiver_uuid, created_at) VALUES(?,?,?)";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sender.toString());
            stmt.setString(2, receiver.toString());
            stmt.setLong(3, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error al enviar solicitud", e);
        }

        List<UUID> receiverRequests = requestCache.computeIfAbsent(receiver, k -> new ArrayList<>());
        if (!receiverRequests.contains(sender)) receiverRequests.add(sender);
    }

    public void removeRequest(UUID sender, UUID receiver) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM " + getRequestsTableName() + " WHERE sender_uuid=? AND receiver_uuid=?")) {
            stmt.setString(1, sender.toString());
            stmt.setString(2, receiver.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error al eliminar solicitud", e);
        }

        List<UUID> receiverRequests = requestCache.get(receiver);
        if (receiverRequests != null) receiverRequests.remove(sender);
    }

    public boolean hasPendingRequest(UUID sender, UUID receiver) {
        return getPendingRequests(receiver).contains(sender);
    }

    // ==================== UTILITIES ====================

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Conexión a la base de datos cerrada correctamente.");
        }
    }

    public boolean isMySQL() {
        return useMySQL;
    }

    public record PlayerData(String effectId, String hex, String style, String prefix, long lastUsed) {
    }
}