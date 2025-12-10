package com.loma.plugin.managers;

import com.loma.plugin.LoMa;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;
import java.io.IOException;

/**
 * Manages cosmetic particle effects selected by players.
 */
public class CosmeticsManager {
    private final LoMa plugin;
    private final Map<UUID, Selection> particles = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> previousHelmets = new ConcurrentHashMap<>();
    private final Map<UUID, String> hats = new ConcurrentHashMap<>();
    private BukkitTask task;

    // storage
    private File file;
    private FileConfiguration cfg;
    // particles parameters
    private File particlesFile;
    private FileConfiguration particlesConfig;
    // Thread-local текущая выборка для подстановки параметров
    private final ThreadLocal<Selection> currentSel = new ThreadLocal<>();

    public CosmeticsManager(LoMa plugin) {
        this.plugin = plugin;
        initStorage();
        loadParticlesConfig();
        loadFromDisk();
        startTask();
    }

    // ===== HATS =====
    public void setHat(UUID uuid, String hatId) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            // всё равно запомним выбор, применится при входе
            hats.put(uuid, hatId);
            save(uuid);
            // синхронизируем с прокси
            Selection sel = particles.get(uuid);
            plugin.setCosmeticsOnProxy(uuid, hatId, sel == null ? null : sel.particle, sel == null ? null : sel.animation);
            return;
        }

        // Если на этом сервере косметика запрещена — сохраняем выбор и синхронизируем, но локально не применяем
        if (!isCosmeticsAllowedHere()) {
            hats.put(uuid, hatId);
            save(uuid);
            Selection sel = particles.get(uuid);
            plugin.setCosmeticsOnProxy(uuid, hatId, sel == null ? null : sel.particle, sel == null ? null : sel.animation);
            return;
        }

        // Сохраняем текущий шлем один раз
        previousHelmets.putIfAbsent(uuid, safeCopy(player.getInventory().getHelmet()));

        ItemStack hat;
        switch (hatId.toLowerCase()) {
            case "govnyashka":
            case "poop":
                hat = new ItemStack(Material.LEATHER_HELMET);
                LeatherArmorMeta meta = (LeatherArmorMeta) hat.getItemMeta();
                if (meta != null) {
                    meta.setColor(Color.fromRGB(0x6b4b1b)); // коричневый
                    meta.setDisplayName(com.loma.plugin.utils.MessageUtils.color("&6Говняшка"));
                    hat.setItemMeta(meta);
                }
                break;
            default:
                hat = new ItemStack(Material.CARVED_PUMPKIN);
                break;
        }
        // Mark hat as locked with PDC to block removal
        try {
            ItemMeta im = hat.getItemMeta();
            if (im != null) {
                im.getPersistentDataContainer().set(new NamespacedKey(plugin, "hat"), PersistentDataType.STRING, "1");
                hat.setItemMeta(im);
            }
        } catch (Exception ignored) {}

        player.getInventory().setHelmet(hat);
        player.updateInventory();

        // persist
        hats.put(uuid, hatId);
        save(uuid);
        // отправляем на прокси актуальное состояние
        Selection sel = particles.get(uuid);
        plugin.setCosmeticsOnProxy(uuid, hatId, sel == null ? null : sel.particle, sel == null ? null : sel.animation);
    }

    public void clearHat(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        ItemStack prev = previousHelmets.remove(uuid);
        player.getInventory().setHelmet(prev);
        player.updateInventory();
        hats.remove(uuid);
        save(uuid);
        // синхронизация с прокси (hat = null)
        Selection sel = particles.get(uuid);
        plugin.setCosmeticsOnProxy(uuid, null, sel == null ? null : sel.particle, sel == null ? null : sel.animation);
    }

    private ItemStack safeCopy(ItemStack it) {
        return it == null ? null : it.clone();
    }

    public void setParticle(UUID uuid, String animation, String particleName) {
        setParticle(uuid, animation, particleName, null);
    }

    public void setParticle(UUID uuid, String animation, String particleName, java.util.Map<String, String> params) {
        // Если на этом сервере косметика запрещена — не сохраняем локально (чтобы не рендерить), только отправим на прокси
        if (!isCosmeticsAllowedHere()) {
            String hat = hats.get(uuid);
            plugin.setCosmeticsOnProxy(uuid, hat, particleName, animation);
            return;
        }
        particles.put(uuid, new Selection(animation, particleName, params));
        save(uuid);
        // синхронизация с прокси
        String hat = hats.get(uuid);
        plugin.setCosmeticsOnProxy(uuid, hat, particleName, animation);
    }

    public void clearParticle(UUID uuid) {
        particles.remove(uuid);
        save(uuid);
        // синхронизация с прокси (particle = null)
        String hat = hats.get(uuid);
        plugin.setCosmeticsOnProxy(uuid, hat, null, null);
    }

    public void shutdown() {
        if (task != null) {
            try { task.cancel(); } catch (Exception ignored) {}
        }
        // persist all before clearing
        saveAll();
        particles.clear();
        previousHelmets.clear();
        hats.clear();
    }

    private void startTask() {
        // Лёгкий рендер частиц каждые 2 тика, кадры привязаны к текущей позиции игрока
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (particles.isEmpty()) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                Selection sel = particles.get(player.getUniqueId());
                if (sel == null) continue;
                // Учитываем настройки игрока
                if (plugin.getPreferencesManager() != null &&
                        !plugin.getPreferencesManager().isEnabled(player.getUniqueId(), "particles", true)) {
                    continue;
                }
                emitFrame(player, sel);
            }
        }, 10L, 2L); // старт через 0.5с, затем каждые 2 тика (~10 FPS)
    }

    private void emitFrame(Player player, Selection sel) {
        if (!isCosmeticsAllowedHere()) return;
        Particle p = Particle.FIREWORK;
        try { p = Particle.valueOf(sel.particle.toUpperCase()); } catch (Exception ignored) {}

        Location base = player.getLocation();
        // Подставим текущую selection, чтобы param()/iparam() могли читать оверрайды
        currentSel.set(sel);
        try {
            // Предварительная обработка расширенных анимаций
            String animName = sel.animation == null ? "" : sel.animation.toLowerCase();
            if (animName.equals("orbit") || animName.equals("halo") || animName.equals("double_helix")
                    || animName.equals("tornado") || animName.equals("aura")
                    || animName.equals("halo_rise") || animName.equals("sphere_pulse")) {
                playExtraAnimations(player, sel, p, base);
                return;
            }
            switch (sel.animation.toLowerCase()) {
            // ===== ВНИЗ-ВВЕРХ: из пола вверх =====
            case "column_up": {
                // Несколько вертикальных струй, равномерно по окружности
                double height = param("column_up", "height", 2.4);
                int jets = (int) iparam("column_up", "jets", 6);
                double baseY = param("column_up", "base-y", 0.05); // из пола
                double radius = param("column_up", "radius", 0.8);
                double angSpeed = param("column_up", "ang-speed", 0.12);
                double riseSpeed = param("column_up", "rise-speed", 0.06);
                double phaseOffset = param("column_up", "phase-offset", 0.2);
                for (int i = 0; i < jets; i++) {
                    double a = (Math.PI * 2 / jets) * i + sel.phase * angSpeed;
                    double x = Math.cos(a) * radius;
                    double z = Math.sin(a) * radius;
                    // подъём вдоль струи
                    double t = (sel.phase * riseSpeed + i * phaseOffset) % 1.0; // 0..1
                    double y = baseY + t * height;
                    spawnCosmeticParticle(player, p, base.clone().add(x, y, z), 2, 0.01, 0.01, 0.01, 0);
                }
                sel.phase += 1.0;
                break;
            }
            case "fountain":
            case "geyser_up": {
                // Фонтан из центра вверх с лёгким разбросом
                double height = param("geyser_up", "height", 2.8);
                double speed = param("geyser_up", "speed", 0.08);
                double baseY = param("geyser_up", "base-y", 0.1);
                double spreadBase = param("geyser_up", "spread-base", 0.2);
                double spreadSlope = param("geyser_up", "spread-slope", 0.6);
                double t = (sel.phase * speed) % 1.0; // 0..1
                double y = baseY + t * height;
                double spread = spreadBase + t * spreadSlope;
                spawnCosmeticParticle(player, p, base.clone().add(0, y, 0), 8, spread, 0.05, spread, 0.0);
                sel.phase += 1.0;
                break;
            }
            case "double_spiral_up": {
                // Две противоположные спирали, поднимающиеся вверх
                double height = param("double_spiral_up", "height", 2.2);
                double radius = param("double_spiral_up", "radius", 0.9);
                double speed = param("double_spiral_up", "speed", 0.06);
                double angSpeed = param("double_spiral_up", "ang-speed", 0.22);
                double baseY = param("double_spiral_up", "base-y", 0.2);
                double t = (sel.phase * speed) % 1.0;
                double y = baseY + t * height;
                double ang = sel.phase * angSpeed;
                for (int sign = -1; sign <= 1; sign += 2) {
                    double a = ang * sign;
                    double x = Math.cos(a) * radius;
                    double z = Math.sin(a) * radius;
                    spawnCosmeticParticle(player, p, base.clone().add(x, y, z), 2, 0.01, 0.01, 0.01, 0);
                }
                sel.phase += 1.0;
                break;
            }

            // ===== СВЕРХУ ВНИЗ: дождь =====
            case "rain_down":
            case "rain":
            case "shower_down": {
                // Спавним несколько капель/частиц на высоте над игроком с небольшим горизонтальным разбросом
                double radius = param("rain_down", "radius", 1.6);
                double top = param("rain_down", "top", 3.2); // высота начала дождя над игроком
                double fallSpeed = param("rain_down", "speed", 0.06);
                int drops = (int) iparam("rain_down", "drops", 6);
                for (int i = 0; i < drops; i++) {
                    double a = Math.random() * Math.PI * 2;
                    double r = Math.random() * radius;
                    double x = Math.cos(a) * r;
                    double z = Math.sin(a) * r;
                    double y = top - (sel.phase * fallSpeed % (top + 0.2)); // эффект стекает вниз по кадрам
                    spawnCosmeticParticle(player, p, base.clone().add(x, y, z), 1, 0.01, 0.01, 0.01, 0.0);
                }
                sel.phase += 1.0;
                break;
            }
            case "confetti_rain": {
                // Конфетти сверху вниз — широкий разброс
                double top = param("confetti_rain", "top", 3.5);
                double fallSpeed = param("confetti_rain", "speed", 0.08);
                int count = (int) iparam("confetti_rain", "count", 10);
                double spread = param("confetti_rain", "spread", 3.0);
                for (int i = 0; i < count; i++) {
                    double x = (Math.random() - 0.5) * spread;
                    double z = (Math.random() - 0.5) * spread;
                    double y = top - (sel.phase * fallSpeed % (top + 0.3));
                    spawnCosmeticParticle(player, p, base.clone().add(x, y, z), 1, 0.02, 0.02, 0.02, 0.0);
                }
                sel.phase += 1.0;
                break;
            }
            case "snow_shower": {
                // Мягопадающий «снег»: больше частиц, ниже скорость
                double top = param("snow_shower", "top", 3.0);
                int count = (int) iparam("snow_shower", "count", 12);
                double spread = param("snow_shower", "spread", 3.0);
                double fallSpeed = param("snow_shower", "speed", 0.04);
                for (int i = 0; i < count; i++) {
                    double x = (Math.random() - 0.5) * spread;
                    double z = (Math.random() - 0.5) * spread;
                    double y = top - (sel.phase * fallSpeed % (top + 0.2));
                    spawnCosmeticParticle(player, p, base.clone().add(x, y, z), 1, 0.04, 0.04, 0.04, 0.0);
                }
                sel.phase += 1.0;
                break;
            }

            // ===== Дополнительно =====
            case "ring_pulse": {
                // Расширяющееся кольцо на уровне пояса игрока
                double maxR = param("ring_pulse", "max-radius", 1.8);
                double y = param("ring_pulse", "y", 0.9);
                double speed = param("ring_pulse", "speed", 0.06);
                int points = (int) iparam("ring_pulse", "points", 18);
                double r = (sel.phase * speed % 1.0) * maxR;
                for (int i = 0; i < points; i++) {
                    double a = (Math.PI * 2 / points) * i;
                    spawnCosmeticParticle(player, p, base.clone().add(Math.cos(a) * r, y, Math.sin(a) * r), 1, 0, 0, 0, 0);
                }
                sel.phase += 1.0;
                break;
            }
            case "spiral_up":
            case "spiralup":
            case "flame_column": {
                // Вертикальная спираль, поднимающаяся снизу вверх вокруг игрока
                // Хорошо смотрится с FLAME, SOUL_FIRE_FLAME и подобными
                double height = param("spiral_up", "height", 2.0);            // общая высота колонны
                double speedY = param("spiral_up", "speedY", 0.06);            // скорость подъёма за кадр
                double radius = param("spiral_up", "radius", 0.8);             // радиус спирали
                double turnsPerHeight = param("spiral_up", "turns", 2.5);     // количество витков на высоту

                // Текущая вертикальная позиция (0..height)
                double y = (sel.phase % (height / speedY)) * speedY; // зацикливаемся по высоте
                // Угол для текущей высоты
                double angle = (y / height) * (Math.PI * 2 * turnsPerHeight) + sel.phase * 0.12;

                // Несколько точек вокруг текущего слоя для плотности
                int spokes = (int) iparam("spiral_up", "spokes", 3);
                for (int i = 0; i < spokes; i++) {
                    double a = angle + i * (Math.PI * 2 / Math.max(1, spokes));
                    double x = Math.cos(a) * radius;
                    double z = Math.sin(a) * radius;
                    spawnCosmeticParticle(player, p, base.clone().add(x, 0.2 + y, z), 2, 0.01, 0.01, 0.01, 0);
                }

                // Смещаем фазу: вверх и вращаем чуть быстрее, чтобы получилась плавная спираль
                sel.phase += 1.0; // влияет на y (через модуль) и на вращение
                break;
            }
            case "spiral": {
                // Маленькая спираль, вращающаяся со временем и двигающаяся вместе с игроком
                double r = param("spiral", "radius", 0.9);
                double yAmp = param("spiral", "y-amp", 0.7);
                double yBase = param("spiral", "y-base", 0.7);
                double omega = param("spiral", "omega", 0.2);
                double angle = sel.phase;
                double y = (Math.sin(sel.phase * omega) + 1) * yAmp; // 0..2*yAmp
                double x = Math.cos(angle) * r;
                double z = Math.sin(angle) * r;
                spawnCosmeticParticle(player, p, base.clone().add(x, yBase + y, z), 2, 0, 0, 0, 0);
                sel.phase += param("spiral", "phase-speed", 0.25);
                break;
            }
            case "circle": {
                // Кольцо вокруг игрока, несколько точек за кадр
                double y = param("circle", "y", 0.6);
                double radius = param("circle", "radius", 1.2);
                int points = (int) iparam("circle", "points", 6);
                for (int i = 0; i < points; i++) {
                    double ang = sel.phase + (Math.PI * 2 / Math.max(1, points)) * i;
                    double x = Math.cos(ang) * radius;
                    double z = Math.sin(ang) * radius;
                    spawnCosmeticParticle(player, p, base.clone().add(x, y, z), 1, 0, 0, 0, 0);
                }
                sel.phase += param("circle", "phase-speed", 0.15);
                break;
            }
            case "triple_ring": {
                // Три кольца на разных высотах с разной скоростью вращения
                double r = param("triple_ring", "radius", 1.2);
                double y1 = param("triple_ring", "y1", 0.6);
                double y2 = param("triple_ring", "y2", 1.2);
                double y3 = param("triple_ring", "y3", 1.8);
                int pts = (int) iparam("triple_ring", "points", 10);
                double s1 = param("triple_ring", "speed1", 0.10);
                double s2 = param("triple_ring", "speed2", -0.12);
                double s3 = param("triple_ring", "speed3", 0.16);
                for (int i = 0; i < pts; i++) {
                    double a = (Math.PI * 2 / pts) * i;
                    spawnCosmeticParticle(player, p, base.clone().add(Math.cos(a + sel.phase * s1) * r, y1, Math.sin(a + sel.phase * s1) * r), 1, 0, 0, 0, 0);
                    spawnCosmeticParticle(player, p, base.clone().add(Math.cos(a + sel.phase * s2) * r, y2, Math.sin(a + sel.phase * s2) * r), 1, 0, 0, 0, 0);
                    spawnCosmeticParticle(player, p, base.clone().add(Math.cos(a + sel.phase * s3) * r, y3, Math.sin(a + sel.phase * s3) * r), 1, 0, 0, 0, 0);
                }
                sel.phase += 1.0;
                break;
            }
            case "star": {
                // Звезда с N лучами в горизонтальной плоскости
                int rays = Math.max(3, (int) iparam("star", "rays", 5));
                double rOuter = param("star", "radius", 1.4);
                double rInner = param("star", "inner", rOuter * 0.5);
                double y = param("star", "y", 1.0);
                int seg = Math.max(10, (int) iparam("star", "segments", 40));
                double rot = sel.phase * param("star", "rot", 0.08);
                for (int i = 0; i < rays; i++) {
                    double a1 = rot + (Math.PI * 2 / rays) * i;
                    double a2 = rot + (Math.PI * 2 / rays) * (i + 0.5);
                    for (int s = 0; s <= seg; s++) {
                        double t = s / (double) seg;
                        double ax = a1 + (a2 - a1) * t;
                        double rr = rOuter + (rInner - rOuter) * t;
                        spawnCosmeticParticle(player, p, base.clone().add(Math.cos(ax) * rr, y, Math.sin(ax) * rr), 1, 0, 0, 0, 0);
                    }
                }
                sel.phase += 1.0;
                break;
            }
            case "cube_orbit": {
                // Точки по вершинам вращающегося куба вокруг игрока
                double size = param("cube_orbit", "size", 1.0);
                double y = param("cube_orbit", "y", 1.2);
                double rot = sel.phase * param("cube_orbit", "speed", 0.08);
                double[][] corners = {
                        {-size, -size, -size}, { size, -size, -size}, { size, -size,  size}, { -size, -size,  size},
                        {-size,  size, -size}, { size,  size, -size}, { size,  size,  size}, { -size,  size,  size}
                };
                for (double[] c : corners) {
                    double x = c[0], yy = c[1], z = c[2];
                    // Простое вращение вокруг Y
                    double xr = x * Math.cos(rot) - z * Math.sin(rot);
                    double zr = x * Math.sin(rot) + z * Math.cos(rot);
                    spawnCosmeticParticle(player, p, base.clone().add(xr, y + yy * 0.3, zr), 1, 0, 0, 0, 0);
                }
                sel.phase += 1.0;
                break;
            }
            case "spiral_down": {
                // Спиральная колонна сверху вниз
                double height = param("spiral_down", "height", 2.0);
                double speedY = param("spiral_down", "speedY", 0.06);
                double radius = param("spiral_down", "radius", 0.8);
                double turnsPerHeight = param("spiral_down", "turns", 2.5);
                double y = height - (sel.phase % (height / speedY)) * speedY;
                double angle = (y / height) * (Math.PI * 2 * turnsPerHeight) + sel.phase * 0.12;
                int spokes = Math.max(1, (int) iparam("spiral_down", "spokes", 3));
                for (int i = 0; i < spokes; i++) {
                    double a = angle + i * (Math.PI * 2 / spokes);
                    double x = Math.cos(a) * radius;
                    double z = Math.sin(a) * radius;
                    spawnCosmeticParticle(player, p, base.clone().add(x, 0.2 + y, z), 2, 0.01, 0.01, 0.01, 0);
                }
                sel.phase += 1.0;
                break;
            }
            case "flower": {
                // Розетка (розовый график): r = R * sin(k * theta)
                double R = param("flower", "radius", 1.4);
                int petals = Math.max(2, (int) iparam("flower", "petals", 6));
                double y = param("flower", "y", 0.8);
                int pts = Math.max(24, (int) iparam("flower", "points", 48));
                double rot = sel.phase * param("flower", "rot", 0.08);
                for (int i = 0; i < pts; i++) {
                    double t = (Math.PI * 2 / pts) * i + rot;
                    double r = Math.abs(Math.sin(petals * t)) * R;
                    double x = Math.cos(t) * r;
                    double z = Math.sin(t) * r;
                    spawnCosmeticParticle(player, p, base.clone().add(x, y, z), 1, 0, 0, 0, 0);
                }
                sel.phase += 1.0;
                break;
            }
            case "wings": {
                // Крылья позади игрока по направлению взгляда
                Vector dir = base.getDirection();
                Vector right = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
                Vector back = dir.clone().multiply(-0.6);
                Location center = base.clone().add(back).add(0, 1.1, 0);
                double tMax = param("wings", "height-sections", 1.8);
                double step = param("wings", "step", 0.15);
                double spreadStart = param("wings", "spread-start", 0.9);
                double spreadSlope = param("wings", "spread-slope", 0.45);
                double yScale = param("wings", "y-scale", 0.6);
                for (double t = 0; t <= tMax; t += step) {
                    double spread = spreadStart - t * spreadSlope; // сужение кверху
                    Vector offsetR = right.clone().multiply(spread);
                    Vector offsetL = right.clone().multiply(-spread);
                    spawnCosmeticParticle(player, p, center.clone().add(offsetR.getX(), t * yScale, offsetR.getZ()), 1, 0, 0, 0, 0);
                    spawnCosmeticParticle(player, p, center.clone().add(offsetL.getX(), t * yScale, offsetL.getZ()), 1, 0, 0, 0, 0);
                }
                sel.phase += 0.1;
                break;
            }
            case "hearts": {
                int count = (int) iparam("hearts", "count", 3);
                double rMin = param("hearts", "r-min", 0.7);
                double rMax = param("hearts", "r-max", 1.3);
                double yMin = param("hearts", "y-min", 0.6);
                double yMax = param("hearts", "y-max", 1.6);
                for (int i = 0; i < count; i++) {
                    double ang = Math.random() * Math.PI * 2;
                    double r = rMin + Math.random() * Math.max(0.0, (rMax - rMin));
                    double x = Math.cos(ang) * r;
                    double z = Math.sin(ang) * r;
                    double y = yMin + Math.random() * Math.max(0.0, (yMax - yMin));
                    spawnCosmeticParticle(player, Particle.HEART, base.clone().add(x, y, z), 1, 0, 0, 0, 0);
                }
                sel.phase += 0.2;
                break;
            }
            case "explosion": {
                // Импульс каждые interval кадров
                int interval = (int) iparam("explosion", "interval", 12);
                int burst = (int) iparam("explosion", "burst", 20);
                double extra = param("explosion", "extra", 0.1);
                if ((sel.tick++ % Math.max(1, interval)) == 0) {
                    for (int i = 0; i < burst; i++) {
                        double ang = Math.random() * Math.PI * 2;
                        double r = 0.2 + Math.random() * 1.2;
                        double x = Math.cos(ang) * r;
                        double z = Math.sin(ang) * r;
                        double y = 0.2 + Math.random() * 1.0;
                        spawnCosmeticParticle(player, p, base.clone().add(x, y, z), 1, x, y, z, extra);
                    }
                }
                break;
            }
            default: {
                // По умолчанию — лёгкий след позади игрока
                Vector dir = base.getDirection().normalize();
                Location trail = base.clone().add(dir.multiply(-0.4)).add(0, 0.2, 0);
                spawnCosmeticParticle(player, p, trail, 4, 0.1, 0.1, 0.1, 0.0);
                sel.phase += 0.15;
            }
        }
        } finally {
            currentSel.remove();
        }
    }

    // ===== Новые анимации =====
    // Вставляем доп. кейсы перед default (для компактности отдельным блоком)
    // Обновлённый switch с новыми вариантами
    private void playExtraAnimations(Player player, Selection sel, Particle p, Location base) {
        switch (sel.animation.toLowerCase()) {
            case "orbit": {
                int sats = (int) iparam("orbit", "satellites", 4);
                double r = param("orbit", "radius", 1.2);
                double y = param("orbit", "y", 1.2);
                double speed = param("orbit", "speed", 0.15);
                for (int i = 0; i < sats; i++) {
                    double ang = sel.phase * speed + (Math.PI * 2 / sats) * i;
                    double x = Math.cos(ang) * r;
                    double z = Math.sin(ang) * r;
                    spawnCosmeticParticle(player, p, base.clone().add(x, y, z), 1, 0, 0, 0, 0);
                }
                sel.phase += 1.0;
                break;
            }
            case "halo": {
                int points = (int) iparam("halo", "points", 24);
                double r = param("halo", "radius", 1.2);
                double y = param("halo", "y", 1.8);
                double rot = sel.phase * param("halo", "speed", 0.12);
                for (int i = 0; i < points; i++) {
                    double a = rot + (Math.PI * 2 / points) * i;
                    spawnCosmeticParticle(player, p, base.clone().add(Math.cos(a) * r, y, Math.sin(a) * r), 1, 0, 0, 0, 0);
                }
                sel.phase += 1.0;
                break;
            }
            case "double_helix": {
                double height = param("double_helix", "height", 2.2);
                double radius = param("double_helix", "radius", 0.9);
                double turns = param("double_helix", "turns", 3.0);
                double y = (sel.phase * param("double_helix", "speed", 0.06)) % 1.0 * height;
                double ang = (y / height) * (Math.PI * 2 * turns) + sel.phase * 0.18;
                for (int s = -1; s <= 1; s += 2) {
                    double a = ang + (Math.PI) * (s > 0 ? 0 : 1);
                    double x = Math.cos(a) * radius;
                    double z = Math.sin(a) * radius;
                    spawnCosmeticParticle(player, p, base.clone().add(x, 0.2 + y, z), 2, 0.01, 0.01, 0.01, 0);
                }
                sel.phase += 1.0;
                break;
            }
            case "tornado": {
                double height = param("tornado", "height", 3.0);
                double baseR = param("tornado", "base-radius", 1.2);
                double topR = param("tornado", "top-radius", 0.2);
                double layers = iparam("tornado", "layers", 16);
                double rotSpeed = param("tornado", "speed", 0.25);
                for (int i = 0; i < layers; i++) {
                    double t = i / Math.max(1.0, layers - 1.0);
                    double r = baseR + (topR - baseR) * t;
                    double y = t * height;
                    double a = sel.phase * rotSpeed + t * Math.PI * 4;
                    spawnCosmeticParticle(player, p, base.clone().add(Math.cos(a) * r, y, Math.sin(a) * r), 1, 0, 0, 0, 0);
                }
                sel.phase += 1.0;
                break;
            }
            case "aura": {
                int count = (int) iparam("aura", "count", 8);
                double rad = param("aura", "radius", 0.9);
                double yMin = param("aura", "y-min", 0.2);
                double yMax = param("aura", "y-max", 1.6);
                for (int i = 0; i < count; i++) {
                    double ang = Math.random() * Math.PI * 2;
                    double r = Math.random() * rad;
                    double x = Math.cos(ang) * r;
                    double z = Math.sin(ang) * r;
                    double y = yMin + Math.random() * (yMax - yMin);
                    spawnCosmeticParticle(player, p, base.clone().add(x, y, z), 1, 0.02, 0.02, 0.02, 0.0);
                }
                sel.phase += 1.0;
                break;
            }
            case "halo_rise": {
                double y0 = param("halo_rise", "y-start", 0.6);
                double y1 = param("halo_rise", "y-end", 1.8);
                double prog = (Math.sin(sel.phase * param("halo_rise", "speed", 0.08)) + 1) / 2.0;
                double y = y0 + (y1 - y0) * prog;
                int points = (int) iparam("halo_rise", "points", 20);
                double r = param("halo_rise", "radius", 1.0);
                for (int i = 0; i < points; i++) {
                    double a = (Math.PI * 2 / points) * i + sel.phase * 0.12;
                    spawnCosmeticParticle(player, p, base.clone().add(Math.cos(a) * r, y, Math.sin(a) * r), 1, 0, 0, 0, 0);
                }
                sel.phase += 1.0;
                break;
            }
            case "sphere_pulse": {
                int points = (int) iparam("sphere_pulse", "points", 32);
                double r = param("sphere_pulse", "radius", 1.2) * (0.6 + 0.4 * Math.sin(sel.phase * 0.1));
                for (int i = 0; i < points; i++) {
                    double u = Math.random();
                    double v = Math.random();
                    double theta = 2 * Math.PI * u;
                    double phi = Math.acos(2 * v - 1);
                    double x = r * Math.sin(phi) * Math.cos(theta);
                    double y = r * Math.cos(phi) * 0.6 + 1.0;
                    double z = r * Math.sin(phi) * Math.sin(theta);
                    spawnCosmeticParticle(player, p, base.clone().add(x, y, z), 1, 0, 0, 0, 0);
                }
                sel.phase += 1.0;
                break;
            }
        }
    }

    // ===== particles.yml loader and helpers =====
    private void loadParticlesConfig() {
        try {
            particlesFile = new File(plugin.getDataFolder(), "particles.yml");
            if (!particlesFile.exists()) {
                if (plugin.getResource("particles.yml") != null) {
                    plugin.saveResource("particles.yml", false);
                } else {
                    particlesFile.getParentFile().mkdirs();
                    particlesFile.createNewFile();
                }
            }
            particlesConfig = YamlConfiguration.loadConfiguration(particlesFile);
        } catch (Exception ignored) {}
    }

    private double param(String anim, String key, double def) {
        try {
            Selection cs = currentSel.get();
            if (cs != null && cs.params != null && cs.params.containsKey(key)) {
                String v = cs.params.get(key);
                try { return Double.parseDouble(v); } catch (Exception ignored) {}
            }
            if (particlesConfig == null) return def;
            return particlesConfig.getDouble("particles." + anim + "." + key, def);
        } catch (Exception ignored) { return def; }
    }

    private int iparam(String anim, String key, int def) {
        try {
            Selection cs = currentSel.get();
            if (cs != null && cs.params != null && cs.params.containsKey(key)) {
                String v = cs.params.get(key);
                try { return Integer.parseInt(v); } catch (Exception ignored) {}
            }
            if (particlesConfig == null) return def;
            return particlesConfig.getInt("particles." + anim + "." + key, def);
        } catch (Exception ignored) { return def; }
    }

    private boolean bparam(String anim, String key, boolean def) {
        try {
            Selection cs = currentSel.get();
            if (cs != null && cs.params != null && cs.params.containsKey(key)) {
                String v = cs.params.get(key);
                return Boolean.parseBoolean(v);
            }
            if (particlesConfig == null) return def;
            return particlesConfig.getBoolean("particles." + anim + "." + key, def);
        } catch (Exception ignored) { return def; }
    }

    /**
     * Персонализированная отправка частиц всем зрителям с куллингом переднего конуса для владельца,
     * чтобы в 1P частицы не мешали обзору. В F5 частицы остаются видимыми (куллинг только на близком расстоянии).
     */
    private void spawnCosmeticParticle(Player owner, Particle p, Location loc, int count, double offX, double offY, double offZ, double extra) {
        boolean hideSelf = plugin.getConfig().getBoolean("features.cosmetics.hide-self-particles", false);
        boolean cullEnabled = plugin.getConfig().getBoolean("features.cosmetics.cull.enabled", true);
        // Перемножаем count на общий множитель amount для текущей анимации (если задано)
        try {
            Selection cs = currentSel.get();
            if (cs != null && cs.animation != null) {
                int amount = iparam(cs.animation.toLowerCase(), "amount", 1);
                if (amount > 1) count = Math.max(0, count * amount);
            }
        } catch (Exception ignored) {}
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(owner)) {
                if (hideSelf) continue; // полностью скрываем для владельца
                if (cullEnabled && shouldCullForOwner(owner, loc)) continue; // иначе — геометрический куллинг 1P
            }
            viewer.spawnParticle(p, loc, count, offX, offY, offZ, extra);
        }
    }

    /**
     * Возвращает true, если точка частицы находится в «переднем конусе» взгляда игрока достаточно близко к глазам,
     * то есть её следует скрыть для самого игрока (в 1P).
     */
    private boolean shouldCullForOwner(Player owner, Location pos) {
        try {
            Location eye = owner.getEyeLocation();
            org.bukkit.util.Vector to = pos.toVector().subtract(eye.toVector());
            double dist = to.length();
            if (dist < 1e-6) return true;

            // Нормализованные направления (полные и по горизонтали)
            org.bukkit.util.Vector dir = eye.getDirection().clone();
            org.bukkit.util.Vector dirH = dir.clone(); dirH.setY(0);
            org.bukkit.util.Vector toN = to.clone().normalize();
            org.bukkit.util.Vector toH = to.clone(); toH.setY(0);

            double dot = 0.0;
            if (dir.lengthSquared() > 1e-6) dot = dir.normalize().dot(toN);

            double horizDot = 0.0;
            if (dirH.lengthSquared() > 1e-6 && toH.lengthSquared() > 1e-6) {
                horizDot = dirH.normalize().dot(toH.normalize());
            }

            double yDiff = Math.abs(pos.getY() - eye.getY());

            // Пороговые значения (расширенный конус и вертикальная «лента» вблизи глаз)
            double maxDist = plugin.getConfig().getDouble("features.cosmetics.cull.max-distance", 2.8);
            double minDot = plugin.getConfig().getDouble("features.cosmetics.cull.min-dot", 0.35); // шире конус по вертикали
            double minHorizDot = plugin.getConfig().getDouble("features.cosmetics.cull.min-horiz-dot", 0.20); // шире по горизонтали
            double maxYDiff = plugin.getConfig().getDouble("features.cosmetics.cull.max-y-diff", 1.4);

            boolean near = dist < maxDist;
            boolean inFront = dot > minDot || horizDot > minHorizDot;
            boolean nearEyeBand = yDiff < maxYDiff;

            return near && inFront && nearEyeBand;
        } catch (Exception ignored) {
            return false;
        }
    }

    // ========= Persistence =========
    private void initStorage() {
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) dataDir.mkdirs();
        file = new File(dataDir, "cosmetics.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    private void loadFromDisk() {
        if (cfg == null) return;
        if (cfg.getConfigurationSection("players") == null) return;
        for (String key : cfg.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String hat = cfg.getString("players." + key + ".hat", null);
                if (hat != null && !hat.isEmpty()) hats.put(uuid, hat);
                String anim = cfg.getString("players." + key + ".particle.animation", null);
                String type = cfg.getString("players." + key + ".particle.type", null);
                if (anim != null && type != null) {
                    java.util.Map<String, String> params = null;
                    org.bukkit.configuration.ConfigurationSection psec = cfg.getConfigurationSection("players." + key + ".particle.params");
                    if (psec != null) {
                        params = new java.util.HashMap<>();
                        for (String pk : psec.getKeys(false)) {
                            String pv = String.valueOf(psec.get(pk));
                            if (pv != null) params.put(pk, pv);
                        }
                    }
                    particles.put(uuid, new Selection(anim, type, params));
                }
            } catch (Exception ignored) {}
        }
    }

    private void save(UUID uuid) {
        if (cfg == null) return;
        String base = "players." + uuid.toString();
        // hat
        String hat = hats.get(uuid);
        cfg.set(base + ".hat", hat);
        // particle
        Selection sel = particles.get(uuid);
        if (sel != null) {
            cfg.set(base + ".particle.animation", sel.animation);
            cfg.set(base + ".particle.type", sel.particle);
            // persist params map if present
            if (sel.params != null && !sel.params.isEmpty()) {
                for (java.util.Map.Entry<String, String> e : sel.params.entrySet()) {
                    cfg.set(base + ".particle.params." + e.getKey(), e.getValue());
                }
            } else {
                cfg.set(base + ".particle.params", null);
            }
        } else {
            cfg.set(base + ".particle", null);
        }
        try { cfg.save(file); } catch (IOException ignored) {}
    }

    private void saveAll() {
        if (cfg == null) return;
        cfg.set("players", null);
        for (UUID u : hats.keySet()) save(u);
        for (UUID u : particles.keySet()) save(u);
        try { cfg.save(file); } catch (IOException ignored) {}
    }

    /** Apply saved cosmetics to player when they join */
    public void applyOnJoin(Player player) {
        UUID uuid = player.getUniqueId();
        // запросим актуальное состояние с прокси (придёт COSMETICS_STATE)
        plugin.requestCosmetics(player, uuid);
        // Локально отрисуем сохранённое состояние (без отправки на прокси)
        String hat = hats.get(uuid);
        Selection sel = particles.get(uuid);
        String anim = sel == null ? null : sel.animation;
        String part = sel == null ? null : sel.particle;
        applyCosmeticsFromProxy(uuid, hat, part, anim);
    }

    /** Применить состояние, полученное с прокси, локально без обратной отправки */
    public void applyCosmeticsFromProxy(UUID uuid, String hatId, String particleName, String animation) {
        // Проверка ограничений сервера
        boolean allowed = isCosmeticsAllowedHere();

        // применяем шляпу
        Player player = Bukkit.getPlayer(uuid);
        if (!allowed || hatId == null || hatId.isEmpty()) {
            // очистить локально
            if (player != null) {
                ItemStack prev = previousHelmets.remove(uuid);
                player.getInventory().setHelmet(prev);
                player.updateInventory();
            }
            if (!allowed) {
                // не сохраняем локально, чтобы при запрете ничего не отображалось
                hats.remove(uuid);
            } else {
                hats.remove(uuid);
            }
        } else {
            // локальная установка без эха
            if (allowed && player != null) {
                // сохранить предыдущий
                previousHelmets.putIfAbsent(uuid, safeCopy(player.getInventory().getHelmet()));
                ItemStack hat;
                switch (hatId.toLowerCase()) {
                    case "govnyashka":
                    case "poop":
                        hat = new ItemStack(Material.LEATHER_HELMET);
                        LeatherArmorMeta meta = (LeatherArmorMeta) hat.getItemMeta();
                        if (meta != null) {
                            meta.setColor(Color.fromRGB(0x6b4b1b));
                            meta.setDisplayName(com.loma.plugin.utils.MessageUtils.color("&6Говняшка"));
                            hat.setItemMeta(meta);
                        }
                        break;
                    default:
                        hat = new ItemStack(Material.CARVED_PUMPKIN);
                        break;
                }
                try {
                    ItemMeta im = hat.getItemMeta();
                    if (im != null) {
                        im.getPersistentDataContainer().set(new NamespacedKey(plugin, "hat"), PersistentDataType.STRING, "1");
                        hat.setItemMeta(im);
                    }
                } catch (Exception ignored) {}
                player.getInventory().setHelmet(hat);
                player.updateInventory();
            }
            if (allowed) hats.put(uuid, hatId);
        }

        // применяем частицы
        if (!allowed) {
            particles.remove(uuid);
        } else {
            boolean hasIncoming = particleName != null && !particleName.isEmpty() && animation != null && !animation.isEmpty();
            if (hasIncoming) {
                Selection old = particles.get(uuid);
                java.util.Map<String, String> keep = old != null ? old.params : null;
                particles.put(uuid, new Selection(animation, particleName, keep));
            } // иначе: оставляем локальный выбор без изменений (не затираем пустым состоянием прокси)
        }
        save(uuid);
    }

    private boolean isCosmeticsAllowedHere() {
        try {
            String server = plugin.getConfig().getString("bungeecord.server-name", "").toLowerCase();
            List<String> whitelist = plugin.getConfig().getStringList("cosmetics.restrictions.whitelist");
            List<String> blacklist = plugin.getConfig().getStringList("cosmetics.restrictions.blacklist");
            boolean inWhitelist = whitelist.stream().anyMatch(s -> s != null && s.equalsIgnoreCase(server));
            boolean inBlacklist = blacklist.stream().anyMatch(s -> s != null && s.equalsIgnoreCase(server));
            if (!whitelist.isEmpty() && !inWhitelist) return false;
            if (inBlacklist) return false;
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    private static class Selection {
        final String animation;
        final String particle;
        final java.util.Map<String, String> params; // пер-селекционные параметры
        double phase = 0.0;
        int tick = 0;
        Selection(String animation, String particle) {
            this(animation, particle, null);
        }
        Selection(String animation, String particle, java.util.Map<String, String> params) {
            this.animation = animation;
            this.particle = particle;
            this.params = params == null ? java.util.Collections.emptyMap() : new java.util.HashMap<>(params);
        }
    }
}
