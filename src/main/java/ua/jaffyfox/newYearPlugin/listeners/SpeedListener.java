package ua.jaffyfox.newYearPlugin.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.configuration.file.FileConfiguration;
import ua.jaffyfox.newYearPlugin.config.ConfigManager;

public class SpeedListener implements Listener {

    private final ConfigManager configManager;

    public SpeedListener(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applySavedSpeed(event.getPlayer());
    }

    public void applySavedSpeed(Player player) {
        FileConfiguration data = configManager.getConfig("SpeedData");
        int level = -1;
        if (data != null) {
            level = data.getInt("players." + player.getUniqueId().toString(), -1);
        }
        if (level < 0) {
            level = Math.max(0, Math.min(5, configManager.getConfig("SpeedConfig").getInt("default_level", 0)));
        }
        // Apply
        player.removePotionEffect(PotionEffectType.SPEED);
        if (level > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, level - 1, false, false), true);
        }
    }
}
