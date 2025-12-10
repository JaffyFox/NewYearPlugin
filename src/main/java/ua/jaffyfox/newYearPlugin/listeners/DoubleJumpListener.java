package ua.jaffyfox.newYearPlugin.listeners;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import ua.jaffyfox.newYearPlugin.config.ConfigManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DoubleJumpListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Map<Player, Boolean> doubleJumpReady = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> flyArmUntil = new HashMap<>();

    public DoubleJumpListener(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        doubleJumpReady.put(player, false);
        applyJumpBoost(player);
        // Allow double-jump toggle in survival
        if (!player.getAllowFlight() && player.getGameMode() != org.bukkit.GameMode.CREATIVE && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            player.setAllowFlight(true);
        }
        // Speed now applied by SpeedListener based on saved value
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        doubleJumpReady.remove(event.getPlayer());
        cooldowns.remove(event.getPlayer().getUniqueId());
        flyArmUntil.remove(event.getPlayer().getUniqueId());
    }

    private void applyJumpBoost(Player player) {
        int level = -1;
        org.bukkit.configuration.file.FileConfiguration data = configManager.getConfig("JumpData");
        if (data != null) {
            level = data.getInt("players." + player.getUniqueId(), -1);
        }
        if (level < 1 || level > 5) {
            level = configManager.getConfig("JumpBoostConfig").getInt("default_level", 2);
        }
        if (level < 1) level = 1; if (level > 5) level = 5;
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, level - 1, false, false), true);
    }

    public void setJumpBoostLevel(Player player, int level) {
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, level - 1, false, false), true);
    }

    private void applySpeedEffect(Player player) {
        int level = configManager.getConfig("SpeedConfig").getInt("default_level", 2);
        if (level <= 0) {
            player.removePotionEffect(PotionEffectType.SPEED);
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, level - 1, false, false), true);
    }

    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (player.isOnGround()) {
            doubleJumpReady.put(player, true);
            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                player.setAllowFlight(true);
                player.setFlying(false);
            }
        }
    }

    @org.bukkit.event.EventHandler
    public void onPlayerToggleFlight(org.bukkit.event.player.PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        // Allow native flight for creative/spectator
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }

        final var djCfg = configManager.getConfig("DoubleJumpConfig");
        boolean enabled = djCfg.getBoolean("enabled", true);
        boolean flyWindow = djCfg.getBoolean("enable_fly_window", false);
        long now = System.currentTimeMillis();

        if (!enabled) {
            return; // do not interfere
        }

        UUID id = player.getUniqueId();
        Long arm = flyArmUntil.get(id);
        if (flyWindow && arm != null && now <= arm) {
            // Allow survival flight during window
            flyArmUntil.remove(id);
            return; // do not cancel
        }

        // Only allow double jump when we've re-armed on ground
        boolean ready = doubleJumpReady.getOrDefault(player, false);
        if (!ready) {
            // prevent enabling survival flight mid-air
            event.setCancelled(true);
            player.setFlying(false);
            player.setAllowFlight(false);
            return;
        }

        // Perform double jump
        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);

        // Cooldown check
        if (isOnCooldown(id, djCfg.getInt("cooldown_seconds", 3))) {
            return;
        }

        double force = djCfg.getDouble("force", 1.5);
        double height = djCfg.getDouble("height", 0.8);
        Vector velocity = player.getLocation().getDirection().multiply(force);
        velocity.setY(height);
        player.setVelocity(velocity);
        player.setFallDistance(0); // avoid big fall damage spikes

        // Play configurable sound via resource key to avoid deprecation
        String soundKey = djCfg.getString("sound.name", "minecraft:entity.bat.takeoff");
        float vol = (float) djCfg.getDouble("sound.volume", 1.0);
        float pitch = (float) djCfg.getDouble("sound.pitch", 1.0);
        try { player.playSound(player.getLocation(), soundKey, vol, pitch); } catch (Exception ignored) {}

        // set cooldown timestamp
        cooldowns.put(id, now);

        // Arm flight window if enabled
        if (flyWindow) {
            long windowMs = djCfg.getLong("fly_window_ms", 1500L);
            flyArmUntil.put(id, now + windowMs);
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && player.getGameMode() != org.bukkit.GameMode.CREATIVE && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                    player.setAllowFlight(true);
                }
            }, 1L);
        }

        doubleJumpReady.put(player, false);
    }

    private boolean isOnCooldown(UUID id, int cooldownSeconds) {
        Long last = cooldowns.get(id);
        if (last == null) return false;
        return System.currentTimeMillis() - last < cooldownSeconds * 1000L;
    }
}