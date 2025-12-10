package com.loma.plugin.playtime;

import com.loma.plugin.LoMa;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PlaytimeService {
    private final LoMa plugin;
    private HikariDataSource dataSource;
    private ExecutorService exec;
    private final Map<UUID, Long> cache = new ConcurrentHashMap<>(); // cache minutes by uuid
    private final long cacheTtlMs = TimeUnit.SECONDS.toMillis(30);
    private final Map<UUID, Long> cacheTime = new ConcurrentHashMap<>();

    public PlaytimeService(LoMa plugin) {
        this.plugin = plugin;
    }

    public void init() {
        HikariConfig cfg = new HikariConfig();
        String host = plugin.getConfig().getString("database.host");
        int port = plugin.getConfig().getInt("database.port");
        String db = plugin.getConfig().getString("database.database");
        String user = plugin.getConfig().getString("database.username");
        String pass = plugin.getConfig().getString("database.password");
        String jdbc = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC", host, port, db);
        cfg.setJdbcUrl(jdbc);
        cfg.setUsername(user);
        cfg.setPassword(pass);
        cfg.setMaximumPoolSize(5);
        cfg.setPoolName("LoMa-Playtime");
        this.dataSource = new HikariDataSource(cfg);
        this.exec = Executors.newFixedThreadPool(2);

        // Ensure schema exists
        exec.execute(() -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("""
                     CREATE TABLE IF NOT EXISTS network_playtime (
                         uuid VARCHAR(36) NOT NULL,
                         minutes BIGINT NOT NULL,
                         PRIMARY KEY (uuid)
                     ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                 """)) {
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to ensure playtime table: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        if (exec != null) {
            exec.shutdown();
            try { exec.awaitTermination(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            exec = null;
        }
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    public void getTotalMinutesAsync(UUID uuid, java.util.function.LongConsumer callback) {
        Long cached = getCached(uuid);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        exec.execute(() -> {
            long minutes = queryMinutes(uuid);
            cache(uuid, minutes);
            callback.accept(minutes);
        });
    }

    public long getTotalMinutes(UUID uuid) {
        Long cached = getCached(uuid);
        if (cached != null) return cached;
        long minutes = queryMinutes(uuid);
        cache(uuid, minutes);
        return minutes;
    }

    public java.util.Map<String, Long> getPerServerMinutes(UUID uuid) {
        java.util.Map<String, Long> res = new java.util.HashMap<>();
        String sql = "SELECT server, minutes FROM network_playtime_servers WHERE uuid = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    res.put(rs.getString(1), rs.getLong(2));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to query per-server playtime: " + e.getMessage());
        }
        return res;
    }

    private long queryMinutes(UUID uuid) {
        String sql = "SELECT minutes FROM network_playtime WHERE uuid = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to query playtime: " + e.getMessage());
        }
        return 0L;
    }

    private void cache(UUID uuid, long minutes) {
        cache.put(uuid, minutes);
        cacheTime.put(uuid, System.currentTimeMillis());
    }

    private Long getCached(UUID uuid) {
        Long ts = cacheTime.get(uuid);
        if (ts == null) return null;
        if (System.currentTimeMillis() - ts > cacheTtlMs) {
            cache.remove(uuid);
            cacheTime.remove(uuid);
            return null;
        }
        return cache.get(uuid);
    }

    public static String formatHours(long minutes) {
        long hours = minutes / 60;
        return String.valueOf(hours);
    }
}
