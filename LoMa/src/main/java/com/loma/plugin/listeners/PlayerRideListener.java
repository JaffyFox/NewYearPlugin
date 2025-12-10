package com.loma.plugin.listeners;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;
import org.bukkit.Sound;
import org.bukkit.Particle;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

    public class PlayerRideListener implements Listener {
        private final LoMa plugin;
        // Anti-spam cooldown for throwing passengers
        private final Map<UUID, Long> throwCooldown = new HashMap<>();
        private static final long THROW_COOLDOWN_MS = 1200L; // 1.2s
        // Cooldown for mounting players
        private final Map<UUID, Long> mountCooldown = new HashMap<>();
        private long MOUNT_COOLDOWN_MS = 1500L; // default 1.5s

    public PlayerRideListener(LoMa plugin) {
        this.plugin = plugin;
        // Load cooldown from config (features.ride.cooldown in seconds)
        try {
            int sec = plugin.getConfig().getInt("features.ride.cooldown", 2);
            if (sec < 0) sec = 0;
            this.MOUNT_COOLDOWN_MS = sec * 1000L;
        } catch (Exception ignored) {}
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) return;

        Player clicker = event.getPlayer();
        Player target = (Player) event.getRightClicked();

        // Mount cooldown per clicker
        long now = System.currentTimeMillis();
        Long lastMount = mountCooldown.get(clicker.getUniqueId());
        if (lastMount != null && (now - lastMount) < MOUNT_COOLDOWN_MS) {
            long remain = (MOUNT_COOLDOWN_MS - (now - lastMount) + 999) / 1000L;
            MessageUtils.send(clicker, plugin.getMessage("ride.cooldown").replace("{time}", String.valueOf(remain)));
            return;
        }

        // Block riding Citizens NPCs or any entity marked as NPC
        try {
            if (target.hasMetadata("NPC")) return;
            // If Citizens API is present, also guard via registry
            try {
                if (net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(target)) return;
            } catch (NoClassDefFoundError ignored) {}
        } catch (Exception ignored) {}

        // Preferences: оба должны разрешать посадку
        if (plugin.getPreferencesManager() != null) {
            if (!plugin.getPreferencesManager().isEnabled(clicker.getUniqueId(), "ride", true)) return;
            if (!plugin.getPreferencesManager().isEnabled(target.getUniqueId(), "ride", true)) return;
        }

        // Avoid chains
        if (clicker.getPassengers().size() > 0) return;
        if (target.isInsideVehicle()) return;

        boolean ok = clicker.addPassenger(target);
        if (ok) {
            MessageUtils.send(clicker, plugin.getMessage("ride.mounted").replace("{player}", target.getName()));
            MessageUtils.send(target, plugin.getMessage("ride.you-mounted").replace("{player}", clicker.getName()));
            mountCooldown.put(clicker.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player p = event.getPlayer();
        // Если игрок запретил ride — принудительно разъединяем
        if (plugin.getPreferencesManager() != null &&
                !plugin.getPreferencesManager().isEnabled(p.getUniqueId(), "ride", true)) {
            if (!p.getPassengers().isEmpty()) p.eject();
            if (p.isInsideVehicle()) p.leaveVehicle();
            return;
        }
        if (!p.getPassengers().isEmpty()) {
            p.eject();
        }
        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }
    }

    @EventHandler
    public void onLeftClickThrow(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Player player = event.getPlayer();

        // Respect preferences
        if (plugin.getPreferencesManager() != null &&
                !plugin.getPreferencesManager().isEnabled(player.getUniqueId(), "ride", true)) {
            return;
        }

        if (player.getPassengers().isEmpty()) return;

        // Cooldown
        long now = System.currentTimeMillis();
        Long last = throwCooldown.get(player.getUniqueId());
        if (last != null && now - last < THROW_COOLDOWN_MS) {
            return;
        }
        throwCooldown.put(player.getUniqueId(), now);

        // Throw all passengers forward
        Vector dir = player.getLocation().getDirection().normalize();
        Vector impulse = dir.multiply(1.2);
        if (impulse.getY() < 0.4) impulse.setY(0.6);
        java.util.List<Entity> passengers = new java.util.ArrayList<>(player.getPassengers());
        for (Entity e : passengers) {
            player.removePassenger(e);
            // небольшой аплифтовый импульс
            Vector v = impulse.clone();
            if (v.getY() < 0.5) v.setY(0.5);
            e.setVelocity(v);
        }

        // FX
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1.0, 0), 15, 0.25, 0.25, 0.25, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.8f, 1.1f);
    }
}
