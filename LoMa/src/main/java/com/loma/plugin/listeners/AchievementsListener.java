package com.loma.plugin.listeners;

import com.loma.plugin.LoMa;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.entity.Player;

public class AchievementsListener implements Listener {
    private final LoMa plugin;

    public AchievementsListener(LoMa plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFirstChat(AsyncPlayerChatEvent event) {
        if (plugin.getAchievementsManager() == null) return;
        plugin.getAchievementsManager().award(event.getPlayer(), "first_chat");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFirstKill(EntityDeathEvent event) {
        if (plugin.getAchievementsManager() == null) return;
        if (event.getEntity().getKiller() instanceof Player) {
            Player killer = event.getEntity().getKiller();
            if (killer != null) plugin.getAchievementsManager().award(killer, "first_kill");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFirstBlockBreak(BlockBreakEvent event) {
        if (plugin.getAchievementsManager() == null) return;
        plugin.getAchievementsManager().award(event.getPlayer(), "first_block_break");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFirstPickup(EntityPickupItemEvent event) {
        if (plugin.getAchievementsManager() == null) return;
        if (event.getEntity() instanceof Player) {
            plugin.getAchievementsManager().award((Player) event.getEntity(), "first_item_pickup");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFirstCraft(CraftItemEvent event) {
        if (plugin.getAchievementsManager() == null) return;
        if (event.getWhoClicked() instanceof Player) {
            plugin.getAchievementsManager().award((Player) event.getWhoClicked(), "first_craft");
        }
    }
}
