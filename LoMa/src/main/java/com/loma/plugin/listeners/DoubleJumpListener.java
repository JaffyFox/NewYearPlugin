package com.loma.plugin.listeners;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DoubleJumpListener implements Listener {

    private final LoMa plugin;
    private final Map<UUID, Long> cooldowns;
    // Временное «окно» после двойного прыжка, в течение которого повторная двойная нажатиe Space включает режим полёта
    private final Map<UUID, Long> flyArmUntil = new HashMap<>();
    private final NamespacedKey pdcLobbyItemKey;
    private final NamespacedKey pdcActionKey;

    public DoubleJumpListener(LoMa plugin) {
        this.plugin = plugin;
        this.cooldowns = new HashMap<>();
        this.pdcLobbyItemKey = new NamespacedKey(plugin, "lobbyitem");
        this.pdcActionKey = new NamespacedKey(plugin, "action");
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        // Игнорируем если игрок в креативе или зрителе
        if (player.getGameMode() == GameMode.CREATIVE ||
                player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        boolean djEnabled = plugin.getPreferencesManager() == null || plugin.getPreferencesManager().isEnabled(player.getUniqueId(), "doublejump", true);
        boolean flyEnabledPref = plugin.getPreferencesManager() != null && plugin.getPreferencesManager().isEnabled(player.getUniqueId(), "fly", false);
        boolean hasDJPerm = true; // все игроки имеют доступ
        boolean hasFlyPerm = true; // все игроки имеют доступ

        // Если двойной прыжок выключен — не вмешиваемся (пусть обрабатывается обычный полёт, если разрешён)
        if (!djEnabled || !hasDJPerm) {
            return;
        }

        boolean holdingServerSelector = isServerSelector(player.getInventory().getItemInMainHand())
                || isServerSelector(player.getInventory().getItemInOffHand());
        if (holdingServerSelector) {
            // Полностью блокируем двойной прыжок, чтобы избежать перемещения вперёд при использовании компаса
            event.setCancelled(true);
            player.setAllowFlight(false);
            player.setFlying(false);
            flyArmUntil.remove(player.getUniqueId());
            return;
        }

        long now = System.currentTimeMillis();

        if (player.isFlying()) {
            flyArmUntil.remove(player.getUniqueId());
            return;
        }

        Long arm = flyArmUntil.get(player.getUniqueId());
        if (arm != null && now <= arm && flyEnabledPref && hasFlyPerm) {
            // Во время окна — разрешаем полёт, не отменяем событие
            flyArmUntil.remove(player.getUniqueId());
            return;
        }

        // Проверка кулдауна
        if (isOnCooldown(player)) {
            long remaining = getCooldownRemaining(player);
            MessageUtils.sendActionBar(player, plugin.getMessage("doublejump.cooldown")
                    .replace("{time}", String.valueOf(remaining)));
            event.setCancelled(true);
            return;
        }

        // Отмена полета и выполнение двойного прыжка (первое двойное нажатие)
        event.setCancelled(true);
        player.setAllowFlight(false);
        player.setFlying(false);

        // Применение силы прыжка
        double force = plugin.getConfig().getDouble("features.double-jump.force", 1.5);
        double height = plugin.getConfig().getDouble("features.double-jump.height", 0.8);

        Vector direction = player.getLocation().getDirection();
        Vector velocity = direction.multiply(force).setY(height);
        player.setVelocity(velocity);

        // Анимация
        plugin.getAnimationManager().playDoubleJumpAnimation(player);

        // Звук: играем по ресурсному ключу (строка), без использования устаревшего enum valueOf
        String soundName = plugin.getConfig().getString("features.double-jump.sound");
        if (soundName != null && !soundName.isEmpty()) {
            String key = soundName.toLowerCase();
            try { player.playSound(player.getLocation(), key, 0.5f, 1.0f); } catch (Exception ignored) {}
        }

        // Установка кулдауна
        setCooldown(player);

        // Отправка сообщения в action bar (локализовано)
        MessageUtils.sendActionBar(player, plugin.getMessage("doublejump.enabled"));

        // Если включён fly и есть право — через малую задержку снова разрешаем попытку полёта
        if (flyEnabledPref && hasFlyPerm) {
            long windowMs = 1500L; // окно 1.5 секунды, в это время повторная двойная клавиша включает fly
            flyArmUntil.put(player.getUniqueId(), now + windowMs);
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                player.setAllowFlight(true);
            }, 1L);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Игнорируем если игрок в креативе или зрителе
        if (player.getGameMode() == GameMode.CREATIVE ||
                player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // Проверка настроек игрока и прав
        if (plugin.getPreferencesManager() != null &&
                !plugin.getPreferencesManager().isEnabled(player.getUniqueId(), "doublejump", true)) {
            return;
        }
        if (!player.hasPermission("loma.doublejump")) {
            return;
        }

        if (player.isOnGround() && !player.getAllowFlight()) {
            player.setAllowFlight(true);

            // Подсказка «готов к двойному прыжку» — только если включено в конфиге
            if (plugin.getConfig().getBoolean("features.double-jump.show-ready-hint", false) && !isOnCooldown(player)) {
                MessageUtils.sendActionBar(player, plugin.getMessage("actionbar.double-jump-ready"));
            }
        }
    }

    private boolean isOnCooldown(Player player) {
        Long last = cooldowns.get(player.getUniqueId());
        if (last == null) return false;
        long cooldown = plugin.getConfig().getLong("features.double-jump.cooldown", 5) * 1000L;
        return System.currentTimeMillis() - last < cooldown;
    }

    private long getCooldownRemaining(Player player) {
        int cooldownTime = plugin.getConfig().getInt("features.double-jump.cooldown", 3);
        UUID uuid = player.getUniqueId();
        if (!cooldowns.containsKey(uuid)) return 0;

        long lastJump = cooldowns.get(uuid);
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastJump;
        long cooldownMs = cooldownTime * 1000L;

        if (elapsed >= cooldownMs) return 0;

        return (cooldownMs - elapsed) / 1000L;
    }

    private void setCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private boolean isServerSelector(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(pdcLobbyItemKey, PersistentDataType.STRING)) return false;
        String action = pdc.get(pdcActionKey, PersistentDataType.STRING);
        return action != null && action.toLowerCase().startsWith("menu:servers");
    }
}