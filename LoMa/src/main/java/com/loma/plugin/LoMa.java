package com.loma.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import com.loma.plugin.commands.*;
import com.loma.plugin.listeners.*;
import com.loma.plugin.managers.*;
import com.loma.plugin.achievements.AchievementsManager;
import com.loma.plugin.utils.MessageUtils;
import com.loma.plugin.scoreboard.ScoreboardManager;
import com.loma.plugin.playtime.PlaytimeService;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;

public class LoMa extends JavaPlugin implements PluginMessageListener {

    private static LoMa instance;
    private NPCManager npcManager;
    private SpawnManager spawnManager;
    private ScoreboardManager scoreboardManager;
    private AnimationManager animationManager;
    private MenuManager menuManager;
    private PreferencesManager preferencesManager;
    private CosmeticsManager cosmeticsManager;
    private FileConfiguration messagesConfig;
    private FileConfiguration npcsConfig;
    private PlaytimeService playtimeService;
    private AchievementsManager achievementsManager;
    private volatile int networkOnline = -1;

    // Кэш данных игроков из Velocity
    private final Map<UUID, PlayerStatsCache> statsCache = new HashMap<>();
    // Кэш расширенных профилей игроков
    private final Map<UUID, PlayerProfileCache> profileCache = new HashMap<>();
    // Кэш наигранного времени по серверам
    private final Map<UUID, Map<String, Long>> perServerPlaytime = new HashMap<>();
    
    // Система авторизации
    private final Map<UUID, Boolean> authorizedPlayers = new HashMap<>();
    private final Map<UUID, java.util.List<String>> playerAllowedServers = new HashMap<>();
    // Последний собеседник в ЛС (для /r)
    private final Map<UUID, String> lastPmPartner = new HashMap<>();
    // Ожидающие ответы на проверку аккаунта: username(lower) -> callback(exists, autoLogin)
    private final Map<String, java.util.function.BiConsumer<Boolean, Boolean>> pendingAccountChecks = new HashMap<>();
    private FileConfiguration serversConfig;

    @Override
    public void onEnable() {
        instance = this;

        // Создание конфигурационных файлов
        saveDefaultConfig();
        loadConfigs();

        // Установка глобального префикса для сообщений игрокам
        try {
            String prefix = getMessagesConfig() != null ? getMessagesConfig().getString("prefix", "") : "";
            com.loma.plugin.utils.MessageUtils.setPrefix(com.loma.plugin.utils.MessageUtils.color(prefix));
        } catch (Exception ignored) {}

        // Инициализация менеджеров
        initializeManagers();

        // Регистрация команд
        registerCommands();

        // Регистрация слушателей событий
        registerListeners();

        // Загрузка данных
        loadData();

        // Регистрация Plugin Messaging
        registerPluginMessaging();

        // Метрики bStats (опционально)
        int pluginId = 20000; // Замените на ваш ID
        // new Metrics(this, pluginId);

        MessageUtils.sendConsole(getMessage("startup.header"));
        MessageUtils.sendConsole(getMessage("startup.name"));
        MessageUtils.sendConsole(getMessage("startup.version").replace("{version}", getDescription().getVersion()));
        MessageUtils.sendConsole(getMessage("startup.author"));
        MessageUtils.sendConsole(getMessage("startup.status"));
        MessageUtils.sendConsole(getMessage("startup.footer"));
    }

    /** Запросить косметику игрока (Velocity) */
    public void requestCosmetics(org.bukkit.entity.Player requester, UUID targetUUID) {
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("GET_COSMETICS");
            out.writeUTF(targetUUID.toString());
            requester.sendPluginMessage(this, "loma:stats", out.toByteArray());
        } catch (Exception ignored) {}
    }

    /** Установить косметику игрока на прокси (Velocity) */
    public void setCosmeticsOnProxy(UUID uuid, String hat, String particle, String animation) {
        try {
            org.bukkit.entity.Player any = null;
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) { any = p; break; }
            if (any == null) return;
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("SET_COSMETICS");
            out.writeUTF(uuid.toString());
            out.writeUTF(hat == null ? "" : hat);
            out.writeUTF(particle == null ? "" : particle);
            out.writeUTF(animation == null ? "" : animation);
            any.sendPluginMessage(this, "loma:stats", out.toByteArray());
        } catch (Exception ignored) {}
    }

    /** Расширенный профиль, кэшируемый на Spigot */
    public static class PlayerProfileCache {
        public final UUID uuid;
        public final String username;
        public final long playtimeMinutes;
        public final String rank;
        public final int achievements;
        public final boolean online;
        public final String currentServer;
        public final long lastSeen;
        public final long firstJoin;
        public final String lastServer;
        public final boolean share;

        public PlayerProfileCache(UUID uuid, String username, long minutes, String rank, int achievements,
                                  boolean online, String currentServer, long lastSeen, long firstJoin, String lastServer, boolean share) {
            this.uuid = uuid;
            this.username = username;
            this.playtimeMinutes = minutes;
            this.rank = rank;
            this.achievements = achievements;
            this.online = online;
            this.currentServer = currentServer;
            this.lastSeen = lastSeen;
            this.firstJoin = firstJoin;
            this.lastServer = lastServer;
            this.share = share;
        }

        public String getFormattedPlaytime() {
            long hours = playtimeMinutes / 60;
            long mins = playtimeMinutes % 60;
            return hours + "ч " + mins + "м";
        }
    }

    /** Получить кэш профиля */
    public PlayerProfileCache getPlayerProfile(UUID uuid) { return profileCache.get(uuid); }

    @Override
    public void onDisable() {
        // Сохранение данных
        saveData();

        // Сохранение NPC перед очисткой
        if (npcManager != null) {
            npcManager.saveNPCs();
        }

        // Сохранение Citizens (если плагин включен)
        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "citizens save");
            } catch (Exception e) {
                getLogger().warning("Failed to save Citizens data: " + e.getMessage());
            }
        }

        // Очистка NPC
        if (npcManager != null) {
            npcManager.removeAllNPCs();
        }

        // Очистка скорбордов
        if (scoreboardManager != null) {
            scoreboardManager.removeAll();
        }

        // Остановка сервиса игрового времени
        if (playtimeService != null) {
            try { playtimeService.shutdown(); } catch (Exception ignored) {}
        }

        // Остановка менеджера косметики
        if (cosmeticsManager != null) {
            try { cosmeticsManager.shutdown(); } catch (Exception ignored) {}
        }

        // Сохранение достижений
        if (achievementsManager != null) {
            try { achievementsManager.saveData(); } catch (Exception ignored) {}
        }

        MessageUtils.sendConsole(getMessage("shutdown.disabled"));
    }

    private void initializeManagers() {
        this.npcManager = new NPCManager(this);
        this.spawnManager = new SpawnManager(this);
        this.scoreboardManager = new ScoreboardManager(this);
        this.animationManager = new AnimationManager(this);
        this.preferencesManager = new PreferencesManager(this);
        this.cosmeticsManager = new CosmeticsManager(this);
        this.menuManager = new MenuManager(this);
        this.achievementsManager = new AchievementsManager(this);
        // Инициализация сервиса игрового времени (если включено)
        if (getConfig().getBoolean("database.enabled", false)) {
            this.playtimeService = new PlaytimeService(this);
            try {
                this.playtimeService.init();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to initialize PlaytimeService", e);
            }
        }
    }

    private void registerCommands() {
        // Основная команда
        getCommand("loma").setExecutor(new LoMaCommand(this));
        getCommand("loma").setTabCompleter(new LoMaTabCompleter());

        // Команды для NPC
        NPCCommand npcCommand = new NPCCommand(this);
        getCommand("lmnpc").setExecutor(npcCommand);
        getCommand("lmnpc").setTabCompleter(npcCommand);

        // Команда спавна
        getCommand("spawn").setExecutor(new SpawnCommand(this));
        getCommand("setspawn").setExecutor(new SetSpawnCommand(this));

        // Команды для серверов
        getCommand("server").setExecutor(new ServerCommand(this));

        // Команда лобби
        getCommand("lobby").setExecutor(new LobbyCommand(this));
        // Команда профиля
        getCommand("profile").setExecutor(new ProfileCommand(this));
        try { getCommand("profile").setTabCompleter(new com.loma.plugin.commands.ProfileTabCompleter()); } catch (Exception ignored) {}

        // Приватность локации
        getCommand("seeme").setExecutor(new com.loma.plugin.commands.SeemeCommand(this));

        // Команда статистики
        com.loma.plugin.commands.StatsCommand stats = new com.loma.plugin.commands.StatsCommand(this);
        getCommand("stats").setExecutor(stats);
        getCommand("stats").setTabCompleter(stats);
        
        // Тоггл доступности серверов
        getCommand("srv").setExecutor(new com.loma.plugin.commands.SrvCommand(this));
        
        // Сообщения в ЛС (кросс-сервер)
        getCommand("msg").setExecutor(new com.loma.plugin.commands.MsgCommand(this));
        getCommand("r").setExecutor(new com.loma.plugin.commands.ReplyCommand(this));
        
        // Команда авторизации
        getCommand("login").setExecutor(new com.loma.plugin.commands.LoginCommand(this));
        
        // Команда от HolaBot для создания аккаунтов
        getCommand("holabot_account_add").setExecutor(new com.loma.plugin.commands.HolaBotCommand(this));
    }

    private void registerListeners() {
        // Регистрация событий
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerRideListener(this), this);
        // Достижения (триггеры)
        Bukkit.getPluginManager().registerEvents(new com.loma.plugin.listeners.AchievementsListener(this), this);
        // Защита лобби
        Bukkit.getPluginManager().registerEvents(new LobbyProtectionListener(this), this);

        // Слушатели для NPC
        Bukkit.getPluginManager().registerEvents(new NPCInteractListener(this), this);

        // Двойной прыжок
        if (getConfig().getBoolean("features.double-jump.enabled")) {
            Bukkit.getPluginManager().registerEvents(new DoubleJumpListener(this), this);
        }

        // Защита от голода
        if (getConfig().getBoolean("features.disable-hunger")) {
            Bukkit.getPluginManager().registerEvents(new HungerDisableListener(), this);
        }

        // Слушатель чата
        if (getConfig().getBoolean("features.custom-chat.enabled")) {
            Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);
        }
        
        // Слушатель авторизации
        Bukkit.getPluginManager().registerEvents(new com.loma.plugin.listeners.LoginListener(this), this);
    }

    private void loadConfigs() {
        // Загрузка локализации
        String lang = getConfig().getString("localization.language", "ru").toLowerCase();
        File localesDir = new File(getDataFolder(), "locales");
        if (!localesDir.exists()) localesDir.mkdirs();

        File localeFile = new File(localesDir, "messages_" + lang + ".yml");
        if (!localeFile.exists()) {
            // Попробуем использовать резервный файл messages_<lang>_back.yml, если он есть
            File backupLocale = new File(localesDir, "messages_" + lang + "_back.yml");
            if (backupLocale.exists()) {
                localeFile = backupLocale;
            } else {
                // Пытаемся сохранить встроенный ресурс
                String resourcePath = "locales/messages_" + lang + ".yml";
                if (getResource(resourcePath) != null) {
                    saveResource(resourcePath, false);
                } else {
                    // Фолбэк на английский
                    saveResource("locales/messages_en.yml", false);
                    localeFile = new File(localesDir, "messages_en.yml");
                }
            }
        }
        messagesConfig = YamlConfiguration.loadConfiguration(localeFile);

        // Overlay: load optional messages_ru_new.yml and merge missing keys
        try {
            java.io.File localesDir2 = new java.io.File(getDataFolder(), "locales");
            java.io.File overlay = new java.io.File(localesDir2, "messages_ru_new.yml");
            if (overlay.exists()) {
                org.bukkit.configuration.file.YamlConfiguration extra = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(overlay);
                for (String key : extra.getKeys(true)) {
                    if (extra.isConfigurationSection(key)) continue;
                    if (!messagesConfig.contains(key)) {
                        messagesConfig.set(key, extra.get(key));
                    }
                }
            }
        } catch (Exception ignored) {}

        // Загрузка конфигурации NPC (бандл-ресурс больше не требуется)
        File npcsFile = new File(getDataFolder(), "npcs.yml");
        if (!npcsFile.exists()) {
            try {
                getDataFolder().mkdirs();
                if (npcsFile.createNewFile()) {
                    getLogger().info("Created empty npcs.yml in data folder");
                }
            } catch (Exception e) {
                getLogger().warning("Failed to create npcs.yml: " + e.getMessage());
            }
        }
        npcsConfig = YamlConfiguration.loadConfiguration(npcsFile);
        
        // Загрузка конфигурации серверов
        File serversFile = new File(getDataFolder(), "servers.yml");
        if (!serversFile.exists()) {
            saveResource("servers.yml", false);
        }
        serversConfig = YamlConfiguration.loadConfiguration(serversFile);
    }

    private void loadData() {
        // Загрузка спавна (с проверками на null)
        if (getConfig().contains("spawn")) {
            String worldName = getConfig().getString("spawn.world");
            if (worldName == null || worldName.isEmpty()) {
                getLogger().warning("Spawn configuration found but 'spawn.world' is missing. Skipping spawn load.");
            } else {
                org.bukkit.World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    getLogger().warning("World '" + worldName + "' is not loaded. Skipping spawn load.");
                } else {
                    Location spawn = new Location(
                            world,
                            getConfig().getDouble("spawn.x"),
                            getConfig().getDouble("spawn.y"),
                            getConfig().getDouble("spawn.z"),
                            (float) getConfig().getDouble("spawn.yaw"),
                            (float) getConfig().getDouble("spawn.pitch")
                    );
                    spawnManager.setSpawn(spawn);
                }
            }
        }

        // Загрузка NPC
        npcManager.loadNPCs();

        // Автосообщения отключены по требованию

        // Запуск обновления скорборда
        if (getConfig().getBoolean("features.scoreboard.enabled")) {
            scoreboardManager.startUpdateTask();
        }
    }

    private void saveData() {
        // Сохранение спавна
        if (spawnManager == null) return; // безопасно выйти, если инициализация не завершилась
        Location spawn = spawnManager.getSpawn();
        if (spawn != null) {
            getConfig().set("spawn.world", spawn.getWorld().getName());
            getConfig().set("spawn.x", spawn.getX());
            getConfig().set("spawn.y", spawn.getY());
            getConfig().set("spawn.z", spawn.getZ());
            getConfig().set("spawn.yaw", spawn.getYaw());
            getConfig().set("spawn.pitch", spawn.getPitch());
            saveConfig();
        }

        // Сохранение NPC
        npcManager.saveNPCs();
    }

    private void startAutoMessages() {
        int interval = getConfig().getInt("features.auto-messages.interval", 300) * 20; // В тиках

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            List<String> messages = getConfig().getStringList("features.auto-messages.messages");
            if (!messages.isEmpty()) {
                String message = messages.get((int) (Math.random() * messages.size()));
                Bukkit.getOnlinePlayers().forEach(player ->
                        MessageUtils.send(player, message));
            }
        }, interval, interval);
    }

    public void reload() {
        reloadConfig();
        loadConfigs();

        // Перезагрузка менеджеров
        npcManager.reload();
        scoreboardManager.reload();
        if (menuManager != null) menuManager.reload();
        if (achievementsManager != null) achievementsManager.reload();

        MessageUtils.sendConsole("&aLoMa configuration reloaded!");
    }

    // Getters
    public static LoMa getInstance() {
        return instance;
    }

    public NPCManager getNPCManager() {
        return npcManager;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public AnimationManager getAnimationManager() {
        return animationManager;
    }

    public CosmeticsManager getCosmeticsManager() {
        return cosmeticsManager;
    }

    public PreferencesManager getPreferencesManager() {
        return preferencesManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public PlaytimeService getPlaytimeService() {
        return playtimeService;
    }

    public AchievementsManager getAchievementsManager() {
        return achievementsManager;
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public FileConfiguration getNPCsConfig() {
        return npcsConfig;
    }

    public String getMessage(String path) {
        return MessageUtils.color(messagesConfig.getString(path, "&cMessage not found: " + path));
    }

    /**
     * Получить данные игрока (строка)
     * TODO: Интеграция с Velocity через Plugin Messaging или БД
     */
    public String getPlayerData(UUID uuid, String key, String defaultValue) {
        // Временная заглушка - возвращает дефолтное значение
        // В будущем: запрос к Velocity через plugin messaging или прямой запрос к БД
        return defaultValue;
    }

    /**
     * Получить данные игрока (число)
     * TODO: Интеграция с Velocity через Plugin Messaging или БД
     */
    public int getPlayerDataInt(UUID uuid, String key, int defaultValue) {
        PlayerStatsCache cache = statsCache.get(uuid);
        if (cache != null) {
            if (key.equals("achievements")) return cache.achievements;
        }
        return defaultValue;
    }

    // ===== ЛС (последний собеседник) =====
    public void setLastPmPartner(UUID uuid, String name) {
        if (name == null) lastPmPartner.remove(uuid); else lastPmPartner.put(uuid, name);
    }
    public String getLastPmPartner(UUID uuid) {
        return lastPmPartner.get(uuid);
    }

    // ========== PLUGIN MESSAGING ==========

    private void registerPluginMessaging() {
        // Outgoing channel for proxy connections (used by servers menu)
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // Custom stats channels
        getServer().getMessenger().registerOutgoingPluginChannel(this, "loma:stats");
        getServer().getMessenger().registerIncomingPluginChannel(this, "loma:stats", this);
        
        // Accounts channel for login system
        getServer().getMessenger().registerOutgoingPluginChannel(this, "loma:accounts");
        getServer().getMessenger().registerIncomingPluginChannel(this, "loma:accounts", this);
        
        MessageUtils.sendConsole("&aPlugin Messaging registered: BungeeCord, loma:stats, loma:accounts");
    }

    /**
     * Запросить данные игрока из Velocity
     */
    public void requestPlayerStats(org.bukkit.entity.Player requester, UUID targetUUID) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GET_PLAYTIME");
        out.writeUTF(targetUUID.toString());

        requester.sendPluginMessage(this, "loma:stats", out.toByteArray());
    }

    /** Запрос профиля игрока по нику (Velocity) */
    public void requestPlayerProfileByName(org.bukkit.entity.Player requester, String name) {
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("GET_PLAYER_PROFILE_BY_NAME");
            out.writeUTF(name);
            requester.sendPluginMessage(this, "loma:stats", out.toByteArray());
        } catch (Exception ignored) {}
    }

    /** Запрос профиля игрока по UUID (Velocity) */
    public void requestPlayerProfileByUUID(org.bukkit.entity.Player requester, UUID uuid) {
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("GET_PLAYER_PROFILE_BY_UUID");
            out.writeUTF(uuid.toString());
            requester.sendPluginMessage(this, "loma:stats", out.toByteArray());
        } catch (Exception ignored) {}
    }

    /** Установить видимость местоположения (делится ли игрок своим текущим сервером) */
    public void setShareVisibility(UUID uuid, boolean share) {
        try {
            org.bukkit.entity.Player any = null;
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) { any = p; break; }
            if (any == null) return;
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("SET_SHARE");
            out.writeUTF(uuid.toString());
            out.writeByte(share ? 1 : 0);
            any.sendPluginMessage(this, "loma:stats", out.toByteArray());
        } catch (Exception ignored) {}
    }

    /** Запросить общее количество игроков в сети (Velocity) */
    public void requestNetworkOnline(org.bukkit.entity.Player requester) {
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("GET_NETWORK_ONLINE");
            requester.sendPluginMessage(this, "loma:stats", out.toByteArray());
        } catch (Exception ignored) {}
    }

    /** Запросить наигранное время по серверам (Velocity) */
    public void requestPlaytimePerServer(org.bukkit.entity.Player requester, UUID targetUUID) {
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("GET_PLAYTIME_PER_SERVER");
            out.writeUTF(targetUUID.toString());
            requester.sendPluginMessage(this, "loma:stats", out.toByteArray());
        } catch (Exception ignored) {}
    }

    @Override
    public void onPluginMessageReceived(String channel, org.bukkit.entity.Player player, byte[] message) {
        if (!channel.equals("loma:stats") && !channel.equals("loma:accounts")) return;

        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String action = in.readUTF();

            if (action.equals("STATS_RESPONSE")) {
                String uuidStr = in.readUTF();
                UUID uuid = UUID.fromString(uuidStr);
                long minutes = in.readLong();
                String rank = in.readUTF();
                int achievements = in.readInt();

                // Сохраняем в кэш
                statsCache.put(uuid, new PlayerStatsCache(minutes, rank, achievements));

                // Уведомляем скорборд об обновлении
                Player targetPlayer = getServer().getPlayer(uuid);
                if (targetPlayer != null && scoreboardManager != null) {
                    scoreboardManager.updateScoreboard(targetPlayer);
                }

                getLogger().info("Received stats for " + uuid + ": " + minutes + " minutes, rank: " + rank);
            } else if (action.equals("NETWORK_ONLINE")) {
                int total = in.readInt();
                this.networkOnline = total;
                getLogger().fine("Network online: " + total);
            } else if (action.equals("PER_SERVER_PLAYTIME")) {
                try {
                    UUID uuid = UUID.fromString(in.readUTF());
                    int count = in.readInt();
                    Map<String, Long> map = new java.util.HashMap<>();
                    for (int i = 0; i < count; i++) {
                        String srv = in.readUTF();
                        long mins = in.readLong();
                        map.put(srv, mins);
                    }
                    perServerPlaytime.put(uuid, map);

                    // Если у отправителя открыт stats для этой цели — обновим
                    if (player != null && getMenuManager() != null && player.getOpenInventory() != null) {
                        org.bukkit.inventory.Inventory top = player.getOpenInventory().getTopInventory();
                        if (top != null) {
                            getMenuManager().updateStatsMenu(player, top, uuid);
                        }
                    }
                } catch (Exception ex) {
                    getLogger().warning("Failed to parse PER_SERVER_PLAYTIME: " + ex.getMessage());
                }
            } else if (action.equals("PLAYER_PROFILE")) {
                // Расширенный профиль игрока
                try {
                    UUID uuid = UUID.fromString(in.readUTF());
                    String username = in.readUTF();
                    long minutes = in.readLong();
                    String rank = in.readUTF();
                    int achievements = in.readInt();
                    boolean online = in.readByte() == 1;
                    String currentServer = in.readUTF();
                    long lastSeen = in.readLong();
                    long firstJoin = in.readLong();
                    String lastServer = in.readUTF();
                    boolean share = in.readByte() == 1;

                    PlayerProfileCache cache = new PlayerProfileCache(uuid, username, minutes, rank, achievements, online, currentServer, lastSeen, firstJoin, lastServer, share);
                    profileCache.put(uuid, cache);

                    // Обновляем открытое меню профиля цели, если сейчас открыто у запрашивающего игрока
                    if (player != null && getMenuManager() != null && player.getOpenInventory() != null) {
                        org.bukkit.inventory.Inventory top = player.getOpenInventory().getTopInventory();
                        if (top != null) {
                            getMenuManager().updateProfileMenuForTarget(player, top, uuid);
                        }
                    }
                } catch (Exception ex) {
                    getLogger().warning("Failed to parse PLAYER_PROFILE: " + ex.getMessage());
                }
            } else if (action.equals("COSMETICS_STATE")) {
                try {
                    UUID uuid = UUID.fromString(in.readUTF());
                    String hat = in.readUTF();
                    String particle = in.readUTF();
                    String animation = in.readUTF();
                    if (cosmeticsManager != null) {
                        cosmeticsManager.applyCosmeticsFromProxy(uuid,
                                (hat == null || hat.isEmpty()) ? null : hat,
                                (particle == null || particle.isEmpty()) ? null : particle,
                                (animation == null || animation.isEmpty()) ? null : animation);
                    }
                } catch (Exception ex) {
                    getLogger().warning("Failed to process COSMETICS_STATE: " + ex.getMessage());
                }
            } else if (action.equals("PRIVATE_MSG_DELIVER")) {
                // Доставка ЛС получателю по его UUID (в payload)
                try {
                    java.util.UUID targetUUID = java.util.UUID.fromString(in.readUTF());
                    String fromName = in.readUTF();
                    String fromUuid = in.readUTF();
                    String msg = in.readUTF();
                    org.bukkit.entity.Player target = getServer().getPlayer(targetUUID);
                    if (target != null) {
                        setLastPmPartner(target.getUniqueId(), fromName);
                        MessageUtils.send(target, "&d[ЛС] &7от &f" + fromName + "&7: &r" + msg);
                        target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
                    }
                } catch (Exception ex) {
                    getLogger().warning("Failed to process PRIVATE_MSG_DELIVER: " + ex.getMessage());
                }
            } else if (action.equals("PRIVATE_MSG_STATUS")) {
                // ACK отправителю (route by sender UUID)
                try {
                    java.util.UUID senderUUID = java.util.UUID.fromString(in.readUTF());
                    String target = in.readUTF();
                    boolean ok = in.readBoolean();
                    org.bukkit.entity.Player senderPl = getServer().getPlayer(senderUUID);
                    if (senderPl != null && !ok) {
                        MessageUtils.send(senderPl, "&cИгрок &f" + target + "&c не найден в сети.");
                    }
                } catch (Exception ex) {
                    getLogger().warning("Failed to process PRIVATE_MSG_STATUS: " + ex.getMessage());
                }
            } else if (channel.equals("loma:accounts") && action.equals("ACCOUNT_CHECK_RESPONSE")) {
                // Ответ от Velocity на проверку наличия аккаунта
                String username = in.readUTF();
                boolean exists = in.readBoolean();
                boolean autoLogin = false;
                String allowedServers = "";
                if (exists) {
                    // password_hash пропускаем
                    String ignoredHash = in.readUTF();
                    allowedServers = in.readUTF();
                    autoLogin = in.readBoolean();

                    // Сохраним список доступных серверов в кэш плагина
                    org.bukkit.entity.Player target = getServer().getPlayerExact(username);
                    if (target != null) {
                        java.util.List<String> servers = new java.util.ArrayList<>();
                        if (allowedServers != null && !allowedServers.isEmpty()) {
                            for (String s : allowedServers.split(",")) {
                                if (!s.trim().isEmpty()) servers.add(s.trim());
                            }
                        }
                        playerAllowedServers.put(target.getUniqueId(), servers);
                    }
                }

                // Вернём результат через отложенный callback
                java.util.function.BiConsumer<Boolean, Boolean> cb = pendingAccountChecks.remove(username.toLowerCase());
                if (cb != null) {
                    final boolean resExists = exists;
                    final boolean resAuto = autoLogin;
                    Bukkit.getScheduler().runTask(this, () -> cb.accept(resExists, resAuto));
                }
            }
        } catch (Exception e) {
            getLogger().warning("Failed to process plugin message: " + e.getMessage());
        }
    }

    /**
     * Получить данные из кэша
     */
    public PlayerStatsCache getStatsCache(UUID uuid) {
        return statsCache.get(uuid);
    }

    /** Наигранное время по серверам из кэша (null если нет данных) */
    public Map<String, Long> getPerServerPlaytime(UUID uuid) {
        return perServerPlaytime.get(uuid);
    }

    /** Количество игроков на всей сети (если неизвестно — -1) */
    public int getNetworkOnline() { return networkOnline; }
    
    // ========== МЕТОДЫ АВТОРИЗАЦИИ ==========
    
    /**
     * Проверить, авторизован ли игрок
     */
    public boolean isPlayerAuthorized(UUID uuid) {
        return authorizedPlayers.getOrDefault(uuid, false);
    }
    
    /**
     * Установить статус авторизации игрока
     */
    public void setPlayerAuthorized(UUID uuid, boolean authorized) {
        if (authorized) {
            authorizedPlayers.put(uuid, true);
        } else {
            authorizedPlayers.remove(uuid);
            playerAllowedServers.remove(uuid);
        }
    }
    
    /**
     * Получить список разрешённых серверов для игрока
     */
    public java.util.List<String> getPlayerAllowedServers(UUID uuid) {
        return playerAllowedServers.getOrDefault(uuid, new java.util.ArrayList<>());
    }
    
    /**
     * Проверить существование аккаунта через Velocity
     */
    public void checkAccountExists(Player player, java.util.function.BiConsumer<Boolean, Boolean> callback) {
        try {
            // Готовим payload
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("CHECK_ACCOUNT");
            out.writeUTF(player.getName());
            out.writeUTF(player.getAddress().getAddress().getHostAddress());

            // Ставим callback и таймаут ДО отправки
            String key = player.getName().toLowerCase();
            pendingAccountChecks.put(key, callback);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (pendingAccountChecks.remove(key) != null) {
                    callback.accept(false, false);
                }
            }, 20L * 5);

            // Отправляем на главном потоке с небольшой задержкой (чтобы соединение успело установиться)
            Bukkit.getScheduler().runTaskLater(this, () -> {
                try {
                    if (!player.isOnline()) return;
                    getLogger().info("Sending CHECK_ACCOUNT for " + player.getName() + " ip=" + player.getAddress().getAddress().getHostAddress());
                    player.sendPluginMessage(this, "loma:accounts", out.toByteArray());
                } catch (Exception ex) {
                    getLogger().warning("Failed to send CHECK_ACCOUNT: " + ex.getMessage());
                }
            }, 10L);
        } catch (Exception e) {
            getLogger().warning("Failed to check account: " + e.getMessage());
            Bukkit.getScheduler().runTask(this, () -> callback.accept(false, false));
        }
    }
    
    /**
     * Проверить логин игрока
     */
    public void checkLogin(Player player, String password, java.util.function.BiConsumer<Boolean, java.util.List<String>> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                // Отправляем запрос на проверку пароля через Velocity
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("CHECK_LOGIN");
                out.writeUTF(player.getName());
                out.writeUTF(password);
                out.writeUTF(player.getAddress().getAddress().getHostAddress());
                
                player.sendPluginMessage(this, "loma:accounts", out.toByteArray());
                
                // ВРЕМЕННОЕ РЕШЕНИЕ: для демо всегда успешно
                // В реальности нужно дождаться ответа от Velocity
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    java.util.List<String> servers = new java.util.ArrayList<>();
                    servers.add("survival");
                    servers.add("main_events");
                    playerAllowedServers.put(player.getUniqueId(), servers);
                    callback.accept(true, servers);
                }, 20L);
                
            } catch (Exception e) {
                getLogger().warning("Failed to check login: " + e.getMessage());
                Bukkit.getScheduler().runTask(this, () -> callback.accept(false, null));
            }
        });
    }
    
    /**
     * Получить конфигурацию серверов
     */
    public FileConfiguration getServersConfig() {
        return serversConfig;
    }
    
    /**
     * Создать аккаунт в Velocity (отправка plugin message)
     */
    public void createAccount(String username, String passwordHash, String servers, String discordId) {
        getServer().getScheduler().runTask(this, () -> {
            try {
                // Регистрируем канал если не зарегистрирован
                if (!getServer().getMessenger().isOutgoingChannelRegistered(this, "loma:accounts")) {
                    getServer().getMessenger().registerOutgoingPluginChannel(this, "loma:accounts");
                    getLogger().info("Registered outgoing channel: loma:accounts");
                }
                
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("CREATE_ACCOUNT");
                out.writeUTF(username);
                out.writeUTF(passwordHash);
                out.writeUTF(servers);
                out.writeUTF(discordId);
                
                // Отправляем через любого онлайн игрока (включая HoloBot)
                Player sender = null;
                for (Player p : getServer().getOnlinePlayers()) {
                    sender = p;
                    getLogger().info("Found player for plugin message: " + p.getName());
                    break;
                }
                
                if (sender != null) {
                    byte[] data = out.toByteArray();
                    sender.sendPluginMessage(this, "loma:accounts", data);
                    getLogger().info("Sent plugin message to Velocity via " + sender.getName() + " (size: " + data.length + " bytes)");
                } else {
                    getLogger().warning("Cannot create account - no players online");
                }
            } catch (Exception e) {
                getLogger().severe("Error creating account: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Класс для кэширования данных игрока
     */
    public static class PlayerStatsCache {
        public final long playtimeMinutes;
        public final String rank;
        public final int achievements;
        
        public PlayerStatsCache(long playtimeMinutes, String rank, int achievements) {
            this.playtimeMinutes = playtimeMinutes;
            this.rank = rank;
            this.achievements = achievements;
        }
        
        public String getFormattedPlaytime() {
            long hours = playtimeMinutes / 60;
            long mins = playtimeMinutes % 60;
            return hours + "ч " + mins + "м";
        }
    }
}