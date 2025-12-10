package com.loma.plugin.listeners;

import com.loma.plugin.LoMa;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerSpawnListener implements Listener {

    private final LoMa plugin;

    public PlayerSpawnListener(LoMa plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (plugin.getConfig().getBoolean("spawn.teleport-on-respawn")) {
            if (plugin.getSpawnManager().hasSpawn()) {
                event.setRespawnLocation(plugin.getSpawnManager().getSpawn());
            }
        }
    }
}