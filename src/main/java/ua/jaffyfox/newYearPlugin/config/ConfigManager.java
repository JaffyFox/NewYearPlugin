package ua.jaffyfox.newYearPlugin.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final JavaPlugin plugin;
    private final Map<String, FileConfiguration> configs = new HashMap<>();
    private final Map<String, File> files = new HashMap<>();

    // Map logical config names used in code to resource paths inside the jar
    private final Map<String, String> nameToPath = new HashMap<String, String>() {{
        put("JumpBoostConfig", "JumpBoostConfig.yml");
        put("DoubleJumpConfig", "DoubleJumpConfig.yml");
        put("SpeedConfig", "SpeedConfig.yml");
        put("SpeedData", "SpeedData.yml");
        put("JumpData", "JumpData.yml");
        put("SpawnConfig", "SpawnConfig.yml");
        put("PissConfig", "Fun/PissConfig.yml");
        put("VomitConfig", "Fun/VomitConfig.yml");
        put("PoopConfig", "Fun/PoopConfig.yml");
        put("SitConfig", "Fun/SitConfig.yml");
        put("ElevatorConfig", "Elevator/ElevatorConfig.yml");
        // Note: first floor file in resources is named "FirstfFlor.yml" (with an extra 'f')
        put("FirstFlor", "Elevator/FirstfFlor.yml");
        put("SecondFlor", "Elevator/SecondFlor.yml");
        put("ThirdFlor", "Elevator/ThirdFlor.yml");
        put("FourthFlor", "Elevator/FourthFlor.yml");
    }};

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        // Ensure plugin data folder exists
        if (!plugin.getDataFolder().exists()) {
            // noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }

        for (Map.Entry<String, String> entry : nameToPath.entrySet()) {
            String name = entry.getKey();
            String relativePath = entry.getValue();
            loadOne(name, relativePath);
        }
    }

    private void loadOne(String name, String relativePath) {
        File file = new File(plugin.getDataFolder(), relativePath);
        if (!file.getParentFile().exists()) {
            // noinspection ResultOfMethodCallIgnored
            file.getParentFile().mkdirs();
        }
        if (!file.exists()) {
            // Copy default from jar resources if present
            try {
                plugin.saveResource(relativePath, false);
            } catch (IllegalArgumentException ignored) {
                // Resource doesn't exist in jar; create an empty file
                try {
                    // noinspection ResultOfMethodCallIgnored
                    file.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to create config file: " + file.getPath());
                }
            }
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        configs.put(name, cfg);
        files.put(name, file);
    }

    public FileConfiguration getConfig(String name) {
        FileConfiguration cfg = configs.get(name);
        if (cfg == null) {
            // Try load lazily if missing
            String path = nameToPath.get(name);
            if (path != null) {
                loadOne(name, path);
                return configs.get(name);
            }
        }
        return cfg;
    }

    public void saveConfig(String name) {
        FileConfiguration cfg = configs.get(name);
        File file = files.get(name);
        if (cfg != null && file != null) {
            try {
                cfg.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save config '" + name + "': " + e.getMessage());
            }
        }
    }

    public void reload(String name) {
        File file = files.get(name);
        if (file != null) {
            configs.put(name, YamlConfiguration.loadConfiguration(file));
        }
    }
}
