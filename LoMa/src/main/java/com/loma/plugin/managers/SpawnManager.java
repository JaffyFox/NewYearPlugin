package com.loma.plugin.managers;

import com.loma.plugin.LoMa;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class SpawnManager {

    private final LoMa plugin;
    private Location spawn;

    public SpawnManager(LoMa plugin) {
        this.plugin = plugin;
        this.spawn = null;
    }

    public Location getSpawn() {
        return spawn != null ? spawn.clone() : null;
    }

    public void setSpawn(Location location) {
        this.spawn = location.clone();
        saveSpawn();
    }

    public boolean hasSpawn() {
        return spawn != null;
    }

    public void teleportToSpawn(Player player) {
        if (spawn == null) return;

        player.teleport(spawn);

        // Анимация телепортации
        if (plugin.getConfig().getBoolean("animations.teleport.enabled")) {
            plugin.getAnimationManager().playTeleportAnimation(player);
        }
    }

    public void teleportToSpawnDelayed(Player player, int delay) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    teleportToSpawn(player);
                }
            }
        }.runTaskLater(plugin, delay * 20L);
    }

    private void saveSpawn() {
        if (spawn == null) return;

        plugin.getConfig().set("spawn.world", spawn.getWorld().getName());
        plugin.getConfig().set("spawn.x", spawn.getX());
        plugin.getConfig().set("spawn.y", spawn.getY());
        plugin.getConfig().set("spawn.z", spawn.getZ());
        plugin.getConfig().set("spawn.yaw", spawn.getYaw());
        plugin.getConfig().set("spawn.pitch", spawn.getPitch());
        plugin.saveConfig();
    }

    public boolean isInSpawnRadius(Location location) {
        if (spawn == null || location == null) return false;
        if (!spawn.getWorld().equals(location.getWorld())) return false;

        double radius = plugin.getConfig().getDouble("spawn.protection-radius", 100);
        return spawn.distance(location) <= radius;
    }
}