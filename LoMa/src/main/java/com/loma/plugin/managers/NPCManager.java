package com.loma.plugin.managers;

import com.loma.plugin.LoMa;
import com.loma.plugin.npc.CustomNPC;
import com.loma.plugin.npc.NPCAction;
import com.loma.plugin.utils.MessageUtils;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.SkinTrait;
import net.citizensnpcs.trait.LookClose;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import org.bukkit.scheduler.BukkitTask;

public class NPCManager {

    private final LoMa plugin;
    private final Map<Integer, CustomNPC> npcs;
    private final Map<String, NPCAction> actions;
    private BukkitTask lookAtPlayerTask;

    public NPCManager(LoMa plugin) {
        this.plugin = plugin;
        this.npcs = new HashMap<>();
        this.actions = new HashMap<>();

        // Регистрация стандартных действий
        registerDefaultActions();
        
        // Запуск задачи для поворота NPC к игрокам
        startLookAtPlayerTask();
    }

    private void registerDefaultActions() {
        // Действие: выполнить команду
        registerAction("command", (player, npc, args) -> {
            if (args.length > 0) {
                String command = String.join(" ", args)
                        .replace("{player}", player.getName())
                        .replace("{uuid}", player.getUniqueId().toString());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        });

        // Действие: отправить на сервер (BungeeCord)
        registerAction("server", (player, npc, args) -> {
            if (args.length > 0) {
                connectToServer(player, args[0]);
            }
        });

        // Действие: открыть меню
        registerAction("menu", (player, npc, args) -> {
            if (args.length > 0) {
                plugin.getMenuManager().openMenu(player, args[0]);
            }
        });

        // Действие: отправить сообщение
        registerAction("message", (player, npc, args) -> {
            if (args.length > 0) {
                String message = String.join(" ", args)
                        .replace("{player}", player.getName());
                MessageUtils.send(player, message);
            }
        });

        // Действие: телепортация
        registerAction("teleport", (player, npc, args) -> {
            if (args.length >= 3) {
                try {
                    double x = Double.parseDouble(args[0]);
                    double y = Double.parseDouble(args[1]);
                    double z = Double.parseDouble(args[2]);
                    String world = args.length > 3 ? args[3] : player.getWorld().getName();

                    Location loc = new Location(Bukkit.getWorld(world), x, y, z);
                    if (args.length >= 6) {
                        loc.setYaw(Float.parseFloat(args[4]));
                        loc.setPitch(Float.parseFloat(args[5]));
                    }

                    player.teleport(loc);
                    MessageUtils.send(player, plugin.getMessage("npc.teleported"));
                } catch (NumberFormatException e) {
                    MessageUtils.send(player, plugin.getMessage("errors.invalid-coordinates"));
                }
            }
        });

        // Действие: дать предмет
        registerAction("give", (player, npc, args) -> {
            if (args.length >= 1) {
                try {
                    Material material = Material.valueOf(args[0].toUpperCase());
                    int amount = args.length > 1 ? Integer.parseInt(args[1]) : 1;

                    ItemStack item = new ItemStack(material, amount);
                    player.getInventory().addItem(item);
                    MessageUtils.send(player, plugin.getMessage("npc.item-given")
                            .replace("{item}", material.name())
                            .replace("{amount}", String.valueOf(amount)));
                } catch (Exception e) {
                    MessageUtils.send(player, plugin.getMessage("errors.invalid-item-or-amount"));
                }
            }
        });

        // Действие: воспроизвести звук
        registerAction("sound", (player, npc, args) -> {
            if (args.length >= 1) {
                try {
                    String sound = args[0];
                    float volume = args.length > 1 ? Float.parseFloat(args[1]) : 1.0f;
                    float pitch = args.length > 2 ? Float.parseFloat(args[2]) : 1.0f;

                    player.playSound(player.getLocation(), sound, volume, pitch);
                } catch (Exception e) {
                    MessageUtils.send(player, plugin.getMessage("errors.invalid-sound"));
                }
            }
        });

        // Действие: эффект частиц
        registerAction("particle", (player, npc, args) -> {
            if (args.length >= 1) {
                plugin.getAnimationManager().playParticleEffect(player, args[0]);
            }
        });
    }

    public void registerAction(String name, NPCAction action) {
        actions.put(name.toLowerCase(), action);
    }

    public CustomNPC createNPC(String name, Location location, String skinName) {
        if (!isCitizensEnabled()) {
            MessageUtils.sendConsole("&cCitizens API is not available!");
            return null;
        }

        // Создание NPC через Citizens API
        NPC citizensNPC = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, MessageUtils.color(name));
        citizensNPC.spawn(location);

        // Установка скина
        if (skinName != null && !skinName.isEmpty()) {
            SkinTrait skinTrait = citizensNPC.getOrAddTrait(SkinTrait.class);
            skinTrait.setSkinName(skinName);
        }

        // Создание CustomNPC
        CustomNPC customNPC = new CustomNPC(citizensNPC.getId(), name, location, skinName);
        npcs.put(citizensNPC.getId(), customNPC);

        // Сохранение
        saveNPC(customNPC);

        return customNPC;
    }

    public void removeNPC(int id) {
        // Сначала удаляем из нашей карты
        CustomNPC customNPC = npcs.remove(id);
        
        if (isCitizensEnabled()) {
            NPC citizensNPC = CitizensAPI.getNPCRegistry().getById(id);
            if (citizensNPC != null) {
                // Сначала деспавним сущность
                if (citizensNPC.isSpawned()) {
                    citizensNPC.despawn();
                }
                // Затем удаляем NPC из реестра
                citizensNPC.destroy();
                
                // Дополнительная проверка - удаляем из реестра Citizens
                CitizensAPI.getNPCRegistry().deregister(citizensNPC);
            }
        }

        // Удаление файла NPC
        File f = getNpcFile(id);
        if (f.exists()) {
            if (!f.delete()) {
                plugin.getLogger().warning("Failed to delete NPC file: " + f.getName());
            }
        }
        
        // Логирование для отладки
        if (customNPC != null) {
            plugin.getLogger().info("Successfully removed NPC: " + customNPC.getName() + " (ID: " + id + ")");
        }
    }

    public void removeAllNPCs() {
        if (isCitizensEnabled()) {
            npcs.keySet().forEach(id -> {
                NPC citizensNPC = CitizensAPI.getNPCRegistry().getById(id);
                if (citizensNPC != null) {
                    if (citizensNPC.isSpawned()) {
                        citizensNPC.despawn();
                    }
                    citizensNPC.destroy();
                    // Важно: также удаляем из реестра, чтобы Citizens не восстанавливал дубликаты на рестарте
                    CitizensAPI.getNPCRegistry().deregister(citizensNPC);
                }
            });
        }
        npcs.clear();
        
        // Остановка задачи поворота
        if (lookAtPlayerTask != null) {
            lookAtPlayerTask.cancel();
            lookAtPlayerTask = null;
        }
    }

    public void handleNPCInteract(Player player, int npcId) {
        CustomNPC customNPC = npcs.get(npcId);
        if (customNPC == null) return;

        // Проверка задержки
        if (!customNPC.canInteract(player)) {
            long cooldown = customNPC.getCooldownRemaining(player);
            MessageUtils.send(player, plugin.getMessage("npc.cooldown")
                    .replace("{time}", String.valueOf(cooldown / 1000)));
            return;
        }

        // Проверка прав
        if (customNPC.getPermission() != null && !player.hasPermission(customNPC.getPermission())) {
            MessageUtils.send(player, plugin.getMessage("npc.no-permission"));
            return;
        }

        // Выполнение действий
        for (Map.Entry<String, String[]> entry : customNPC.getActions().entrySet()) {
            NPCAction action = actions.get(entry.getKey().toLowerCase());
            if (action != null) {
                action.execute(player, customNPC, entry.getValue());
            }
        }

        // Обновление времени последнего взаимодействия
        customNPC.setLastInteraction(player);
    }

    public void loadNPCs() {
        // Миграция со старого формата npcs.yml, если каталог пуст
        File dir = getNpcDir();
        if (!dir.exists()) dir.mkdirs();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null || files.length == 0) {
            migrateFromLegacyYaml();
            files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".yml"));
        }

        if (files == null) return;

        for (File file : files) {
            try {
                FileConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
                int id = cfg.getInt("id");
                String name = cfg.getString("name");
                String worldName = cfg.getString("location.world");
                double x = cfg.getDouble("location.x");
                double y = cfg.getDouble("location.y");
                double z = cfg.getDouble("location.z");
                float yaw = (float) cfg.getDouble("location.yaw");
                float pitch = (float) cfg.getDouble("location.pitch");
                Location location = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);

                CustomNPC customNPC = new CustomNPC(id, name, location, cfg.getString("skin"));

                // Действия
                ConfigurationSection actionsSection = cfg.getConfigurationSection("actions");
                if (actionsSection != null) {
                    for (String actionKey : actionsSection.getKeys(false)) {
                        String actionValue = actionsSection.getString(actionKey, "");
                        String[] args = actionValue.isEmpty() ? new String[0] : actionValue.split(" ");
                        customNPC.addAction(actionKey, args);
                    }
                }

                customNPC.setPermission(cfg.getString("permission"));
                customNPC.setCooldown(cfg.getInt("cooldown", 0));
                customNPC.setGlowing(cfg.getBoolean("glowing", false));
                customNPC.setLookAtPlayer(cfg.getBoolean("look-at-player", true));

                npcs.put(id, customNPC);

                // Спавн через Citizens
                if (isCitizensEnabled()) {
                    NPC citizensNPC = CitizensAPI.getNPCRegistry().getById(id);
                    if (citizensNPC == null) {
                        // Попробуем найти уже существующего NPC по имени и близкой позиции, чтобы не создавать дубль
                        NPC matched = null;
                        for (NPC n : CitizensAPI.getNPCRegistry()) {
                            try {
                                String nName = n.getName();
                                if (nName != null && MessageUtils.color(nName).equals(MessageUtils.color(name))) {
                                    org.bukkit.Location stored = n.getStoredLocation();
                                    if (stored != null && stored.getWorld() != null && location.getWorld() != null
                                            && stored.getWorld().getName().equals(location.getWorld().getName())
                                            && stored.distanceSquared(location) < 0.25) { // в пределах 0.5 блока
                                        matched = n; break;
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        if (matched != null) {
                            citizensNPC = matched;
                            // привязываем наш CustomNPC к найденному id
                            customNPC = new CustomNPC(citizensNPC.getId(), name, location, cfg.getString("skin"));
                            npcs.put(citizensNPC.getId(), customNPC);
                        } else {
                            // Citizens-NPC отсутствует и совпадений нет — считаем, что его удалили в Citizens.
                            // Удаляем запись LoMa и пропускаем создание нового, чтобы не возрождать вручную удалённые NPC.
                            npcs.remove(id);
                            File stale = getNpcFile(id);
                            if (stale.exists()) try { stale.delete(); } catch (Exception ignored) {}
                            plugin.getLogger().info("NPC id=" + id + " ('" + name + "') отсутствует в Citizens — запись LoMa удалена, не создаём новый.");
                            continue; // к следующему файлу
                        }
                    } else if (!citizensNPC.isSpawned()) {
                        citizensNPC.spawn(location);
                    }
                    
                    if (customNPC.getSkinName() != null) {
                        SkinTrait skinTrait = citizensNPC.getOrAddTrait(SkinTrait.class);
                        skinTrait.setSkinName(customNPC.getSkinName());
                    }
                    
                    citizensNPC.data().set(NPC.Metadata.NAMEPLATE_VISIBLE, true);
                    citizensNPC.data().set(NPC.Metadata.COLLIDABLE, false);
                    
                    // Применяем настройки свечения
                    if (customNPC.isGlowing()) {
                        citizensNPC.data().set(NPC.Metadata.GLOWING, true);
                    }

                    // Точный взгляд на игрока через LookClose (вместо нашего таймера)
                    if (customNPC.isLookAtPlayer()) {
                        LookClose lc = citizensNPC.getOrAddTrait(LookClose.class);
                        lc.setRange(20);
                        lc.setRandomLook(false);
                        lc.setRealisticLooking(true);
                        // Наличие трейта достаточно, чтобы активировать слежение в текущей версии Citizens
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to load NPC from file " + file.getName() + ": " + ex.getMessage());
            }
        }

        MessageUtils.sendConsole("&aLoaded " + npcs.size() + " NPCs!");
    }

    public void saveNPCs() {
        for (CustomNPC npc : npcs.values()) {
            saveNPC(npc);
        }
    }

    private void saveNPC(CustomNPC npc) {
        try {
            File file = getNpcFile(npc.getId(), npc.getName());
            org.bukkit.configuration.file.YamlConfiguration cfg = new org.bukkit.configuration.file.YamlConfiguration();
            cfg.set("id", npc.getId());
            cfg.set("name", npc.getName());
            cfg.set("location.world", npc.getLocation().getWorld().getName());
            cfg.set("location.x", npc.getLocation().getX());
            cfg.set("location.y", npc.getLocation().getY());
            cfg.set("location.z", npc.getLocation().getZ());
            cfg.set("location.yaw", npc.getLocation().getYaw());
            cfg.set("location.pitch", npc.getLocation().getPitch());
            cfg.set("skin", npc.getSkinName());
            cfg.set("permission", npc.getPermission());
            cfg.set("cooldown", npc.getCooldown());
            cfg.set("glowing", npc.isGlowing());
            cfg.set("look-at-player", npc.isLookAtPlayer());
            for (Map.Entry<String, String[]> entry : npc.getActions().entrySet()) {
                cfg.set("actions." + entry.getKey(), String.join(" ", entry.getValue()));
            }
            cfg.save(file);
        } catch (Exception ex) {
            plugin.getLogger().severe("Failed to save NPC file: " + ex.getMessage());
        }
    }

    private File getNpcDir() {
        File dir = new File(plugin.getDataFolder(), "npcs");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private File getNpcFile(int id) {
        File dir = getNpcDir();
        // fallback search by id_*.yml
        File[] list = dir.listFiles((d, name) -> name.startsWith(id + "_") && name.endsWith(".yml"));
        if (list != null && list.length > 0) return list[0];
        return new File(dir, id + ".yml");
    }

    private File getNpcFile(int id, String name) {
        String safe = name == null ? String.valueOf(id) : name.replaceAll("[^a-zA-Z0-9-_]", "_");
        return new File(getNpcDir(), id + "_" + safe + ".yml");
    }

    private void migrateFromLegacyYaml() {
        File legacy = new File(plugin.getDataFolder(), "npcs.yml");
        if (!legacy.exists()) return;
        try {
            FileConfiguration config = plugin.getNPCsConfig();
            ConfigurationSection npcsSection = config.getConfigurationSection("npcs");
            if (npcsSection == null) return;
            for (String key : npcsSection.getKeys(false)) {
                try {
                    int id = Integer.parseInt(key);
                    ConfigurationSection npcSection = npcsSection.getConfigurationSection(key);
                    String name = npcSection.getString("name", "npc" + id);
                    String worldName = npcSection.getString("location.world");
                    double x = npcSection.getDouble("location.x");
                    double y = npcSection.getDouble("location.y");
                    double z = npcSection.getDouble("location.z");
                    float yaw = (float) npcSection.getDouble("location.yaw");
                    float pitch = (float) npcSection.getDouble("location.pitch");

                    org.bukkit.configuration.file.YamlConfiguration cfg = new org.bukkit.configuration.file.YamlConfiguration();
                    cfg.set("id", id);
                    cfg.set("name", name);
                    cfg.set("location.world", worldName);
                    cfg.set("location.x", x);
                    cfg.set("location.y", y);
                    cfg.set("location.z", z);
                    cfg.set("location.yaw", yaw);
                    cfg.set("location.pitch", pitch);
                    cfg.set("skin", npcSection.getString("skin"));
                    cfg.set("permission", npcSection.getString("permission"));
                    cfg.set("cooldown", npcSection.getInt("cooldown", 0));
                    cfg.set("glowing", npcSection.getBoolean("glowing", false));
                    cfg.set("look-at-player", npcSection.getBoolean("look-at-player", true));

                    ConfigurationSection actionsSection = npcSection.getConfigurationSection("actions");
                    if (actionsSection != null) {
                        for (String actionKey : actionsSection.getKeys(false)) {
                            String actionValue = actionsSection.getString(actionKey, "");
                            cfg.set("actions." + actionKey, actionValue);
                        }
                    }

                    cfg.save(getNpcFile(id, name));
                } catch (Exception inner) {
                    plugin.getLogger().warning("Failed to migrate NPC " + key + ": " + inner.getMessage());
                }
            }
            plugin.getLogger().info("Migrated legacy npcs.yml to individual files in npcs/.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to migrate legacy npcs.yml: " + e.getMessage());
        }
    }

    private void connectToServer(Player player, String server) {
        String current = plugin.getConfig().getString("bungeecord.server-name", "");
        if (current.equalsIgnoreCase(server)) {
            MessageUtils.send(player, plugin.getMessage("server.already-here"));
            return;
        }
        // Единая проверка доступа: авторизация, закрытие сервера, allowed_servers
        if (plugin.getMenuManager() != null && !plugin.getMenuManager().canJoinServer(player, server)) {
            return;
        }
        if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, "BungeeCord")) {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);

        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        MessageUtils.send(player, plugin.getMessage("server.connecting").replace("{server}", server));
    }

    private boolean isCitizensEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("Citizens");
    }

    public void reload() {
        removeAllNPCs();
        npcs.clear();
        loadNPCs();
        
        // Перезапуск задачи поворота
        if (lookAtPlayerTask != null) {
            lookAtPlayerTask.cancel();
        }
        startLookAtPlayerTask();
    }

    public CustomNPC getNPC(int id) {
        return npcs.get(id);
    }

    /**
     * Получить NPC по идентификатору: числовой ID или имя (без учета регистра)
     */
    public CustomNPC getNPCByIdentifier(String idOrName) {
        if (idOrName == null || idOrName.isEmpty()) return null;
        try {
            int id = Integer.parseInt(idOrName);
            return getNPC(id);
        } catch (NumberFormatException ignored) {
        }
        String needle = idOrName.toLowerCase();
        for (CustomNPC npc : npcs.values()) {
            if (npc.getName() != null && npc.getName().toLowerCase().equals(needle)) {
                return npc;
            }
        }
        return null;
    }

    public Collection<CustomNPC> getAllNPCs() {
        return npcs.values();
    }

    public Map<String, NPCAction> getActions() {
        return actions;
    }
    
    /**
     * Обновление настроек NPC в Citizens
     */
    public void updateNPCSettings(int npcId) {
        if (!isCitizensEnabled()) return;
        
        CustomNPC customNPC = npcs.get(npcId);
        if (customNPC == null) return;
        
        NPC citizensNPC = CitizensAPI.getNPCRegistry().getById(npcId);
        if (citizensNPC == null) return;
        
        // Обновление имени
        citizensNPC.setName(MessageUtils.color(customNPC.getName()));
        
        // Обновление скина
        if (customNPC.getSkinName() != null && !customNPC.getSkinName().isEmpty()) {
            SkinTrait skinTrait = citizensNPC.getOrAddTrait(SkinTrait.class);
            skinTrait.setSkinName(customNPC.getSkinName());
        }
        
        // Обновление свечения
        citizensNPC.data().set(NPC.Metadata.GLOWING, customNPC.isGlowing());
        
        // Если NPC заспавнен, перезапускаем его для применения изменений
        if (citizensNPC.isSpawned()) {
            Location loc = citizensNPC.getStoredLocation();
            citizensNPC.despawn();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                citizensNPC.spawn(loc);
            }, 2L);
        }
    }
    
    /**
     * Запуск задачи для поворота NPC к ближайшим игрокам
     */
    private void startLookAtPlayerTask() {
        if (!isCitizensEnabled()) return;
        
        lookAtPlayerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (CustomNPC customNPC : npcs.values()) {
                if (!customNPC.isLookAtPlayer()) continue;
                
                NPC citizensNPC = CitizensAPI.getNPCRegistry().getById(customNPC.getId());
                if (citizensNPC == null || !citizensNPC.isSpawned()) continue;
                
                org.bukkit.entity.Entity npcEntity = citizensNPC.getEntity();
                if (npcEntity == null) continue;
                
                // Находим ближайшего игрока в радиусе ~20 блоков
                Player nearestPlayer = null;
                double nearestDistance = 400.0; // 20^2
                
                for (Player player : npcEntity.getWorld().getPlayers()) {
                    if (player.getLocation().distanceSquared(npcEntity.getLocation()) < nearestDistance) {
                        nearestDistance = player.getLocation().distanceSquared(npcEntity.getLocation());
                        nearestPlayer = player;
                    }
                }
                
                // Поворачиваем NPC к игроку
                if (nearestPlayer != null) {
                    Location playerLoc = nearestPlayer.getEyeLocation();

                    Location npcEyeLoc;
                    if (npcEntity instanceof LivingEntity livingEntity) {
                        npcEyeLoc = livingEntity.getEyeLocation();
                    } else {
                        npcEyeLoc = npcEntity.getLocation().add(0, npcEntity.getHeight() * 0.5, 0);
                    }

                    // Вычисляем направление
                    double dx = playerLoc.getX() - npcEyeLoc.getX();
                    double dy = playerLoc.getY() - npcEyeLoc.getY();
                    double dz = playerLoc.getZ() - npcEyeLoc.getZ();

                    // Вычисляем yaw и pitch
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
                    float pitch = (float) -(Math.atan2(dy, distance) * 180.0 / Math.PI);

                    Location npcLoc = npcEntity.getLocation();
                    npcLoc.setYaw(yaw);
                    npcLoc.setPitch(pitch);
                    citizensNPC.faceLocation(playerLoc);
                }
            }
        }, 0L, 5L); // Обновление каждые 5 тиков (0.25 секунды)
    }

    /** Немедленно повернуть указанного NPC к ближайшему игроку (если есть) */
    public void forceLookAtNearest(int npcId) {
        CustomNPC customNPC = npcs.get(npcId);
        if (customNPC == null) return;
        faceNearestPlayerInternal(customNPC);
    }

    /** Внутренняя реализация быстрого поворота */
    private void faceNearestPlayerInternal(CustomNPC customNPC) {
        if (!isCitizensEnabled() || customNPC == null) return;
        NPC citizensNPC = CitizensAPI.getNPCRegistry().getById(customNPC.getId());
        if (citizensNPC == null || !citizensNPC.isSpawned()) return;
        org.bukkit.entity.Entity npcEntity = citizensNPC.getEntity();
        if (npcEntity == null) return;
        Player nearest = null;
        double best = 400.0;
        for (Player p : npcEntity.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(npcEntity.getLocation());
            if (d < best) { best = d; nearest = p; }
        }
        if (nearest != null) citizensNPC.faceLocation(nearest.getEyeLocation());
    }
}