package com.loma.plugin.listeners;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.List;

    public class LobbyProtectionListener implements Listener {

    private final LoMa plugin;

    public LobbyProtectionListener(LoMa plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // Разрешить операторам (по требованию)
        if (player.isOp()) return;

        // Блокировка перетаскивания предметов в зоне лобби
        if (plugin.getSpawnManager().isInSpawnRadius(player.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // Разрешить операторам (по требованию)
        if (player.isOp()) return;

        // Блокировка перетаскивания предметов в зоне лобби
        if (plugin.getSpawnManager().isInSpawnRadius(player.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("protection.block-break", true)) return;

        Player player = event.getPlayer();

        // Проверка байпасса
        if (player.hasPermission("loma.bypass.build") || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // Проверка радиуса спавна
        if (plugin.getSpawnManager().isInSpawnRadius(event.getBlock().getLocation())) {
            event.setCancelled(true);
            MessageUtils.sendActionBar(player, plugin.getMessage("actionbar.spawn-protection"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("protection.block-place", true)) return;

        Player player = event.getPlayer();

        // Проверка байпасса
        if (player.hasPermission("loma.bypass.build") || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // Проверка радиуса спавна
        if (plugin.getSpawnManager().isInSpawnRadius(event.getBlock().getLocation())) {
            event.setCancelled(true);
            MessageUtils.sendActionBar(player, plugin.getMessage("actionbar.spawn-protection"));
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();

        // Проверка байпасса
        if (player.hasPermission("loma.bypass.damage")) {
            return;
        }

        // Отключение урона от падения
        if (plugin.getConfig().getBoolean("protection.fall-damage", true) &&
                event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            return;
        }

        // Отключение урона в целом
        if (plugin.getConfig().getBoolean("protection.damage", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("protection.pvp", true)) return;

        // Проверка PvP
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            Player damager = (Player) event.getDamager();

            if (!damager.hasPermission("loma.bypass.pvp")) {
                event.setCancelled(true);
                MessageUtils.sendActionBar(damager, plugin.getMessage("lobby.protection"));
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!plugin.getConfig().getBoolean("protection.hunger", true)) return;

        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            if (!player.hasPermission("loma.bypass.hunger")) {
                event.setCancelled(true);
                player.setFoodLevel(20);
                player.setSaturation(20);
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!plugin.getConfig().getBoolean("protection.item-drop", true)) return;

        Player player = event.getPlayer();

        if (!player.hasPermission("loma.bypass.drop")) {
            event.setCancelled(true);
            MessageUtils.sendActionBar(player, plugin.getMessage("lobby.protection"));
        }
    }

    @EventHandler
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (!plugin.getConfig().getBoolean("protection.item-pickup", true)) return;

        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            if (!player.hasPermission("loma.bypass.pickup")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!plugin.getConfig().getBoolean("protection.mob-spawn", true)) return;

        // Разрешаем спавн NPC
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM ||
                event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.COMMAND) {
            return;
        }

        // Проверка списка отключенных сущностей
        List<String> disabledEntities = plugin.getConfig().getStringList("performance.disable-entities");
        if (disabledEntities.contains(event.getEntityType().name())) {
            event.setCancelled(true);
            return;
        }

        // Проверка радиуса спавна
        if (plugin.getSpawnManager().isInSpawnRadius(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        if (!plugin.getConfig().getBoolean("protection.weather-damage", true)) return;

        // Отмена изменения погоды
        if (plugin.getConfig().getBoolean("spawn.world-settings.lock-weather", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        Material blockType = event.getClickedBlock().getType();

        // Проверка байпасса
        if (player.hasPermission("loma.bypass.interact")) {
            return;
        }

        // Проверка разрешенных взаимодействий
        List<String> allowed = plugin.getConfig().getStringList("protection.allowed-interactions");
        if (!allowed.contains(blockType.name())) {
            // Проверка радиуса спавна
            if (plugin.getSpawnManager().isInSpawnRadius(event.getClickedBlock().getLocation())) {
                event.setCancelled(true);
                MessageUtils.sendActionBar(player, plugin.getMessage("actionbar.spawn-protection"));
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Телепортация при падении в void
        if (plugin.getConfig().getBoolean("features.void-teleport.enabled")) {
            int voidLevel = plugin.getConfig().getInt("features.void-teleport.level", -10);

            if (player.getLocation().getY() <= voidLevel) {
                plugin.getSpawnManager().teleportToSpawn(player);
                MessageUtils.send(player, plugin.getMessage("lobby.void-teleport"));
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Защита от взаимодействия с рамками и стойками для брони
        if (event.getRightClicked().getType() == EntityType.ITEM_FRAME ||
                event.getRightClicked().getType() == EntityType.ARMOR_STAND) {

            Player player = event.getPlayer();

            if (!player.hasPermission("loma.bypass.interact")) {
                if (plugin.getSpawnManager().isInSpawnRadius(event.getRightClicked().getLocation())) {
                    event.setCancelled(true);
                    MessageUtils.sendActionBar(player, plugin.getMessage("actionbar.spawn-protection"));
                }
            }
        }
    }

    @EventHandler
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();

        if (!player.hasPermission("loma.bypass.build")) {
            if (plugin.getSpawnManager().isInSpawnRadius(event.getBlock().getLocation())) {
                event.setCancelled(true);
                MessageUtils.sendActionBar(player, plugin.getMessage("actionbar.spawn-protection"));
            }
        }
    }

    @EventHandler
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();

        if (!player.hasPermission("loma.bypass.build")) {
            if (plugin.getSpawnManager().isInSpawnRadius(event.getBlock().getLocation())) {
                event.setCancelled(true);
                MessageUtils.sendActionBar(player, plugin.getMessage("actionbar.spawn-protection"));
            }
        }
    }
}