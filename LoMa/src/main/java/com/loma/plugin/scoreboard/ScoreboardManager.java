package com.loma.plugin.scoreboard;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScoreboardManager {

    private final LoMa plugin;
    private final Map<UUID, FastBoard> scoreboards;
    private BukkitTask updateTask;
    private long lastNetworkRequestAt = 0L;
    private long lastStatsRequestAt = 0L;

    public ScoreboardManager(LoMa plugin) {
        this.plugin = plugin;
        this.scoreboards = new HashMap<>();
    }

    public void createScoreboard(Player player) {
        if (!plugin.getConfig().getBoolean("features.scoreboard.enabled")) {
            return;
        }

        FastBoard board = new FastBoard(player);

        // Установка заголовка
        String title = plugin.getConfig().getString("features.scoreboard.title", "&b&lLOBBY");
        board.updateTitle(MessageUtils.color(title));

        // Установка строк
        updateScoreboard(board);

        scoreboards.put(player.getUniqueId(), board);

        // Запросим сетевой онлайн сразу, чтобы быстрее отобразить
        try { plugin.requestNetworkOnline(player); lastNetworkRequestAt = System.currentTimeMillis(); } catch (Exception ignored) {}
    }

    public void removeScoreboard(Player player) {
        FastBoard board = scoreboards.remove(player.getUniqueId());
        if (board != null) {
            board.delete();
        }
    }

    public void removeAll() {
        scoreboards.values().forEach(FastBoard::delete);
        scoreboards.clear();

        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    /**
     * Получить скорборд игрока
     * @param player Игрок
     * @return FastBoard игрока или null, если не найден
     */
    public FastBoard getScoreboard(Player player) {
        return scoreboards.get(player.getUniqueId());
    }

    /**
     * Обновить скорборд по игроку (перегрузка без использования вложенного типа снаружи)
     */
    public void updateScoreboard(Player player) {
        FastBoard board = getScoreboard(player);
        if (board != null) {
            updateScoreboard(board);
        }
    }

    public void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        int interval = plugin.getConfig().getInt("features.scoreboard.update-interval", 20);
        int statsRefreshMs = plugin.getConfig().getInt("features.scoreboard.stats-refresh-seconds", 10) * 1000;

        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, FastBoard> entry : scoreboards.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    updateScoreboard(entry.getValue());
                }
            }
            // Периодически обновляем сетевой онлайн через Plugin Messaging (раз в 5 секунд)
            long now = System.currentTimeMillis();
            if (now - lastNetworkRequestAt > 5000) {
                Player any = null;
                for (Player p : Bukkit.getOnlinePlayers()) { any = p; break; }
                if (any != null) {
                    try { plugin.requestNetworkOnline(any); lastNetworkRequestAt = now; } catch (Exception ignored) {}
                }
            }

            // Регулярно запрашиваем свежие данные плейтайма у Velocity
            if (now - lastStatsRequestAt > statsRefreshMs) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    try { plugin.requestPlayerStats(p, p.getUniqueId()); } catch (Exception ignored) {}
                }
                lastStatsRequestAt = now;
            }
        }, interval, interval);
    }

    public void updateScoreboard(FastBoard board) {
        Player player = board.getPlayer();
        List<String> lines = plugin.getConfig().getStringList("features.scoreboard.lines");

        String[] processedLines = new String[lines.size()];

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            // Замена плейсхолдеров
            line = line.replace("{player}", player.getName())
                    .replace("{displayname}", player.getDisplayName())
                    .replace("{world}", player.getWorld().getName())
                    .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                    .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()))
                    .replace("{ping}", String.valueOf(player.getPing()))
                    .replace("{x}", String.valueOf(player.getLocation().getBlockX()))
                    .replace("{y}", String.valueOf(player.getLocation().getBlockY()))
                    .replace("{z}", String.valueOf(player.getLocation().getBlockZ()));

            // Часы наигранного времени по сети (предпочтительно из кэша Velocity)
            if (line.contains("{hours_played}")) {
                com.loma.plugin.LoMa.PlayerStatsCache cache = plugin.getStatsCache(player.getUniqueId());
                if (cache != null) {
                    line = line.replace("{hours_played}", cache.getFormattedPlaytime());
                } else if (plugin.getPlaytimeService() != null) {
                    long minutes = plugin.getPlaytimeService().getTotalMinutes(player.getUniqueId());
                    line = line.replace("{hours_played}", com.loma.plugin.playtime.PlaytimeService.formatHours(minutes));
                    // Параллельно запросим актуальные данные у Velocity
                    try { plugin.requestPlayerStats(player, player.getUniqueId()); } catch (Exception ignored) {}
                } else {
                    line = line.replace("{hours_played}", "0ч 0м");
                    // Запросим stats, чтобы заполнить кэш в следующий тик
                    try { plugin.requestPlayerStats(player, player.getUniqueId()); } catch (Exception ignored) {}
                }
            }

            // Онлайн всей сети (Velocity)
            if (line.contains("{network_online}")) {
                int total = plugin.getNetworkOnline();
                line = line.replace("{network_online}", total < 0 ? "?" : String.valueOf(total));
            }

            // PlaceholderAPI поддержка
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                line = PlaceholderAPI.setPlaceholders(player, line);
            }

            // Цветовые коды
            line = MessageUtils.color(line);

            processedLines[i] = line;
        }

        board.updateLines(processedLines);
    }

    public void reload() {
        removeAll();

        if (plugin.getConfig().getBoolean("features.scoreboard.enabled")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                createScoreboard(player);
            }
            startUpdateTask();
        }
    }

    /**
     * Fast scoreboard implementation
     */
    public static class FastBoard {

        private final Player player;
        private final Scoreboard scoreboard;
        private final Objective objective;
        private final String[] lines = new String[15];
        private final Team[] teams = new Team[15];

        public FastBoard(Player player) {
            this.player = player;
            this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            this.objective = scoreboard.registerNewObjective("loma", Criteria.DUMMY, "LoMa");
            this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);

            for (int i = 0; i < 15; i++) {
                Team team = scoreboard.registerNewTeam("line" + i);
                team.addEntry(getEntry(i));
                teams[i] = team;
            }

            player.setScoreboard(scoreboard);
        }

        public void updateTitle(String title) {
            objective.setDisplayName(title);
        }

        public void updateLines(String... lines) {
            for (int i = 0; i < 15; i++) {
                if (i < lines.length && lines[i] != null) {
                    updateLine(i, lines[i]);
                } else {
                    removeLine(i);
                }
            }
        }

        private void updateLine(int line, String text) {
            Team team = teams[line];
            String entry = getEntry(line);

            if (text.length() > 64) {
                text = text.substring(0, 64);
            }

            String prefix;
            String suffix = "";

            if (text.length() <= 16) {
                prefix = text;
            } else {
                int cut = 16;
                // Не разрезаем цветовой код: если последний символ префикса — '§', отступаем на 1 назад
                if (text.charAt(cut - 1) == ChatColor.COLOR_CHAR) {
                    cut -= 1;
                }

                prefix = text.substring(0, cut);
                String lastColor = ChatColor.getLastColors(prefix);
                String rest = text.substring(cut);

                suffix = lastColor + rest;

                if (suffix.length() > 16) {
                    int max = 16;
                    // Избегаем обрыва на '§'
                    if (suffix.charAt(max - 1) == ChatColor.COLOR_CHAR) {
                        max -= 1;
                    }
                    suffix = suffix.substring(0, max);
                }
            }

            team.setPrefix(prefix);
            team.setSuffix(suffix);

            if (objective.getScore(entry).getScore() != 15 - line) {
                objective.getScore(entry).setScore(15 - line);
            }
        }

        private void removeLine(int line) {
            String entry = getEntry(line);
            if (scoreboard.getEntries().contains(entry)) {
                scoreboard.resetScores(entry);
            }
        }

        private String getEntry(int line) {
            return ChatColor.values()[line].toString() + ChatColor.RESET;
        }

        public Player getPlayer() {
            return player;
        }

        public void delete() {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }
}