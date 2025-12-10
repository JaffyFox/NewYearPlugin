package com.loma.plugin.managers;

import com.loma.plugin.LoMa;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PreferencesManager {
    private final LoMa plugin;
    private final Map<UUID, Map<String, Boolean>> prefs = new HashMap<>();
    private File file;
    private FileConfiguration cfg;

    public PreferencesManager(LoMa plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        file = new File(dataDir, "preferences.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        cfg = YamlConfiguration.loadConfiguration(file);
        // Load all
        if (cfg.getConfigurationSection("players") != null) {
            for (String key : cfg.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    Map<String, Boolean> m = new HashMap<>();
                    for (String k : cfg.getConfigurationSection("players." + key).getKeys(false)) {
                        m.put(k, cfg.getBoolean("players." + key + "." + k));
                    }
                    prefs.put(uuid, m);
                } catch (Exception ignored) {}
            }
        }
    }

    public boolean isEnabled(UUID uuid, String feature, boolean def) {
        return prefs.getOrDefault(uuid, new HashMap<>()).getOrDefault(feature, def);
    }

    public boolean isEnabled(UUID uuid, String feature) {
        // By default features are enabled unless toggled off
        return isEnabled(uuid, feature, true);
    }

    public void set(UUID uuid, String feature, boolean value) {
        prefs.computeIfAbsent(uuid, u -> new HashMap<>()).put(feature, value);
        save(uuid);
    }

    public void setAll(UUID uuid, boolean value) {
        Map<String, Boolean> m = prefs.computeIfAbsent(uuid, u -> new HashMap<>());
        m.put("doublejump", value);
        m.put("particles", value);
        m.put("ride", value);
        m.put("visibility", value);
        m.put("fly", value);
        save(uuid);
    }

    private void save(UUID uuid) {
        String base = "players." + uuid.toString();
        cfg.set(base, null);
        Map<String, Boolean> m = prefs.get(uuid);
        if (m != null) {
            for (Map.Entry<String, Boolean> e : m.entrySet()) {
                cfg.set(base + "." + e.getKey(), e.getValue());
            }
        }
        try { cfg.save(file); } catch (IOException ignored) {}
    }
}
