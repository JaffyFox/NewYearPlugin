package com.loma.plugin.listeners;

import com.loma.plugin.LoMa;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final LoMa plugin;

    public PlayerQuitListener(LoMa plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Обработка выхода игрока (уже реализовано в PlayerJoinListener)
    }
}