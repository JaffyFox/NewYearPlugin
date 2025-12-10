package com.loma.plugin.achievements;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AchievementsManager {
    private final LoMa plugin;
    private final Map<String, Achievement> achievements = new HashMap<>();
    private final Map<UUID, Set<String>> progress = new HashMap<>();
    private File dataFile;
    private FileConfiguration dataCfg;

    public AchievementsManager(LoMa plugin) {
        this.plugin = plugin;
        loadDefinitions();
        loadData();
    }

    public void reload() {
        loadDefinitions();
        loadData();
    }

    private void loadDefinitions() {
        achievements.clear();
        File dir = new File(plugin.getDataFolder(), "achievements");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // ensure default file exists (only first_join)
        String[] defaults = new String[]{
                "achievements/first_join.yml"
        };
        for (String res : defaults) {
            try {
                if (plugin.getResource(res) != null) {
                    String name = res.substring(res.lastIndexOf('/') + 1);
                    java.io.File out = new java.io.File(dir, name);
                    if (!out.exists()) plugin.saveResource(res, false);
                }
            } catch (Exception ignored) {}
        }
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            String key = cfg.getString("key");
            if (key == null || key.isEmpty()) {
                key = f.getName().replace(".yml", "");
            }
            // load only first_join
            if (!"first_join".equalsIgnoreCase(key)) continue;
            String name = cfg.getString("name", key);
            String description = cfg.getString("description", "");
            String iconStr = cfg.getString("icon", "PAPER");
            Material icon;
            try { icon = Material.valueOf(iconStr.toUpperCase()); } catch (Exception e) { icon = Material.PAPER; }
            achievements.put(key.toLowerCase(), new Achievement(key.toLowerCase(), MessageUtils.color(name), MessageUtils.color(description), icon));
        }
    }

    private void loadData() {
        try {
            dataFile = new File(plugin.getDataFolder(), "achievements-data.yml");
            if (!dataFile.exists()) {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            }
            dataCfg = YamlConfiguration.loadConfiguration(dataFile);
            progress.clear();
            if (dataCfg.isConfigurationSection("players")) {
                for (String uuidStr : dataCfg.getConfigurationSection("players").getKeys(false)) {
                    List<String> list = dataCfg.getStringList("players." + uuidStr);
                    Set<String> set = new HashSet<>();
                    for (String k : list) set.add(k.toLowerCase());
                    try {
                        progress.put(UUID.fromString(uuidStr), set);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load achievements data: " + e.getMessage());
        }
    }

    public void saveData() {
        if (dataCfg == null) return;
        try {
            dataCfg.set("players", null);
            for (Map.Entry<UUID, Set<String>> e : progress.entrySet()) {
                dataCfg.set("players." + e.getKey().toString(), new ArrayList<>(e.getValue()));
            }
            dataCfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save achievements data: " + e.getMessage());
        }
    }

    public int getCount(UUID uuid) {
        return progress.getOrDefault(uuid, Collections.emptySet()).size();
    }

    public Collection<Achievement> listAll() { return achievements.values(); }

    public boolean has(UUID uuid, String key) {
        return progress.getOrDefault(uuid, Collections.emptySet()).contains(key.toLowerCase());
    }

    public boolean award(Player player, String key) {
        key = key.toLowerCase();
        Achievement a = achievements.get(key);
        if (a == null) return false;
        Set<String> set = progress.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        if (set.contains(key)) return false;
        set.add(key);
        saveData();

        // Feedback (localized)
        String title = getMsg("achievements.awarded.title", "&6&lДостижение!");
        String chat = getMsg("achievements.awarded.chat", "&6Вы получили достижение: &e{name}").replace("{name}", a.getName());
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        MessageUtils.sendTitle(player, title, a.getName(), 10, 40, 10);
        MessageUtils.send(player, chat);
        return true;
    }

    public void openMenu(Player player) {
        AchievementsMenu.open(plugin, this, player);
    }

    public ItemStack buildIcon(Player viewer, Achievement a) {
        boolean done = has(viewer.getUniqueId(), a.getKey());
        String name = (done ? "&a" : "&7") + a.getName();
        java.util.List<String> lore = new java.util.ArrayList<>();
        String sDone = getMsg("achievements.status.done", "&aПолучено");
        String sNot = getMsg("achievements.status.not-done", "&7Не получено");
        lore.add(done ? MessageUtils.color(sDone) : MessageUtils.color(sNot));
        if (a.getDescription() != null && !a.getDescription().isEmpty()) lore.add(MessageUtils.color("&8" + a.getDescription()));
        return new com.loma.plugin.utils.ItemBuilder(a.getIcon())
                .setName(MessageUtils.color(name))
                .setLore(lore)
                .build();
    }

    private String getMsg(String path, String def) {
        try {
            org.bukkit.configuration.file.FileConfiguration cfg = plugin.getMessagesConfig();
            if (cfg != null && cfg.contains(path)) return MessageUtils.color(cfg.getString(path));
        } catch (Exception ignored) {}
        return MessageUtils.color(def);
    }
}
