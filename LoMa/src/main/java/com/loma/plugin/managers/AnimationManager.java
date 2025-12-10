package com.loma.plugin.managers;

import com.loma.plugin.LoMa;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public class AnimationManager {

    private final LoMa plugin;
    private final Map<String, ParticleAnimation> animations;

    public AnimationManager(LoMa plugin) {
        this.plugin = plugin;
        this.animations = new HashMap<>();

        registerDefaultAnimations();
    }

    private void registerDefaultAnimations() {
        // Спиральная анимация
        animations.put("spiral", (player, particle) -> {
            Location loc = player.getLocation();
            new BukkitRunnable() {
                double angle = 0;
                double y = 0;
                int ticks = 0;

                @Override
                public void run() {
                    if (ticks > 40) {
                        cancel();
                        return;
                    }

                    double x = Math.cos(angle) * 1.5;
                    double z = Math.sin(angle) * 1.5;

                    Location particleLoc = loc.clone().add(x, y, z);
                    player.getWorld().spawnParticle(particle, particleLoc, 3, 0, 0, 0, 0);

                    angle += Math.PI / 8;
                    y += 0.1;
                    ticks++;
                }
            }.runTaskTimer(plugin, 0, 1);
        });

        // Круговая анимация
        animations.put("circle", (player, particle) -> {
            Location loc = player.getLocation();
            new BukkitRunnable() {
                int step = 0;

                @Override
                public void run() {
                    if (step > 20) {
                        cancel();
                        return;
                    }

                    for (int i = 0; i < 360; i += 10) {
                        double angle = Math.toRadians(i);
                        double x = Math.cos(angle) * 2;
                        double z = Math.sin(angle) * 2;

                        Location particleLoc = loc.clone().add(x, 0.5, z);
                        player.getWorld().spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0);
                    }

                    step++;
                }
            }.runTaskTimer(plugin, 0, 5);
        });

        // Крылья
        animations.put("wings", (player, particle) -> {
            new BukkitRunnable() {
                double angle = 0;

                @Override
                public void run() {
                    Location loc = player.getLocation();

                    // Левое крыло
                    for (double y = 0; y < 2; y += 0.1) {
                        double x = Math.sin(angle) * (2 - y);
                        Location wingLoc = loc.clone().add(-x, y, 0);
                        Vector dir = player.getLocation().getDirection();
                        wingLoc = rotateAroundY(wingLoc, loc, dir.getY());
                        player.getWorld().spawnParticle(particle, wingLoc, 1, 0, 0, 0, 0);
                    }

                    // Правое крыло
                    for (double y = 0; y < 2; y += 0.1) {
                        double x = Math.sin(angle) * (2 - y);
                        Location wingLoc = loc.clone().add(x, y, 0);
                        Vector dir = player.getLocation().getDirection();
                        wingLoc = rotateAroundY(wingLoc, loc, dir.getY());
                        player.getWorld().spawnParticle(particle, wingLoc, 1, 0, 0, 0, 0);
                    }

                    angle += 0.1;
                    if (angle > Math.PI) {
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 0, 2);
        });

        // Взрыв частиц
        animations.put("explosion", (player, particle) -> {
            Location loc = player.getLocation();

            for (int i = 0; i < 100; i++) {
                double x = (Math.random() - 0.5) * 3;
                double y = Math.random() * 2;
                double z = (Math.random() - 0.5) * 3;

                Location particleLoc = loc.clone().add(x, y, z);
                Vector direction = particleLoc.toVector().subtract(loc.toVector()).normalize();

                player.getWorld().spawnParticle(particle, particleLoc, 0,
                        direction.getX(), direction.getY(), direction.getZ(), 0.3);
            }

            player.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.0f);
        });

        // Сердечки вокруг игрока
        animations.put("hearts", (player, particle) -> {
            Location loc = player.getLocation();

            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (ticks > 60) {
                        cancel();
                        return;
                    }

                    for (int i = 0; i < 5; i++) {
                        double angle = Math.random() * Math.PI * 2;
                        double x = Math.cos(angle) * 1.5;
                        double z = Math.sin(angle) * 1.5;
                        double y = Math.random() * 2;

                        Location heartLoc = loc.clone().add(x, y, z);
                        player.getWorld().spawnParticle(Particle.HEART, heartLoc, 1, 0, 0, 0, 0);
                    }

                    ticks++;
                }
            }.runTaskTimer(plugin, 0, 2);
        });
    }

    public void playParticleEffect(Player player, String effectName) {
        ParticleAnimation animation = animations.get(effectName.toLowerCase());
        if (animation != null) {
            animation.play(player, Particle.FIREWORK);
        }
    }

    public void playCustomEffect(Player player, String effectName, Particle particle) {
        ParticleAnimation animation = animations.get(effectName.toLowerCase());
        if (animation != null) {
            animation.play(player, particle);
        }
    }

    public void playTeleportAnimation(Player player) {
        Location loc = player.getLocation();

        // Частицы телепортации
        player.getWorld().spawnParticle(Particle.PORTAL, loc, 100, 0.5, 1, 0.5, 0.1);
        player.getWorld().spawnParticle(Particle.ENCHANT, loc, 50, 0.5, 1, 0.5, 0);

        // Звук телепортации
        player.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        // Круговая волна частиц
        new BukkitRunnable() {
            double radius = 0;

            @Override
            public void run() {
                if (radius > 3) {
                    cancel();
                    return;
                }

                for (int i = 0; i < 360; i += 10) {
                    double angle = Math.toRadians(i);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;

                    Location particleLoc = loc.clone().add(x, 0.1, z);
                    player.getWorld().spawnParticle(Particle.WITCH, particleLoc, 1, 0, 0, 0, 0);
                }

                radius += 0.3;
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    public void playDoubleJumpAnimation(Player player) {
        Location loc = player.getLocation();

        // Облако под ногами
        player.getWorld().spawnParticle(Particle.CLOUD, loc, 20, 0.3, 0.1, 0.3, 0);

        // Звук
        player.playSound(loc, Sound.ENTITY_BAT_TAKEOFF, 0.5f, 1.0f);

        // След из частиц
        new BukkitRunnable() {
            int ticks = 0;
            Location lastLoc = loc.clone();

            @Override
            public void run() {
                if (!player.isOnline() || player.isOnGround() || ticks > 20) {
                    cancel();
                    return;
                }

                Location currentLoc = player.getLocation();
                Vector direction = currentLoc.toVector().subtract(lastLoc.toVector());

                if (direction.length() > 0) {
                    direction.normalize();
                    for (double i = 0; i < currentLoc.distance(lastLoc); i += 0.2) {
                        Location particleLoc = lastLoc.clone().add(direction.clone().multiply(i));
                        player.getWorld().spawnParticle(Particle.FIREWORK, particleLoc, 1, 0, 0, 0, 0);
                    }
                }

                lastLoc = currentLoc.clone();
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    public void registerAnimation(String name, ParticleAnimation animation) {
        animations.put(name.toLowerCase(), animation);
    }

    public java.util.Set<String> getAnimationNames() {
        return new java.util.HashSet<>(animations.keySet());
    }

    private Location rotateAroundY(Location loc, Location center, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        double x = loc.getX() - center.getX();
        double z = loc.getZ() - center.getZ();

        double newX = x * cos - z * sin;
        double newZ = x * sin + z * cos;

        return center.clone().add(newX, loc.getY() - center.getY(), newZ);
    }

    @FunctionalInterface
    public interface ParticleAnimation {
        void play(Player player, Particle particle);
    }
}