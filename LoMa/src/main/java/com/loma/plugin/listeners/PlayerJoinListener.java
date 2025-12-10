package com.loma.plugin.listeners;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import com.loma.plugin.utils.ItemBuilder;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.FireworkEffect;

import java.util.*;

public class PlayerJoinListener implements Listener {

    private final LoMa plugin;
    private final Set<UUID> hiddenPlayers = new HashSet<>();

    public PlayerJoinListener(LoMa plugin) {
        this.plugin = plugin;
    }

    /** Глобальная анимация входа — частицы и звук, видны/слышны всем игрокам поблизости */
    private void playGlobalJoinEffect(Player player) {
        if (!plugin.getConfig().getBoolean("welcome.global-effect.enabled", true)) return;
        String particleName = plugin.getConfig().getString("welcome.global-effect.particle", "FIREWORK");
        String soundName = plugin.getConfig().getString("welcome.global-effect.sound", "ENTITY_PLAYER_LEVELUP");
        int rings = plugin.getConfig().getInt("welcome.global-effect.rings", 3);
        double radiusStep = plugin.getConfig().getDouble("welcome.global-effect.radius-step", 0.8);
        int ringPoints = plugin.getConfig().getInt("welcome.global-effect.points", 24);

        org.bukkit.World world = player.getWorld();
        org.bukkit.Location base = player.getLocation().clone().add(0, 0.2, 0);
        org.bukkit.Particle particleTmp = org.bukkit.Particle.FIREWORK;
        try { particleTmp = org.bukkit.Particle.valueOf(particleName); } catch (Exception ignored) {}
        final org.bukkit.World w = world;
        final org.bukkit.Location b = base;
        final org.bukkit.Particle p = particleTmp;

        // Кольцевые волны частиц наружу, видны всем (world.spawnParticle)
        new org.bukkit.scheduler.BukkitRunnable() {
            int ring = 0;
            @Override
            public void run() {
                if (ring >= rings) { cancel(); return; }
                double r = (ring + 1) * radiusStep;
                for (int i = 0; i < ringPoints; i++) {
                    double ang = (Math.PI * 2 / ringPoints) * i;
                    double x = Math.cos(ang) * r;
                    double z = Math.sin(ang) * r;
                    w.spawnParticle(p, b.getX() + x, b.getY(), b.getZ() + z, 3, 0.02, 0.02, 0.02, 0);
                }
                // Звук на весь мир в точке (услышат все рядом)
                try { w.playSound(b, soundName, 1.0f, 1.0f); } catch (Exception ignored) {}
                ring++;
            }
        }.runTaskTimer(plugin, 0L, 6L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Отключение стандартного сообщения
        event.setJoinMessage(null);

        boolean onlyCosmetics = plugin.getConfig().getBoolean("server-mode.cosmetics-only", false);

        // В режиме только косметики — применяем только кастомизацию и выходим
        if (onlyCosmetics) {
            if (plugin.getCosmeticsManager() != null) {
                try { plugin.getCosmeticsManager().applyOnJoin(player); } catch (Exception ignored) {}
            }
            return;
        }

        // Сообщение входа в лобби (всегда)
        String joinMessage = plugin.getMessage("lobby.join").replace("{player}", player.getName());
        if (joinMessage != null && !joinMessage.isEmpty()) {
            Bukkit.broadcastMessage(MessageUtils.color(joinMessage));
        }

        // Глобальная анимация входа (видна всем)
        playGlobalJoinEffect(player);

        // Телепортация на спавн
        if (plugin.getConfig().getBoolean("spawn.teleport-on-join")) {
            Location spawn = plugin.getSpawnManager().getSpawn();
            if (spawn != null) {
                player.teleport(spawn);
            }
        }

        // Установка режима игры
        String gamemode = plugin.getConfig().getString("spawn.world-settings.gamemode", "ADVENTURE");
        try {
            player.setGameMode(GameMode.valueOf(gamemode.toUpperCase()));
        } catch (IllegalArgumentException e) {
            player.setGameMode(GameMode.ADVENTURE);
        }

        // Сброс состояния игрока
        resetPlayer(player);

        // Выдача предметов лобби
        if (plugin.getConfig().getBoolean("features.lobby-items.enabled", true)) {
            giveLobbyItems(player);
        }

        // Применить сохранённую кастомизацию (шляпы/частицы)
        if (plugin.getCosmeticsManager() != null) {
            try { plugin.getCosmeticsManager().applyOnJoin(player); } catch (Exception ignored) {}
        }

        // Запуск приветственной анимации
        new BukkitRunnable() {
            @Override
            public void run() {
                playWelcomeAnimation(player);
            }
        }.runTaskLater(plugin, plugin.getConfig().getInt("welcome.delay", 20));

        // Обновление скорборда
        if (plugin.getConfig().getBoolean("features.scoreboard.enabled")) {
            plugin.getScoreboardManager().createScoreboard(player);
        }

        // Применение эффектов
        applyJoinEffects(player);

        // Проверка первого входа (визуальные эффекты/сообщение)
        if (!player.hasPlayedBefore()) {
            handleFirstJoin(player);
        }

        // Достижение «Первый вход»: выдать, если ещё не получено
        if (plugin.getAchievementsManager() != null) {
            try {
                if (!plugin.getAchievementsManager().has(player.getUniqueId(), "first_join")) {
                    plugin.getAchievementsManager().award(player, "first_join");
                }
            } catch (Exception ignored) {}
        }

        // Запрос статистики из Velocity (игровое время, ранг, достижения) для профиля
        try {
            plugin.requestPlayerStats(player, player.getUniqueId());
        } catch (Exception ignored) {}
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Отключение стандартного сообщения
        event.setQuitMessage(null);

        // Кастомное сообщение выхода
        if (plugin.getConfig().getBoolean("features.custom-quit-messages", false)) {
            String quitMessage = getQuitMessage(player);
            if (quitMessage != null && !quitMessage.isEmpty()) {
                Bukkit.broadcastMessage(MessageUtils.color(quitMessage));
            }
        }

        // Удаление из скорборда
        if (plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().removeScoreboard(player);
        }

        // Очистка данных
        hiddenPlayers.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (plugin.getConfig().getBoolean("spawn.teleport-on-respawn")) {
            Location spawn = plugin.getSpawnManager().getSpawn();
            if (spawn != null) {
                event.setRespawnLocation(spawn);

                // Анимация респавна
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        playSpawnAnimation(event.getPlayer());
                    }
                }.runTaskLater(plugin, 1);
            }
        }
    }

    private void playWelcomeAnimation(Player player) {
        // Приветственное сообщение в чате
        if (plugin.getConfig().getBoolean("welcome.messages")) {
            List<String> messages = plugin.getConfig().getStringList("welcome.messages");
            for (String message : messages) {
                player.sendMessage(MessageUtils.color(message
                        .replace("{player}", player.getName())
                        .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                        .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()))));
            }
        }

        // Title сообщение
        if (plugin.getConfig().getBoolean("welcome.title.enabled")) {
            String title = MessageUtils.color(plugin.getConfig().getString("welcome.title.title")
                    .replace("{player}", player.getName()));
            String subtitle = MessageUtils.color(plugin.getConfig().getString("welcome.title.subtitle")
                    .replace("{player}", player.getName()));
            int fadeIn = plugin.getConfig().getInt("welcome.title.fade-in");
            int stay = plugin.getConfig().getInt("welcome.title.stay");
            int fadeOut = plugin.getConfig().getInt("welcome.title.fade-out");

            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }

        // Звуковой эффект
        if (plugin.getConfig().getBoolean("welcome.sound.enabled")) {
            String soundName = plugin.getConfig().getString("welcome.sound.sound");
            float volume = (float) plugin.getConfig().getDouble("welcome.sound.volume");
            float pitch = (float) plugin.getConfig().getDouble("welcome.sound.pitch");
            if (soundName != null && !soundName.isEmpty()) {
                String key = soundName.toLowerCase();
                try { player.playSound(player.getLocation(), key, volume, pitch); } catch (Exception ignored) {}
            }
        }

        // Фейерверк для VIP
        if (plugin.getConfig().getBoolean("welcome.firework.enabled")) {
            String permission = plugin.getConfig().getString("welcome.firework.permission");
            if (permission == null || player.hasPermission(permission)) {
                launchFireworks(player);
            }
        }

        // Анимация спавна
        playSpawnAnimation(player);
    }

    private void playSpawnAnimation(Player player) {
        Location loc = player.getLocation();

        // Частицы спавна
        if (plugin.getConfig().getBoolean("animations.spawn-particles.enabled")) {
            String particleType = plugin.getConfig().getString("animations.spawn-particles.type");
            int amount = plugin.getConfig().getInt("animations.spawn-particles.amount");
            double radius = plugin.getConfig().getDouble("animations.spawn-particles.radius");

            try {
                Particle particle = Particle.valueOf(particleType);

                // Круговая анимация частиц
                new BukkitRunnable() {
                    int tick = 0;

                    @Override
                    public void run() {
                        if (tick >= 20) {
                            cancel();
                            return;
                        }

                        double angle = tick * Math.PI / 10;
                        for (int i = 0; i < 3; i++) {
                            double x = Math.cos(angle + i * Math.PI * 2 / 3) * radius;
                            double z = Math.sin(angle + i * Math.PI * 2 / 3) * radius;
                            Location particleLoc = loc.clone().add(x, tick * 0.1, z);
                            player.getWorld().spawnParticle(particle, particleLoc, 5, 0.1, 0.1, 0.1, 0);
                        }

                        tick++;
                    }
                }.runTaskTimer(plugin, 0, 1);

            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid particle type: " + particleType);
            }
        }

        // Молния (визуальная)
        if (plugin.getConfig().getBoolean("animations.lightning.enabled")) {
            String permission = plugin.getConfig().getString("animations.lightning.permission");
            if (permission == null || player.hasPermission(permission)) {
                player.getWorld().strikeLightningEffect(loc);
            }
        }

        // Анимация телепортации
        if (plugin.getConfig().getBoolean("animations.teleport.enabled")) {
            String particleType = plugin.getConfig().getString("animations.teleport.particles");
            String soundName = plugin.getConfig().getString("animations.teleport.sound");

            try {
                Particle particle = Particle.valueOf(particleType);
                player.getWorld().spawnParticle(particle, loc, 100, 0.5, 1, 0.5, 0.1);

                if (soundName != null && !soundName.isEmpty()) {
                    String key = soundName.toLowerCase();
                    try { player.playSound(loc, key, 1.0f, 1.0f); } catch (Exception ignored) {}
                }
            } catch (IllegalArgumentException e) {
                // Игнорируем ошибки
            }
        }
    }

    private void launchFireworks(Player player) {
        int amount = plugin.getConfig().getInt("welcome.firework.amount", 3);

        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= amount) {
                    cancel();
                    return;
                }

                Location loc = player.getLocation();
                Firework firework = player.getWorld().spawn(loc, Firework.class);
                FireworkMeta meta = firework.getFireworkMeta();

                // Случайные цвета
                Random random = new Random();
                Color color1 = getRandomColor(random);
                Color color2 = getRandomColor(random);

                // Случайный тип
                FireworkEffect.Type type = getRandomType(random);

                FireworkEffect effect = FireworkEffect.builder()
                        .with(type)
                        .withColor(color1, color2)
                        .withFade(Color.WHITE)
                        .trail(true)
                        .flicker(true)
                        .build();

                meta.addEffect(effect);
                meta.setPower(1);
                firework.setFireworkMeta(meta);

                count++;
            }
        }.runTaskTimer(plugin, 0, 10);
    }

    private Color getRandomColor(Random random) {
        Color[] colors = {
                Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW,
                Color.PURPLE, Color.ORANGE, Color.AQUA, Color.FUCHSIA
        };
        return colors[random.nextInt(colors.length)];
    }

    private FireworkEffect.Type getRandomType(Random random) {
        FireworkEffect.Type[] types = {
                FireworkEffect.Type.BALL, FireworkEffect.Type.BALL_LARGE,
                FireworkEffect.Type.BURST, FireworkEffect.Type.STAR
        };
        return types[random.nextInt(types.length)];
    }

    private void resetPlayer(Player player) {
        // Очистка эффектов
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Восстановление здоровья и еды
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setExhaustion(0);

        // Сброс опыта
        player.setExp(0);
        player.setLevel(0);

        // Удаление огня
        player.setFireTicks(0);

        // Сброс полёта — доступен всем по настройке профиля
        boolean flyPref = plugin.getPreferencesManager() == null || plugin.getPreferencesManager().isEnabled(player.getUniqueId(), "fly", false);
        player.setAllowFlight(flyPref);
        if (!flyPref && player.isFlying()) player.setFlying(false);
    }

    private void giveLobbyItems(Player player) {
        java.io.File menusDir = new java.io.File(plugin.getDataFolder(), "menus");
        if (!menusDir.exists()) menusDir.mkdirs();
        java.io.File file = new java.io.File(menusDir, "lobby-items.yml");
        if (!file.exists()) {
            plugin.saveResource("menus/lobby-items.yml", false);
        }

        org.bukkit.configuration.file.FileConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

        if (cfg.getBoolean("clear-inventory", true)) {
            player.getInventory().clear();
        }

        org.bukkit.configuration.ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items == null) return;

        for (String key : items.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection s = items.getConfigurationSection(key);
            if (s == null) continue;

            // option to disable specific item
            if (!s.getBoolean("enabled", true)) continue;

            int slot = s.getInt("slot", -1);
            if (slot < 0 || slot > 8) continue;

            Material material = safeMaterial(s.getString("material", "STONE"));
            String name = resolveText(cfg, s.getString("name", key));
            // Подстановки плейсхолдеров для имени игрока
            name = name.replace("{player}", player.getName()).replace("{displayname}", player.getDisplayName());
            java.util.List<String> lore = resolveTextList(cfg, s.getStringList("lore"));
            lore = lore.stream()
                    .map(t -> t.replace("{player}", player.getName()).replace("{displayname}", player.getDisplayName()))
                    .collect(java.util.stream.Collectors.toList());
            String action = s.getString("action", "");

            com.loma.plugin.utils.ItemBuilder builder = new com.loma.plugin.utils.ItemBuilder(material)
                    .setName(name)
                    .setLore(lore)
                    .addNBTTag("lobbyitem", key)
                    .addNBTTag("action", action);
            // Для головы игрока — установить владельца, чтобы показывалась скин-голова
            if (material == Material.PLAYER_HEAD) {
                builder.setSkullOwner(player.getName());
            }

            ItemStack item = builder.build();

            // Optional glowing
            if (s.getBoolean("glowing", false)) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    org.bukkit.enchantments.Enchantment[] all = org.bukkit.enchantments.Enchantment.values();
                    org.bukkit.enchantments.Enchantment any = all.length > 0 ? all[0] : null;
                    if (any != null) {
                        meta.addEnchant(any, 1, true);
                        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                        item.setItemMeta(meta);
                    }
                }
            }

            player.getInventory().setItem(slot, item);
        }
    }

    private Material safeMaterial(String name) {
        try { return Material.valueOf(name.toUpperCase()); } catch (Exception e) { return Material.STONE; }
    }

    private String resolveText(org.bukkit.configuration.file.FileConfiguration msgsCfg, String raw) {
        if (raw == null) return "";
        String base;
        // Поддержка локализационных ключей
        if (plugin.getMessagesConfig() != null && plugin.getMessagesConfig().contains(raw)) {
            base = plugin.getMessagesConfig().getString(raw);
        } else {
            base = raw;
        }
        // Градиенты/HEX
        return com.loma.plugin.utils.GradientUtils.applyGradient(base);
    }

    private java.util.List<String> resolveTextList(org.bukkit.configuration.file.FileConfiguration msgsCfg, java.util.List<String> list) {
        return list == null ? java.util.Collections.emptyList() : list.stream().map(s -> resolveText(msgsCfg, s)).collect(java.util.stream.Collectors.toList());
    }

    private void applyJoinEffects(Player player) {
        // Система званий/привилегий отключена — не выдаем бонусные эффекты
    }

    private void handleFirstJoin(Player player) {
        // Сообщение о первом входе
        String firstJoinMessage = plugin.getMessage("join-quit.first-join")
                .replace("{player}", player.getName());
        Bukkit.broadcastMessage(MessageUtils.color(firstJoinMessage));

        // Особая анимация
        new BukkitRunnable() {
            @Override
            public void run() {
                Location loc = player.getLocation();
                for (int i = 0; i < 5; i++) {
                    player.getWorld().spawnParticle(Particle.FIREWORK, loc, 50, 1, 1, 1, 0.1);
                }
                player.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        }.runTaskLater(plugin, 20);

        // Начальные награды (если настроено)
        if (plugin.getConfig().getBoolean("features.first-join-rewards", false)) {
            // Здесь можно добавить выдачу начальных наград
        }

        // Достижение: Первый вход
        if (plugin.getAchievementsManager() != null) {
            try { plugin.getAchievementsManager().award(player, "first_join"); } catch (Exception ignored) {}
        }
    }

    private String getJoinMessage(Player player) {
        return plugin.getMessage("join-quit.join.default").replace("{player}", player.getName());
    }

    private String getQuitMessage(Player player) {
        return plugin.getMessage("join-quit.quit.default").replace("{player}", player.getName());
    }

    public boolean isPlayerHidden(Player player) {
        return hiddenPlayers.contains(player.getUniqueId());
    }

    public void togglePlayerVisibility(Player player) {
        if (hiddenPlayers.contains(player.getUniqueId())) {
            hiddenPlayers.remove(player.getUniqueId());
            for (Player other : Bukkit.getOnlinePlayers()) {
                player.showPlayer(plugin, other);
            }
            MessageUtils.send(player, plugin.getMessage("visibility.players-shown"));
        } else {
            hiddenPlayers.add(player.getUniqueId());
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)) {
                    player.hidePlayer(plugin, other);
                }
            }
            MessageUtils.send(player, plugin.getMessage("visibility.players-hidden"));
        }
    }
}