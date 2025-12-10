package com.loma.plugin.managers;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.ItemBuilder;
import com.loma.plugin.utils.MessageUtils;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.io.File;

public class MenuManager {

    private final LoMa plugin;
    private final Map<String, MenuData> menus;
    private final Map<String, ServerRule> serverRules = new HashMap<>();
    private final java.util.Map<java.util.UUID, Long> lastConnect = new java.util.concurrent.ConcurrentHashMap<>();

    public MenuManager(LoMa plugin) {
        this.plugin = plugin;
        this.menus = new HashMap<>();

        loadMenus();
    }

    private java.util.Map<String, String> parseParams(String paramStr) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        if (paramStr == null || paramStr.isEmpty()) return map;
        // paramStr format: key=value,key2=value2 (no quotes)
        String[] pairs = paramStr.split(",");
        for (String pair : pairs) {
            if (pair == null) continue;
            String p = pair.trim();
            if (p.isEmpty()) continue;
            int eq = p.indexOf('=');
            if (eq <= 0) continue;
            String k = p.substring(0, eq).trim();
            String v = p.substring(eq + 1).trim();
            if (!k.isEmpty()) map.put(k, v);
        }
        return map;
    }

    private String formatAgo(long whenMs) {
        long now = System.currentTimeMillis();
        long diff = Math.max(0, now - whenMs);
        long sec = diff / 1000;
        long min = sec / 60;
        long hr = min / 60;
        long day = hr / 24;

        if (day > 0) return day + " д назад";
        if (hr > 0) return hr + " ч назад";
        if (min > 0) return min + " мин назад";
        return "только что";
    }

    /** Обновить меню профиля для целевого игрока */
    public void updateProfileMenuForTarget(Player viewer, Inventory inventory, java.util.UUID targetUuid) {
        // Загружаем макет
        File profileFile = new File(plugin.getDataFolder(), "menus/profile.yml");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(profileFile);

        LoMa.PlayerProfileCache pc = plugin.getPlayerProfile(targetUuid);
        if (pc == null) {
            // Запросим у Velocity и покажем временно загрузку
            plugin.requestPlayerProfileByUUID(viewer, targetUuid);
            inventory.setItem(13, new ItemBuilder(Material.PLAYER_HEAD)
                    .setSkullOwner("Steve")
                    .setName(plugin.getMessage("profile.loading.title"))
                    .setLore(java.util.Collections.singletonList(plugin.getMessage("profile.loading.wait")))
                    .build());
            return;
        }

        String username = pc.username != null ? pc.username : targetUuid.toString();
        String playtime = pc.getFormattedPlaytime();
        boolean showServer = pc.share && pc.online && pc.currentServer != null && !pc.currentServer.isEmpty();

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(pc.online ? plugin.getMessage("profile.status.online") : plugin.getMessage("profile.status.offline"));
        if (pc.online) {
            String serverLine = showServer
                    ? plugin.getMessage("profile.server.current").replace("{server}", pc.currentServer)
                    : plugin.getMessage("profile.server.hidden");
            lore.add(serverLine);
        } else {
            String lastSrv = pc.lastServer != null && !pc.lastServer.isEmpty() ? pc.lastServer : "?";
            lore.add(plugin.getMessage("profile.server.last").replace("{server}", lastSrv));
        }
        lore.add(plugin.getMessage("profile.playtime").replace("{time}", playtime));
        if (pc.lastSeen > 0) {
            lore.add(plugin.getMessage("profile.last-seen").replace("{time}", formatAgo(pc.lastSeen)));
        }
        if (pc.firstJoin > 0) {
            lore.add(plugin.getMessage("profile.first-join").replace("{time}", formatAgo(pc.firstJoin)));
        }

        ItemBuilder head = new ItemBuilder(Material.PLAYER_HEAD)
                .setSkullOwner(username)
                .setName(MessageUtils.color("&a&l" + username))
                .setLore(lore);
        if (showServer) {
            head.addNBTTag("action", "server:" + pc.currentServer);
        }
        inventory.setItem(13, head.build());

        // Кнопка Статистика по цели (слот 30)
        inventory.setItem(30, new ItemBuilder(Material.DIAMOND)
                .setName(plugin.getMessage("menu.stats.name"))
                .setLore(java.util.Arrays.asList(
                        plugin.getMessage("menu.stats.lore1"),
                        plugin.getMessage("menu.stats.lore2")))
                .addNBTTag("action", "stats:" + targetUuid.toString())
                .build());
    }

    public void reload() {
        this.menus.clear();
        this.serverRules.clear();
        loadMenus();
    }

    // Holder to tag our menus
    private static class MenuHolder implements org.bukkit.inventory.InventoryHolder {
        private final String menuName;
        public MenuHolder(String menuName) { this.menuName = menuName; }
        @Override
        public Inventory getInventory() { return null; }
        public String getMenuName() { return menuName; }
    }

    private void updateSettingsMenu(Player player, Inventory inv) {
        java.util.function.Function<Boolean, String> state = b -> b ? MessageUtils.color("&aВключено") : MessageUtils.color("&cВыключено");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(new File(new File(plugin.getDataFolder(), "menus"), "settings.yml"));
        ConfigurationSection items = cfg.getConfigurationSection("items");
        java.util.Set<String> placedToggles = new java.util.HashSet<>();
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection s = items.getConfigurationSection(key);
                if (s == null) continue;
                int slot = s.getInt("slot", -1);
                if (slot < 0 || slot >= inv.getSize()) continue;
                String action = s.getString("action", "");

                Material mat = safeMaterial(s.getString("material", "STONE"));
                String name = resolveText(s.getString("name", key));
                java.util.List<String> lore = resolveTextList(s.getStringList("lore"));

                if (action.startsWith("toggle:")) {
                    String feature = action.substring("toggle:".length()).toLowerCase();
                    // Убираем дубликаты одинаковых переключателей
                    if (placedToggles.contains(feature)) continue;
                    placedToggles.add(feature);

                    // Специально: переопределяем отображение и позицию видимости игроков
                    if (feature.equalsIgnoreCase("visibility")) {
                        slot = 12; // центр верхнего ряда (между полётом и частицами)
                        name = MessageUtils.color("&aВидимость игроков: {state}");
                        lore = java.util.Collections.singletonList(MessageUtils.color("&7Показывать или скрывать игроков"));
                    }
                    boolean defaultEnabled = !feature.equals("fly");
                    boolean enabled = plugin.getPreferencesManager().isEnabled(player.getUniqueId(), feature, defaultEnabled);
                    name = name.replace("{state}", state.apply(enabled));
                    ItemStack item = new ItemBuilder(mat)
                            .setName(name)
                            .setLore(lore)
                            .setGlowing(enabled)
                            .addNBTTag("action", action)
                            .build();
                    inv.setItem(slot, item);
                } else if ("toggleall".equalsIgnoreCase(action)) {
                    if (placedToggles.contains("toggleall")) continue; // защита от дублирования
                    placedToggles.add("toggleall");
                    // Все функции включены?
                    boolean all = allFeaturesEnabled(player.getUniqueId());
                    name = name.replace("{state}", state.apply(all));
                    ItemStack item = new ItemBuilder(mat)
                            .setName(name)
                            .setLore(lore)
                            .setGlowing(all)
                            .addNBTTag("action", action)
                            .build();
                    inv.setItem(slot, item);
                }
            }
        }
    }

    // Меню Шляп с пагинацией: поддержка единого файла menus/hats.yml (pages.*) и legacy каталога menus/hats/
    private void createHatsMenus() {
        File menusDir = new File(plugin.getDataFolder(), "menus");
        if (!menusDir.exists()) menusDir.mkdirs();

        // Новый формат: один файл с секцией pages (поддержка альтернативного пути menus/hats/hats.yml)
        File combinedH = new File(menusDir, "hats.yml");
        File combinedHAlt = new File(menusDir, "hats/hats.yml");
        if (!combinedH.exists() && !combinedHAlt.exists()) {
            if (plugin.getResource("menus/hats.yml") != null) plugin.saveResource("menus/hats.yml", false);
            else if (plugin.getResource("menus/hats/hats.yml") != null) plugin.saveResource("menus/hats/hats.yml", false);
        }
        if (combinedH.exists() || combinedHAlt.exists()) {
            FileConfiguration root = YamlConfiguration.loadConfiguration(combinedH.exists() ? combinedH : combinedHAlt);
            ConfigurationSection pages = root.getConfigurationSection("pages");
            if (pages != null && !pages.getKeys(false).isEmpty()) {
                java.util.Map<Integer, java.util.List<ConfigurationSection>> byPage = new java.util.TreeMap<>();
                for (String key : pages.getKeys(false)) {
                    ConfigurationSection sec = pages.getConfigurationSection(key);
                    if (sec == null) continue;
                    int page = sec.getInt("page", 1);
                    byPage.computeIfAbsent(page, k -> new java.util.ArrayList<>()).add(sec);
                }

                int total = byPage.isEmpty() ? 1 : byPage.keySet().stream().mapToInt(i -> i).max().orElse(1);
                String title = resolveText(root.getString("title", "&8Шляпы"));
                int size = Math.max(27, Math.min(54, root.getInt("size", 54)));
                String layout = root.getString("layout", "grid").toLowerCase(); // grid|spaced
                boolean fillEnabled = root.getBoolean("fill-item.enabled", true);
                Material fillMat = safeMaterial(root.getString("fill-item.material", "PURPLE_STAINED_GLASS_PANE"));
                String fillName = root.getString("fill-item.name", " ");
                ItemStack filler = new ItemBuilder(fillMat).setName(MessageUtils.color(fillName)).build();

                for (int page = 1; page <= total; page++) {
                    String pageTitle = title + MessageUtils.color(" &7(") + page + "/" + total + MessageUtils.color(")");
                    Inventory inv = Bukkit.createInventory(null, size, pageTitle);
                    if (fillEnabled) for (int s = 0; s < inv.getSize(); s++) inv.setItem(s, filler);

                    java.util.List<ItemStack> hatItems = new java.util.ArrayList<>();
                    java.util.List<ConfigurationSection> defs = byPage.getOrDefault(page, java.util.Collections.emptyList());
                    for (ConfigurationSection def : defs) {
                        ConfigurationSection items = def.getConfigurationSection("items");
                        if (items == null) continue;
                        for (String itKey : items.getKeys(false)) {
                            ConfigurationSection s = items.getConfigurationSection(itKey);
                            if (s == null) continue;
                            Material mat = safeMaterial(s.getString("material", "LEATHER_HELMET"));
                            String iname = resolveText(s.getString("name", itKey));
                            java.util.List<String> lore = resolveTextList(s.getStringList("lore"));
                            String hatId = s.getString("id", itKey);
                            String action = "hat:" + hatId;
                            hatItems.add(new ItemBuilder(mat).setName(iname).setLore(lore).addNBTTag("action", action).build());
                        }
                    }

                    java.util.List<Integer> contentSlots = computeSpacedSlots(size);
                    for (int idx = 0; idx < hatItems.size() && idx < contentSlots.size(); idx++) inv.setItem(contentSlots.get(idx), hatItems.get(idx));

                    // off button
                    if (root.isConfigurationSection("off-button")) {
                        ConfigurationSection off = root.getConfigurationSection("off-button");
                        int oslot = off.getInt("slot", size - 1);
                        String defOffName = plugin.getMessage("cosmetics.hat.off-name");
                        ItemStack offItem = new ItemBuilder(safeMaterial(off.getString("material", "BARRIER")))
                                .setName(resolveText(off.getString("name", defOffName)))
                                .setLore(resolveTextList(off.getStringList("lore")))
                                .addNBTTag("action", "hat:off").build();
                        inv.setItem(oslot, offItem);
                    }

                    int prevSlot = size - 9;
                    int nextSlot = size - 1;
                    if (page > 1) inv.setItem(prevSlot, new ItemBuilder(Material.ARROW).setName(plugin.getMessage("menu.prev")).addNBTTag("action", "menu:hats" + (page - 1 == 1 ? "" : ":" + (page - 1))).build()); else inv.setItem(prevSlot, filler);
                    if (page < total) inv.setItem(nextSlot, new ItemBuilder(Material.ARROW).setName(plugin.getMessage("menu.next")).addNBTTag("action", "menu:hats:" + (page + 1)).build()); else inv.setItem(nextSlot, filler);

                    int backSlot = size - 5;
                    inv.setItem(backSlot, new ItemBuilder(Material.BARRIER).setName(plugin.getMessage("menu.back")).addNBTTag("action", "menu:cosmetics").build());

                    String key = page == 1 ? "hats" : ("hats:" + page);
                    menus.put(key, new MenuData(key, pageTitle, size, inv));
                }
                return;
            }
        }

        // Legacy каталожный формат: menus/hats/*.yml
        File pagesDir = new File(menusDir, "hats");
        if (!pagesDir.exists()) {
            pagesDir.mkdirs();
            if (plugin.getResource("menus/hats/first_page.yml") != null) plugin.saveResource("menus/hats/first_page.yml", false);
        }

        java.util.List<File> filesList = new java.util.ArrayList<>();
        try {
            java.nio.file.Files.walk(pagesDir.toPath())
                    .filter(p -> java.nio.file.Files.isRegularFile(p) && (p.getFileName().toString().toLowerCase().endsWith(".yml") || p.getFileName().toString().toLowerCase().endsWith(".yaml")))
                    .forEach(p -> filesList.add(p.toFile()));
        } catch (Exception ignored) {}
        if (filesList.isEmpty()) return;
        filesList.sort(java.util.Comparator.comparing(File::getName));

        int total = filesList.size();
        for (int i = 0; i < filesList.size(); i++) {
            File f = filesList.get(i);
            int page = i + 1;
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            String title = resolveText(cfg.getString("title", "&8Hats")) + MessageUtils.color(" &7(") + page + "/" + total + MessageUtils.color(")");
            int size = Math.max(27, Math.min(54, cfg.getInt("size", 54)));
            Inventory inv = Bukkit.createInventory(null, size, title);

            boolean fillEnabled = cfg.getBoolean("fill-item.enabled", true);
            Material fillMat = safeMaterial(cfg.getString("fill-item.material", "PURPLE_STAINED_GLASS_PANE"));
            String fillName = cfg.getString("fill-item.name", " ");
            ItemStack filler = new ItemBuilder(fillMat).setName(MessageUtils.color(fillName)).build();
            if (fillEnabled) for (int s = 0; s < inv.getSize(); s++) inv.setItem(s, filler);

            java.util.List<ItemStack> hatItems = new java.util.ArrayList<>();
            ConfigurationSection items = cfg.getConfigurationSection("items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    ConfigurationSection s = items.getConfigurationSection(key);
                    if (s == null) continue;
                    Material mat = safeMaterial(s.getString("material", "LEATHER_HELMET"));
                    String name = resolveText(s.getString("name", key));
                    java.util.List<String> lore = resolveTextList(s.getStringList("lore"));
                    String hatId = s.getString("id", key);
                    String action = "hat:" + hatId;
                    hatItems.add(new ItemBuilder(mat).setName(name).setLore(lore).addNBTTag("action", action).build());
                }
            }

            java.util.List<Integer> contentSlots = computeSpacedSlots(size);
            for (int idx = 0; idx < hatItems.size() && idx < contentSlots.size(); idx++) inv.setItem(contentSlots.get(idx), hatItems.get(idx));

            if (cfg.isConfigurationSection("off-button")) {
                ConfigurationSection off = cfg.getConfigurationSection("off-button");
                int oslot = off.getInt("slot", size - 1);
                String defOffName = plugin.getMessage("cosmetics.hat.off-name");
                ItemStack offItem = new ItemBuilder(safeMaterial(off.getString("material", "BARRIER")))
                        .setName(resolveText(off.getString("name", defOffName)))
                        .setLore(resolveTextList(off.getStringList("lore")))
                        .addNBTTag("action", "hat:off").build();
                inv.setItem(oslot, offItem);
            }

            int prevSlot = size - 9;
            int nextSlot = size - 1;
            if (page > 1) inv.setItem(prevSlot, new ItemBuilder(Material.ARROW).setName(plugin.getMessage("menu.prev")).addNBTTag("action", "menu:hats:" + (page - 1)).build()); else inv.setItem(prevSlot, filler);
            if (page < total) inv.setItem(nextSlot, new ItemBuilder(Material.ARROW).setName(plugin.getMessage("menu.next")).addNBTTag("action", "menu:hats:" + (page + 1)).build()); else inv.setItem(nextSlot, filler);

            int backSlot = size - 5;
            inv.setItem(backSlot, new ItemBuilder(Material.BARRIER).setName(plugin.getMessage("menu.back")).addNBTTag("action", "menu:cosmetics").build());

            String key = page == 1 ? "hats" : ("hats:" + page);
            menus.put(key, new MenuData(key, title, size, inv));
        }
    }

    // Вычисляем слоты для контента с отступом в 1 клетку по краям и промежутками в 1 клетку
    // Пример для 54 слотов (6 рядов): используем ряды 1..(rows-2) с шагом 2 и колонки 1..7 с шагом 2
    private java.util.List<Integer> computeSpacedSlots(int size) {
        int rows = Math.max(1, size / 9);
        java.util.List<Integer> result = new java.util.ArrayList<>();
        for (int r = 1; r <= rows - 2; r += 2) {           // пропускаем верхний и нижний ряд
            for (int c = 1; c <= 7; c += 2) {              // колонки 1..7, с отступом от краёв и шагом 2
                int slot = r * 9 + c;
                if (slot >= 0 && slot < size) result.add(slot);
            }
        }
        return result;
    }

    // Плотная сетка: используем каждую ячейку в рядах 1..rows-2 и столбцах 1..7
    private java.util.List<Integer> computeFilledSlots(int size) {
        int rows = Math.max(1, size / 9);
        java.util.List<Integer> result = new java.util.ArrayList<>();
        for (int r = 1; r <= rows - 2; r++) {           // пропускаем верхний и нижний ряд
            for (int c = 1; c <= 7; c++) {              // колонки 1..7, с отступом от краёв
                int slot = r * 9 + c;
                if (slot >= 0 && slot < size) result.add(slot);
            }
        }
        return result;
    }

    private boolean allFeaturesEnabled(java.util.UUID uuid) {
        if (plugin.getPreferencesManager() == null) return true;
        return plugin.getPreferencesManager().isEnabled(uuid, "doublejump", true)
                && plugin.getPreferencesManager().isEnabled(uuid, "particles", true)
                && plugin.getPreferencesManager().isEnabled(uuid, "ride", true)
                && plugin.getPreferencesManager().isEnabled(uuid, "visibility", true)
                && plugin.getPreferencesManager().isEnabled(uuid, "fly", false);
    }

    public boolean canJoinServer(Player player, String server) {
        // Авторизация: лобби доступно всем, остальные — только после /login
        if (!"lobby".equalsIgnoreCase(server) && !plugin.isPlayerAuthorized(player.getUniqueId())) {
            MessageUtils.send(player, "&cСначала авторизуйтесь: &f/login <пароль>");
            return false;
        }

        ServerRule rule = serverRules.get(server.toLowerCase());
        if (rule != null) {
            // Если сервер закрыт — пускаем только тех, кто явно в allow-списке или с байпас-пермой
            if (!rule.open) {
                boolean bypass = player.hasPermission("loma.bypass");
                boolean allowedByName = false;
                boolean allowedByPerm = false;
                for (String val : rule.allowPerms) {
                    if (val == null || val.isEmpty()) continue;
                    // Ник игрока
                    if (player.getName().equalsIgnoreCase(val)) { allowedByName = true; break; }
                    // Или нода пермишена
                    if (player.hasPermission(val)) { allowedByPerm = true; break; }
                }
                if (!(bypass || allowedByName || allowedByPerm)) {
                    MessageUtils.send(player, plugin.getMessage("server.maintenance"));
                    return false;
                }
            }

            // Deny имеет приоритет (ноды пермишенов)
            for (String perm : rule.denyPerms) {
                if (perm != null && !perm.isEmpty() && player.hasPermission(perm)) {
                    MessageUtils.send(player, plugin.getMessage("general.no-permission"));
                    return false;
                }
            }

            // Если указан allow-пул как пермишены и имена, требуем совпадение хотя бы по одному, когда список не пуст
            if (rule.allowPerms != null && !rule.allowPerms.isEmpty()) {
                boolean ok = false;
                for (String val : rule.allowPerms) {
                    if (val == null || val.isEmpty()) continue;
                    if (player.getName().equalsIgnoreCase(val) || player.hasPermission(val)) { ok = true; break; }
                }
                if (!ok) {
                    MessageUtils.send(player, plugin.getMessage("general.no-permission"));
                    return false;
                }
            }
        }

        // Проверка allowed_servers (получено из Velocity через loma:accounts)
        if (!"lobby".equalsIgnoreCase(server)) {
            java.util.List<String> allowed = plugin.getPlayerAllowedServers(player.getUniqueId());
            boolean allowAll = allowed.stream().anyMatch(s -> s.equalsIgnoreCase("*") || s.equalsIgnoreCase("all"));
            boolean allowedMatch = allowAll || allowed.stream().anyMatch(s -> s.equalsIgnoreCase(server));
            if (!allowedMatch) {
                MessageUtils.send(player, plugin.getMessage("general.no-permission"));
                return false;
            }
        }

        return true;
    }

    private static class ServerRule {
        String name;
        boolean open;
        String connectCommand; // optional console command
        List<String> allowPerms = java.util.Collections.emptyList();
        List<String> denyPerms = java.util.Collections.emptyList();
    }

    private void loadMenus() {
        // Создание меню из внешних файлов
        createServerSelectorMenu();
        createProfileMenu();
        createCosmeticsMenu();
        createParticlesMenus();
        createHatsMenus();
        createSettingsMenu();
    }

    private void createServerSelectorMenu() {
        File menusDir = new File(plugin.getDataFolder(), "menus");
        if (!menusDir.exists()) menusDir.mkdirs();
        File serversFile = new File(menusDir, "servers.yml");
        if (!serversFile.exists()) {
            plugin.saveResource("menus/servers.yml", false);
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(serversFile);
        String title = resolveText(cfg.getString("title", "&8Server Selector"));
        int size = cfg.getInt("size", 27);
        Inventory inventory = Bukkit.createInventory(null, size, title);

        // fill-item
        if (cfg.getBoolean("fill-item.enabled", true)) {
            Material mat = safeMaterial(cfg.getString("fill-item.material", "GRAY_STAINED_GLASS_PANE"));
            String name = cfg.getString("fill-item.name", " ");
            ItemStack filler = new ItemBuilder(mat).setName(MessageUtils.color(name)).build();
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, filler);
            }
        }

        // servers
        ConfigurationSection servers = cfg.getConfigurationSection("servers");
        if (servers != null) {
            for (String key : servers.getKeys(false)) {
                ConfigurationSection s = servers.getConfigurationSection(key);
                if (s == null) continue;
                int slot = s.getInt("slot", -1);
                if (slot < 0 || slot >= inventory.getSize()) continue;
                Material material = safeMaterial(s.getString("material", "GRASS_BLOCK"));
                String name = resolveText(s.getString("name", key));
                List<String> lore = s.getStringList("lore").stream().map(this::resolveText).collect(Collectors.toList());
                String serverName = s.getString("server", key);

                ItemStack item = new ItemBuilder(material)
                        .setName(name)
                        .setLore(lore)
                        .addNBTTag("action", "server:" + serverName)
                        .build();
                inventory.setItem(slot, item);

                // Load rule info
                ServerRule rule = new ServerRule();
                rule.name = serverName;
                rule.open = s.getBoolean("open", true);
                rule.connectCommand = s.getString("connect-command", null);
                rule.allowPerms = s.getStringList("allow-permissions");
                rule.denyPerms = s.getStringList("deny-permissions");
                serverRules.put(serverName.toLowerCase(), rule);
            }
        }

        // close-button
        if (cfg.getBoolean("close-button.enabled", true)) {
            int slot = cfg.getInt("close-button.slot", inventory.getSize() - 1);
            Material mat = safeMaterial(cfg.getString("close-button.material", "BARRIER"));
            String name = resolveText(cfg.getString("close-button.name", "&cClose"));
            inventory.setItem(slot, new ItemBuilder(mat)
                    .setName(name)
                    .addNBTTag("action", "close")
                    .build());
        }

        menus.put("servers", new MenuData("servers", title,  size, inventory));
    }

    private void createProfileMenu() {
        File menusDir = new File(plugin.getDataFolder(), "menus");
        if (!menusDir.exists()) menusDir.mkdirs();
        File profileFile = new File(menusDir, "profile.yml");
        if (!profileFile.exists()) plugin.saveResource("menus/profile.yml", false);

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(profileFile);

        String title = resolveText(cfg.getString("title", "&8Your Profile"));
        int size = cfg.getInt("size", 54);
        Inventory inventory = Bukkit.createInventory(null, size, title);

        // Fill
        if (cfg.getBoolean("fill-item.enabled", true)) {
            Material mat = safeMaterial(cfg.getString("fill-item.material", "BLACK_STAINED_GLASS_PANE"));
            String name = cfg.getString("fill-item.name", " ");
            ItemStack filler = new ItemBuilder(mat).setName(MessageUtils.color(name)).build();
            for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
        }

        // Static shell; dynamic content will be set on open in updateProfileMenu()
        menus.put("profile", new MenuData("profile", title, size, inventory));
    }

    private void createCosmeticsMenu() {
        File menusDir = new File(plugin.getDataFolder(), "menus");
        if (!menusDir.exists()) menusDir.mkdirs();
        File cosmeticsFile = new File(menusDir, "cosmetics.yml");
        if (!cosmeticsFile.exists()) {
            // скопируем дефолт из ресурсов
            plugin.saveResource("menus/cosmetics.yml", false);
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(cosmeticsFile);

        String titleRaw = cfg.getString("title", "&8Cosmetics");
        String title = resolveText(titleRaw);
        int size = cfg.getInt("size", 54);
        Inventory inventory = Bukkit.createInventory(null, size, title);

        // fill-item
        if (cfg.getBoolean("fill-item.enabled", true)) {
            Material mat = safeMaterial(cfg.getString("fill-item.material", "PURPLE_STAINED_GLASS_PANE"));
            String name = cfg.getString("fill-item.name", " ");
            ItemStack filler = new ItemBuilder(mat).setName(MessageUtils.color(name)).build();
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, filler);
            }
        }

        // categories
        ConfigurationSection cats = cfg.getConfigurationSection("categories");
        if (cats != null) {
            for (String key : cats.getKeys(false)) {
                ConfigurationSection s = cats.getConfigurationSection(key);
                if (s == null) continue;
                int slot = s.getInt("slot", -1);
                if (slot < 0 || slot >= inventory.getSize()) continue;
                Material material = safeMaterial(s.getString("material", "STONE"));
                String name = resolveText(s.getString("name", key));
                List<String> loreList = s.getStringList("lore");
                List<String> lore = loreList.stream().map(this::resolveText).collect(Collectors.toList());
                String action = s.getString("action", "");

                ItemStack item = new ItemBuilder(material)
                        .setName(name)
                        .setLore(lore)
                        .addNBTTag("action", action)
                        .build();
                inventory.setItem(slot, item);
            }
        }

        // close-button
        if (cfg.getBoolean("close-button.enabled", true)) {
            int slot = cfg.getInt("close-button.slot", inventory.getSize() - 1);
            Material mat = safeMaterial(cfg.getString("close-button.material", "BARRIER"));
            String name = resolveText(cfg.getString("close-button.name", "&cClose"));
            inventory.setItem(slot, new ItemBuilder(mat)
                    .setName(name)
                    .addNBTTag("action", "close")
                    .build());
        }

        menus.put("cosmetics", new MenuData("cosmetics", title, size, inventory));
    }

    private void createParticlesMenus() {
        File menusDir = new File(plugin.getDataFolder(), "menus");
        if (!menusDir.exists()) menusDir.mkdirs();

        // Новая схема: один конфиг menus/particles.yml с секцией pages (поддержка альтернативного пути menus/particles/particles.yml)
        File combined = new File(menusDir, "particles.yml");
        File combinedAlt = new File(menusDir, "particles/particles.yml");
        File combinedTest = new File(menusDir, "test_particles.yml");
        if (!combined.exists() && plugin.getResource("menus/particles.yml") != null) {
            plugin.saveResource("menus/particles.yml", false);
        }
        if (!combined.exists() && !combinedAlt.exists() && plugin.getResource("menus/particles/particles.yml") != null) {
            plugin.saveResource("menus/particles/particles.yml", false);
        }
        if (!combined.exists() && !combinedAlt.exists() && !combinedTest.exists() && plugin.getResource("menus/test_particles.yml") != null) {
            plugin.saveResource("menus/test_particles.yml", false);
        }
        if (combined.exists() || combinedAlt.exists() || combinedTest.exists()) {
            File src = combined.exists() ? combined : (combinedAlt.exists() ? combinedAlt : combinedTest);
            FileConfiguration root = YamlConfiguration.loadConfiguration(src);
            ConfigurationSection pages = root.getConfigurationSection("pages");
            if (pages != null && !pages.getKeys(false).isEmpty()) {
                // Собираем элементы по страницам
                java.util.Map<Integer, java.util.List<ConfigurationSection>> byPage = new java.util.TreeMap<>();
                for (String key : pages.getKeys(false)) {
                    ConfigurationSection sec = pages.getConfigurationSection(key);
                    if (sec == null) continue;
                    int page = sec.getInt("page", 1);
                    byPage.computeIfAbsent(page, k -> new java.util.ArrayList<>()).add(sec);
                }

                int total = byPage.isEmpty() ? 1 : byPage.keySet().stream().mapToInt(i -> i).max().orElse(1);
                String title = resolveText(root.getString("title", "&8Частицы"));
                int size = Math.max(27, Math.min(54, root.getInt("size", 54)));
                String layout = root.getString("layout", "grid").toLowerCase(); // grid|spaced
                boolean fillEnabled = root.getBoolean("fill-item.enabled", true);
                Material fillMat = safeMaterial(root.getString("fill-item.material", "PURPLE_STAINED_GLASS_PANE"));
                String fillName = root.getString("fill-item.name", " ");
                ItemStack filler = new ItemBuilder(fillMat).setName(MessageUtils.color(fillName)).build();

                for (int page = 1; page <= total; page++) {
                    String pageTitle = title + MessageUtils.color(" &7(") + page + "/" + total + MessageUtils.color(")");
                    Inventory inv = Bukkit.createInventory(null, size, pageTitle);
                    if (fillEnabled) for (int s = 0; s < inv.getSize(); s++) inv.setItem(s, filler);

                    java.util.List<ItemStack> particleItems = new java.util.ArrayList<>();
                    java.util.List<ConfigurationSection> defs = byPage.getOrDefault(page, java.util.Collections.emptyList());
                    for (ConfigurationSection def : defs) {
                        ConfigurationSection items = def.getConfigurationSection("items");
                        if (items == null) continue;
                        for (String itKey : items.getKeys(false)) {
                            ConfigurationSection s = items.getConfigurationSection(itKey);
                            if (s == null) continue;
                            Material mat = safeMaterial(s.getString("material", "BLAZE_POWDER"));
                            String iname = resolveText(s.getString("name", itKey));
                            java.util.List<String> lore = resolveTextList(s.getStringList("lore"));
                            // Добавляем стандартные строки: пустая и подсказка «Нажмите, чтобы выбрать»
                            java.util.List<String> loreWithHint = new java.util.ArrayList<>(lore);
                            loreWithHint.add("");
                            loreWithHint.add(resolveText("&eНажмите, чтобы выбрать"));
                            String animation = s.getString("animation", "circle");
                            String particle = s.getString("particle", "FIREWORK");
                            // Соберём дополнительные параметры прямо из айтема (кроме стандартных ключей)
                            java.util.Set<String> exclude = new java.util.HashSet<>(java.util.Arrays.asList(
                                    "slot","material","name","lore","animation","particle","id"));
                            StringBuilder psb = new StringBuilder();
                            for (String k : s.getKeys(false)) {
                                if (exclude.contains(k)) continue;
                                Object v = s.get(k);
                                if (v == null) continue;
                                if (psb.length() > 0) psb.append(',');
                                psb.append(k).append('=').append(String.valueOf(v));
                            }
                            String action = "particle:" + animation + ":" + particle + (psb.length() > 0 ? ":" + psb.toString() : "");
                            particleItems.add(new ItemBuilder(mat).setName(iname).setLore(loreWithHint).addNBTTag("action", action).build());
                        }
                    }

                    // Ограничение количества элементов на страницу
                    int maxPerPage = root.getInt("max-per-page", 8);
                    if (particleItems.size() > maxPerPage) {
                        particleItems = new java.util.ArrayList<>(particleItems.subList(0, maxPerPage));
                    }

                    java.util.List<Integer> contentSlots =
                            ("spaced".equals(layout) ? computeSpacedSlots(size) : computeFilledSlots(size));
                    for (int idx = 0; idx < particleItems.size() && idx < contentSlots.size(); idx++) {
                        inv.setItem(contentSlots.get(idx), particleItems.get(idx));
                    }

                    // off button
                    if (root.isConfigurationSection("off-button")) {
                        ConfigurationSection off = root.getConfigurationSection("off-button");
                        int oslot = off.getInt("slot", size - 1);
                        ItemStack offItem = new ItemBuilder(safeMaterial(off.getString("material", "BARRIER")))
                                .setName(resolveText(off.getString("name", "&c&lВыключить")))
                                .setLore(resolveTextList(off.getStringList("lore")))
                                .addNBTTag("action", "particle:off")
                                .build();
                        inv.setItem(oslot, offItem);
                    }

                    // навигация
                    int prevSlot = size - 9;
                    int nextSlot = size - 1;
                    if (page > 1) {
                        inv.setItem(prevSlot, new ItemBuilder(Material.ARROW)
                                .setName(plugin.getMessage("menu.prev"))
                                .addNBTTag("action", "menu:particles" + (page - 1 == 1 ? "" : ":" + (page - 1)))
                                .build());
                    } else inv.setItem(prevSlot, filler);
                    if (page < total) {
                        inv.setItem(nextSlot, new ItemBuilder(Material.ARROW).setName(plugin.getMessage("menu.next")).addNBTTag("action", "menu:particles:" + (page + 1)).build());
                    } else inv.setItem(nextSlot, filler);

                    int backSlot = size - 5;
                    inv.setItem(backSlot, new ItemBuilder(Material.BARRIER).setName(plugin.getMessage("menu.back")).addNBTTag("action", "menu:cosmetics").build());

                    String key = page == 1 ? "particles" : ("particles:" + page);
                    menus.put(key, new MenuData(key, pageTitle, size, inv));
                }
                return;
            }
        }

        // Legacy: папка menus/particles с файлами страниц
        File pagesDir = new File(menusDir, "particles");
        if (!pagesDir.exists()) {
            pagesDir.mkdirs();
            if (plugin.getResource("menus/particles/first_page.yml") != null) {
                plugin.saveResource("menus/particles/first_page.yml", false);
            }
        }

        java.util.List<File> filesList = new java.util.ArrayList<>();
        try {
            java.nio.file.Files.walk(pagesDir.toPath())
                    .filter(p -> java.nio.file.Files.isRegularFile(p) && (p.getFileName().toString().toLowerCase().endsWith(".yml") || p.getFileName().toString().toLowerCase().endsWith(".yaml")))
                    .forEach(p -> filesList.add(p.toFile()));
        } catch (Exception ignored) {}
        if (filesList.isEmpty()) {
            MessageUtils.sendConsole("&e[LoMa] Particles pages not found in menus/particles");
            return;
        }
        filesList.sort(java.util.Comparator.comparing(File::getName));

        int total = filesList.size();
        for (int i = 0; i < filesList.size(); i++) {
            File f = filesList.get(i);
            int page = i + 1;
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            String title = resolveText(cfg.getString("title", "&8Particles")) + MessageUtils.color(" &7(") + page + "/" + total + MessageUtils.color(")");
            int size = Math.max(27, Math.min(54, cfg.getInt("size", 54)));
            Inventory inv = Bukkit.createInventory(null, size, title);

            boolean fillEnabled = cfg.getBoolean("fill-item.enabled", true);
            Material fillMat = safeMaterial(cfg.getString("fill-item.material", "PURPLE_STAINED_GLASS_PANE"));
            String fillName = cfg.getString("fill-item.name", " ");
            ItemStack filler = new ItemBuilder(fillMat).setName(MessageUtils.color(fillName)).build();
            if (fillEnabled) for (int s = 0; s < inv.getSize(); s++) inv.setItem(s, filler);

            java.util.List<ItemStack> particleItems = new java.util.ArrayList<>();
            ConfigurationSection items = cfg.getConfigurationSection("items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    ConfigurationSection s = items.getConfigurationSection(key);
                    if (s == null) continue;
                    Material mat = safeMaterial(s.getString("material", "BLAZE_POWDER"));
                    String name = resolveText(s.getString("name", key));
                    List<String> lore = resolveTextList(s.getStringList("lore"));
                    // Добавляем стандартные строки: пустая и подсказка «Нажмите, чтобы выбрать»
                    List<String> loreWithHint = new java.util.ArrayList<>(lore);
                    loreWithHint.add("");
                    loreWithHint.add(resolveText("&eНажмите, чтобы выбрать"));
                    String animation = s.getString("animation", "circle");
                    String particle = s.getString("particle", "FIREWORK");
                    // собрать доп.параметры
                    java.util.Set<String> exclude = new java.util.HashSet<>(java.util.Arrays.asList(
                            "slot","material","name","lore","animation","particle","id"));
                    StringBuilder psb = new StringBuilder();
                    for (String k : s.getKeys(false)) {
                        if (exclude.contains(k)) continue;
                        Object v = s.get(k);
                        if (v == null) continue;
                        if (psb.length() > 0) psb.append(',');
                        psb.append(k).append('=').append(String.valueOf(v));
                    }
                    String action = "particle:" + animation + ":" + particle + (psb.length() > 0 ? ":" + psb.toString() : "");
                    particleItems.add(new ItemBuilder(mat).setName(name).setLore(loreWithHint).addNBTTag("action", action).build());
                }
            }

            // Ограничение количества элементов на страницу (legacy)
            int maxPerPage = cfg.getInt("max-per-page", 8);
            if (particleItems.size() > maxPerPage) {
                particleItems = new java.util.ArrayList<>(particleItems.subList(0, maxPerPage));
            }
            java.util.List<Integer> contentSlots = computeSpacedSlots(size);
            for (int idx = 0; idx < particleItems.size() && idx < contentSlots.size(); idx++) inv.setItem(contentSlots.get(idx), particleItems.get(idx));

            if (cfg.isConfigurationSection("off-button")) {
                ConfigurationSection off = cfg.getConfigurationSection("off-button");
                int oslot = off.getInt("slot", size - 1);
                ItemStack offItem = new ItemBuilder(safeMaterial(off.getString("material", "BARRIER")))
                        .setName(resolveText(off.getString("name", "&c&lВыключить")))
                        .setLore(resolveTextList(off.getStringList("lore")))
                        .addNBTTag("action", "particle:off").build();
                inv.setItem(oslot, offItem);
            }

            int prevSlot = size - 9;
            int nextSlot = size - 1;
            if (page > 1) inv.setItem(prevSlot, new ItemBuilder(Material.ARROW).setName(plugin.getMessage("menu.prev")).addNBTTag("action", "menu:particles" + (page - 1 == 1 ? "" : ":" + (page - 1))).build()); else inv.setItem(prevSlot, filler);
            if (page < total) inv.setItem(nextSlot, new ItemBuilder(Material.ARROW).setName(plugin.getMessage("menu.next")).addNBTTag("action", "menu:particles:" + (page + 1)).build()); else inv.setItem(nextSlot, filler);

            int backSlot = size - 5;
            inv.setItem(backSlot, new ItemBuilder(Material.BARRIER).setName(plugin.getMessage("menu.back")).addNBTTag("action", "menu:cosmetics").build());

            String key = page == 1 ? "particles" : ("particles:" + page);
            menus.put(key, new MenuData(key, title, size, inv));
        }
    }

    private void createSettingsMenu() {
        File menusDir = new File(plugin.getDataFolder(), "menus");
        if (!menusDir.exists()) menusDir.mkdirs();
        File file = new File(menusDir, "settings.yml");
        if (!file.exists()) plugin.saveResource("menus/settings.yml", false);

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String title = resolveText(cfg.getString("title", "&8Настройки"));
        int size = cfg.getInt("size", 27);
        Inventory inv = Bukkit.createInventory(null, size, title);

        // fill
        if (cfg.getBoolean("fill-item.enabled", true)) {
            Material mat = safeMaterial(cfg.getString("fill-item.material", "BLACK_STAINED_GLASS_PANE"));
            String name = cfg.getString("fill-item.name", " ");
            ItemStack filler = new ItemBuilder(mat).setName(MessageUtils.color(name)).build();
            for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        }

        // items (placeholders for {state} will be filled on open)
        ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection s = items.getConfigurationSection(key);
                if (s == null) continue;
                int slot = s.getInt("slot", -1);
                if (slot < 0 || slot >= inv.getSize()) continue;
                Material mat = safeMaterial(s.getString("material", "STONE"));
                String name = resolveText(s.getString("name", key));
                List<String> lore = resolveTextList(s.getStringList("lore"));
                String action = s.getString("action", "");
                // ВАЖНО: Тогглы (toggle:*, toggleall) динамически расставляются в updateSettingsMenu(),
                // чтобы избежать дублирования (например, видимость игроков). Поэтому пропускаем их здесь.
                if (action != null && (action.equalsIgnoreCase("toggleall") || action.toLowerCase().startsWith("toggle:"))) {
                    continue;
                }
                ItemStack item = new ItemBuilder(mat).setName(name).setLore(lore).addNBTTag("action", action).build();
                inv.setItem(slot, item);
            }
        }

        // close
        if (cfg.getBoolean("close-button.enabled", true)) {
            int slot = cfg.getInt("close-button.slot", size - 1);
            Material mat = safeMaterial(cfg.getString("close-button.material", "BARRIER"));
            String name = resolveText(cfg.getString("close-button.name", "&cЗакрыть"));
            inv.setItem(slot, new ItemBuilder(mat).setName(name).addNBTTag("action", "close").build());
        }

        menus.put("settings", new MenuData("settings", title, size, inv));
    }

    private Material safeMaterial(String name) {
        try { return Material.valueOf(name.toUpperCase()); } catch (Exception e) { return Material.STONE; }
    }

    private String resolveText(String raw) {
        if (raw == null) return "";
        String base;
        // Если путь существует и это строка в локализации — подставляем её, иначе используем как есть
        if (plugin.getMessagesConfig() != null
                && plugin.getMessagesConfig().contains(raw)
                && plugin.getMessagesConfig().isString(raw)) {
            base = plugin.getMessagesConfig().getString(raw);
        } else {
            base = raw;
        }
        // Поддержка градиентов и hex-тегов: <gradient:#FFFF00:#FFA500>Text</gradient>, <#FFAA00>
        return com.loma.plugin.utils.GradientUtils.applyGradient(base);
    }

    private List<String> resolveTextList(List<String> list) {
        return list == null ? java.util.Collections.emptyList() : list.stream().map(this::resolveText).collect(java.util.stream.Collectors.toList());
    }

    // Удален лобби-селектор — единственное лобби не требует выбора

    public void openMenu(Player player, String menuName) {
        MenuData menu = menus.get(menuName.toLowerCase());

        if (menu == null) {
            MessageUtils.send(player, plugin.getMessage("menu.not-found")
                    .replace("{menu}", menuName));
            return;
        }

        // Создаем копию инвентаря шаблона, чтобы персонализировать для игрока
        Inventory inventory = Bukkit.createInventory(new MenuHolder(menuName.toLowerCase()), menu.getSize(), menu.getTitle());
        inventory.setContents(menu.getInventory().getContents());

        // Специальные обновления для динамических меню
        if (menuName.equalsIgnoreCase("profile") || menuName.toLowerCase().startsWith("profile:")) {
            if (menuName.toLowerCase().startsWith("profile:")) {
                String arg = menuName.substring("profile:".length());
                java.util.UUID targetUuid = null;
                try { targetUuid = java.util.UUID.fromString(arg); } catch (Exception ignored) {}
                if (targetUuid != null) {
                    updateProfileMenuForTarget(player, inventory, targetUuid);
                } else {
                    // Если передано имя — попросим Velocity и позже перерисуем
                    plugin.requestPlayerProfileByName(player, arg);
                    updateProfileMenu(player, inventory);
                }
            } else {
                updateProfileMenu(player, inventory);
            }
        } else if (menuName.equalsIgnoreCase("settings")) {
            updateSettingsMenu(player, inventory);
        }

        player.openInventory(inventory);
        MessageUtils.send(player, plugin.getMessage("menu.opening")
                .replace("{menu}", menuName));
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
    }

    // Открытие меню достижений
    public void openAchievements(Player player) {
        if (plugin.getAchievementsManager() == null) {
            MessageUtils.send(player, "&cСистема достижений недоступна.");
            return;
        }
        String title = MessageUtils.color(
                plugin.getMessagesConfig() != null && plugin.getMessagesConfig().contains("achievements.menu.title")
                        ? plugin.getMessagesConfig().getString("achievements.menu.title")
                        : "&8Достижения"
        );
        int size = 54;
        Inventory inventory = Bukkit.createInventory(new MenuHolder("achievements"), size, title);

        // Фон
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setName(" ").build();
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);

        // Элементы достижений
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        for (com.loma.plugin.achievements.Achievement a : plugin.getAchievementsManager().listAll()) {
            items.add(plugin.getAchievementsManager().buildIcon(player, a));
        }

        java.util.List<Integer> contentSlots = computeSpacedSlots(size);
        for (int i = 0; i < items.size() && i < contentSlots.size(); i++) {
            inventory.setItem(contentSlots.get(i), items.get(i));
        }

        // Назад в профиль (центр нижнего ряда)
        int backSlot = size - 5;
        inventory.setItem(backSlot, new ItemBuilder(Material.BARRIER)
                .setName(plugin.getMessage("menu.back"))
                .addNBTTag("action", "menu:profile")
                .build());

        // Кнопка Закрыть (последний слот)
        inventory.setItem(size - 1, new ItemBuilder(Material.BOOK)
                .setName(plugin.getMessage("menu.close"))
                .addNBTTag("action", "close")
                .build());

        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
    }

    private void updateProfileMenu(Player player, Inventory inventory) {
        LoMa.PlayerStatsCache cache = plugin.getStatsCache(player.getUniqueId());
        if (cache == null) {
            try { plugin.requestPlayerStats(player, player.getUniqueId()); } catch (Exception ignored) {}
        }
        String rank = cache != null ? cache.rank : plugin.getMessage("scoreboard.unknown");
        String playtime = cache != null ? cache.getFormattedPlaytime() : "...";

        // Загрузка макета из файла профиля
        File profileFile = new File(plugin.getDataFolder(), "menus/profile.yml");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(profileFile);

        // Голова игрока: строим лор с добавлением статуса и текущего сервера
        java.util.List<String> headRaw = cfg.getStringList("items.head.lore");
        if (headRaw == null || headRaw.isEmpty()) headRaw = java.util.Arrays.asList("&7Время в сети: &e{playtime}");
        // Сначала применяем локализацию и подстановки для конфиг-строк
        java.util.List<String> cfgLore = headRaw.stream()
                .map(this::resolveText)
                .map(s -> s.replace("{playtime}", playtime))
                .collect(java.util.stream.Collectors.toList());
        // Затем фильтруем строки с рангом
        cfgLore = cfgLore.stream()
                .filter(s -> !s.toLowerCase().contains("{rank}") && !s.toLowerCase().contains("ранг") && !s.toLowerCase().contains("rank"))
                .collect(java.util.stream.Collectors.toList());

        java.util.List<String> headLore = new java.util.ArrayList<>();
        headLore.add(plugin.getMessage("profile.status.online"));
        String srv = plugin.getConfig().getString("bungeecord.server-name", "Server");
        headLore.add(plugin.getMessage("profile.server.current").replace("{server}", srv));
        headLore.addAll(cfgLore);

        inventory.setItem(13, new ItemBuilder(Material.PLAYER_HEAD)
                .setSkullOwner(player.getName())
                .setName(resolveText(cfg.getString("items.head.name", "&a&l{player}")).replace("{player}", player.getName()))
                .setLore(headLore)
                .build());

        // Если кэш был пуст — попробуем обновить меню через небольшой таймер (когда придёт ответ от Velocity)
        if (cache == null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.getOpenInventory() == null) return;
                org.bukkit.inventory.Inventory top = player.getOpenInventory().getTopInventory();
                if (top == null) return;
                org.bukkit.inventory.InventoryHolder holder = top.getHolder();
                if (holder instanceof MenuHolder) {
                    String name = ((MenuHolder) holder).getMenuName();
                    if ("profile".equalsIgnoreCase(name)) {
                        updateProfileMenu(player, top);
                    }
                }
            }, 20L);
        }

        // Статистика
        inventory.setItem(30, new ItemBuilder(safeMaterial(cfg.getString("items.stats.material", "DIAMOND")))
                .setName(resolveText(cfg.getString("items.stats.name", "&b&lСтатистика")))
                .setLore(resolveTextList(cfg.getStringList("items.stats.lore")))
                .addNBTTag("action", "stats")
                .build());

        // Достижения
        int achievements = plugin.getAchievementsManager() != null ? plugin.getAchievementsManager().getCount(player.getUniqueId()) : (cache != null ? cache.achievements : 0);
        List<String> achLore = cfg.getStringList("items.achievements.lore");
        achLore = achLore.isEmpty() ? java.util.Collections.singletonList("&7Ваши достижения: &e{count}") : achLore;
        inventory.setItem(32, new ItemBuilder(safeMaterial(cfg.getString("items.achievements.material", "GOLDEN_APPLE")))
                .setName(resolveText(cfg.getString("items.achievements.name", "&6&lДостижения")))
                .setLore(achLore.stream().map(s -> resolveText(s).replace("{count}", String.valueOf(achievements))).collect(java.util.stream.Collectors.toList()))
                .addNBTTag("action", "menu:achievements")
                .build());

        // Настройки
        inventory.setItem(49, new ItemBuilder(safeMaterial(cfg.getString("items.settings.material", "REDSTONE")))
                .setName(resolveText(cfg.getString("items.settings.name", "&c&lНастройки")))
                .setLore(resolveTextList(cfg.getStringList("items.settings.lore")))
                .addNBTTag("action", cfg.getString("items.settings.action", "menu:settings"))
                .build());
    }

    public void handleClick(Player player, String action) {
        if (action == null) return;

        String[] parts = action.split(":");
        if (parts.length < 1) return;

        switch (parts[0].toLowerCase()) {
            case "close":
                player.closeInventory();
                MessageUtils.send(player, plugin.getMessage("menu.closed"));
                break;

            case "hat":
                if (parts.length >= 2) {
                    String hatId = parts[1];
                    if (hatId.equalsIgnoreCase("off")) {
                        plugin.getCosmeticsManager().clearHat(player.getUniqueId());
                        MessageUtils.send(player, plugin.getMessage("cosmetics.hat.removed"));
                    } else {
                        plugin.getCosmeticsManager().setHat(player.getUniqueId(), hatId);
                        MessageUtils.send(player, plugin.getMessage("cosmetics.hat.set").replace("{hat}", hatId));
                    }
                }
                break;

            case "server":
                if (parts.length > 1) {
                    String target = parts[1];
                    if (!canJoinServer(player, target)) {
                        return;
                    }
                    ServerRule rule = serverRules.getOrDefault(target.toLowerCase(), null);
                    if (rule != null && rule.connectCommand != null && !rule.connectCommand.isEmpty()) {
                        String cmd = rule.connectCommand.replace("{player}", player.getName());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    } else {
                        connectToServer(player, target);
                    }
                }
                break;

            case "menu":
                if (parts.length > 1) {
                    String targetMenu = parts[1];
                    if (parts.length > 2) {
                        targetMenu = parts[1] + ":" + parts[2];
                    }
                    player.closeInventory();
                    String finalTargetMenu = targetMenu;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if ("achievements".equalsIgnoreCase(finalTargetMenu)) {
                            openAchievements(player);
                        } else {
                            openMenu(player, finalTargetMenu);
                        }
                    }, 3L);
                }
                break;

            case "particle":
                if (parts.length >= 2) {
                    if (parts[1].equalsIgnoreCase("off")) {
                        plugin.getCosmeticsManager().clearParticle(player.getUniqueId());
                        MessageUtils.send(player, plugin.getMessage("cosmetics.particles.off"));
                        break;
                    }
                    String animation = parts[1];
                    String particleName = parts.length >= 3 ? parts[2] : "FIREWORK";
                    String paramStr = parts.length >= 4 ? String.join(":", java.util.Arrays.copyOfRange(parts, 3, parts.length)) : null;
                    java.util.Map<String, String> params = parseParams(paramStr);
                    plugin.getCosmeticsManager().setParticle(player.getUniqueId(), animation, particleName, params);
                    // Включаем настройку частиц, чтобы сразу отрисовывалось
                    if (plugin.getPreferencesManager() != null) {
                        plugin.getPreferencesManager().set(player.getUniqueId(), "particles", true);
                    }
                    MessageUtils.send(player, plugin.getMessage("cosmetics.particles.set")
                            .replace("{animation}", animation)
                            .replace("{particle}", particleName));
                }
                break;

            case "command":
                if (parts.length > 1) {
                    String command = String.join(":", java.util.Arrays.copyOfRange(parts, 1, parts.length));
                    Bukkit.dispatchCommand(player, command);
                }
                break;

            case "toggle":
                if (parts.length > 1) {
                    String feature = parts[1].toLowerCase();
                    boolean current = plugin.getPreferencesManager().isEnabled(player.getUniqueId(), feature, true);
                    boolean next = !current;
                    plugin.getPreferencesManager().set(player.getUniqueId(), feature, next);
                    // Применяем эффекты для особых фич
                    if (feature.equals("fly")) {
                        player.setAllowFlight(next);
                        if (!next && player.isFlying()) player.setFlying(false);
                    }
                    updateSettingsMenu(player, player.getOpenInventory().getTopInventory());
                    String state = next ? plugin.getMessage("settings.state.on") : plugin.getMessage("settings.state.off");
                    MessageUtils.send(player, plugin.getMessage("settings.toggle.feature")
                            .replace("{feature}", feature)
                            .replace("{state}", state));
                }
                break;

            case "toggleall":
                {
                    boolean all = allFeaturesEnabled(player.getUniqueId());
                    boolean next = !all;
                    plugin.getPreferencesManager().setAll(player.getUniqueId(), next);
                    // Применить fly сразу
                    player.setAllowFlight(plugin.getPreferencesManager().isEnabled(player.getUniqueId(), "fly", false));
                    if (!player.getAllowFlight() && player.isFlying()) player.setFlying(false);
                    updateSettingsMenu(player, player.getOpenInventory().getTopInventory());
                    MessageUtils.send(player, next ? plugin.getMessage("settings.toggleall.on") : plugin.getMessage("settings.toggleall.off"));
                }
                break;

            case "stats":
            {
                java.util.UUID target = player.getUniqueId();
                Integer page = 1;
                if (parts.length > 1) {
                    try { target = java.util.UUID.fromString(parts[1]); } catch (Exception ignored) {}
                }
                if (parts.length > 2) {
                    try { page = Integer.parseInt(parts[2]); } catch (Exception ignored) {}
                }
                final java.util.UUID finalTarget = target;
                final int finalPage = page == null || page < 1 ? 1 : page;
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> openStatsMenu(player, finalTarget, finalPage), 3L);
            }
            break;
        }
    }

    private void connectToServer(Player player, String server) {
        String current = plugin.getConfig().getString("bungeecord.server-name", "");
        if (current.equalsIgnoreCase(server)) {
            MessageUtils.send(player, plugin.getMessage("server.already-here"));
            return;
        }
        long now = System.currentTimeMillis();
        long cooldownMs = plugin.getConfig().getLong("bungeecord.connect-cooldown-ms", 2000L);
        Long last = lastConnect.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            // Предотвращаем повторную отправку пока ещё идёт подключение
            return;
        }
        lastConnect.put(player.getUniqueId(), now);
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);

        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());

        MessageUtils.send(player, plugin.getMessage("server.connecting")
                .replace("{server}", server));

        // Title при подключении
        MessageUtils.sendTitle(player,
                plugin.getMessage("titles.server-change.title"),
                plugin.getMessage("titles.server-change.subtitle").replace("{server}", server),
                10, 40, 10);
    }

    public boolean isMenu(Inventory inventory) {
        org.bukkit.inventory.InventoryHolder holder = inventory.getHolder();
        return holder instanceof MenuHolder;
    }

    /** Имя меню (key), если это наше меню, иначе null */
    public String getMenuName(Inventory inventory) {
        org.bukkit.inventory.InventoryHolder holder = inventory.getHolder();
        if (holder instanceof MenuHolder) {
            return ((MenuHolder) holder).getMenuName();
        }
        return null;
    }

    // ===== Stats Menu =====
    public void openStatsMenu(Player viewer, java.util.UUID targetUuid) { openStatsMenu(viewer, targetUuid, 1); }

    public void openStatsMenu(Player viewer, java.util.UUID targetUuid, int page) {
        String title = MessageUtils.color("&8Статистика " + (plugin.getPlayerProfile(targetUuid) != null ? plugin.getPlayerProfile(targetUuid).username : viewer.getName()))
                + MessageUtils.color(" &7(") + page + MessageUtils.color(")");
        int size = 54;
        Inventory inventory = Bukkit.createInventory(new MenuHolder("stats:" + targetUuid + ":" + page), size, title);

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(" ").build();
        for (int i = 0; i < size; i++) inventory.setItem(i, filler);

        // Кнопка Назад
        inventory.setItem(size - 5, new ItemBuilder(Material.BARRIER)
                .setName(plugin.getMessage("menu.back"))
                .addNBTTag("action", "menu:profile")
                .build());

        // Первичная отрисовка/загрузка
        updateStatsMenu(viewer, inventory, targetUuid);
        viewer.openInventory(inventory);
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
    }

    public void updateStatsMenu(Player viewer, Inventory inventory, java.util.UUID targetUuid) {
        // Определяем текущую страницу из holder'а
        int page = 1;
        if (inventory.getHolder() instanceof MenuHolder) {
            String name = ((MenuHolder) inventory.getHolder()).getMenuName();
            if (name != null && name.startsWith("stats:")) {
                String[] pp = name.split(":");
                if (pp.length >= 3) {
                    try { page = Integer.parseInt(pp[2]); } catch (Exception ignored) {}
                }
            }
        }

        java.util.Map<String, Long> per = plugin.getPerServerPlaytime(targetUuid);
        if (per == null) {
            plugin.requestPlaytimePerServer(viewer, targetUuid);
            // Поставим заглушку
            inventory.setItem(22, new ItemBuilder(Material.CLOCK).setName(plugin.getMessage("loading.title"))
                    .setLore(java.util.Collections.singletonList(plugin.getMessage("loading.desc"))).build());
            return;
        }

        // Очистим центр
        for (int r = 1; r <= 4; r++) {
            for (int c = 1; c <= 7; c++) {
                int slot = r * 9 + c;
                inventory.setItem(slot, null);
            }
        }

        // Сортировка по убыванию минут
        java.util.List<java.util.Map.Entry<String, Long>> entries = new java.util.ArrayList<>(per.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        long total = entries.stream().mapToLong(java.util.Map.Entry::getValue).sum();

        java.util.List<Integer> slots = computeFilledSlots(inventory.getSize());
        int pageSize = slots.size();
        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) pageSize));
        int clampedPage = Math.max(1, Math.min(page, pages));
        int from = (clampedPage - 1) * pageSize;
        int to = Math.min(entries.size(), from + pageSize);

        for (int i = from, idx = 0; i < to && idx < slots.size(); i++, idx++) {
            String server = entries.get(i).getKey();
            long minutes = entries.get(i).getValue();
            Material icon = getServerIconMaterial(server);
            ItemStack it = new ItemBuilder(icon)
                    .setName(MessageUtils.color("&b" + server))
                    .setLore(java.util.Arrays.asList(
                            MessageUtils.color("&7Наиграно: &e" + com.loma.plugin.playtime.PlaytimeService.formatHours(minutes)),
                            plugin.getMessage("stats.click-to-join")))
                    .addNBTTag("action", "server:" + server)
                    .build();
            inventory.setItem(slots.get(idx), it);
        }

        // Итог
        inventory.setItem(40, new ItemBuilder(Material.BOOK)
                .setName(plugin.getMessage("stats.total.title"))
                .setLore(java.util.Collections.singletonList(
                        plugin.getMessage("stats.total.value").replace("{hours}", com.loma.plugin.playtime.PlaytimeService.formatHours(total))))
                .build());

        // Навигация по страницам
        if (pages > 1) {
            if (clampedPage > 1) {
                inventory.setItem(45, new ItemBuilder(Material.ARROW)
                        .setName(plugin.getMessage("menu.prev"))
                        .addNBTTag("action", "stats:" + targetUuid + ":" + (clampedPage - 1))
                        .build());
            }
            if (clampedPage < pages) {
                inventory.setItem(53, new ItemBuilder(Material.ARROW)
                        .setName(plugin.getMessage("menu.next"))
                        .addNBTTag("action", "stats:" + targetUuid + ":" + (clampedPage + 1))
                        .build());
            }
        }
    }

    private Material getServerIconMaterial(String server) {
        try {
            File file = new File(plugin.getDataFolder(), "menus/servers.yml");
            if (!file.exists()) return Material.PAPER;
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            String path = "server-icons." + server;
            if (cfg.contains(path)) {
                return safeMaterial(cfg.getString(path, "PAPER"));
            }
        } catch (Exception ignored) {}
        return Material.PAPER;
    }

    private static class MenuData {
        private final String name;
        private final String title;
        private final int size;
        private final Inventory inventory; // template

        public MenuData(String name, String title, int size, Inventory inventory) {
            this.name = name;
            this.title = title;
            this.size = size;
            this.inventory = inventory;
        }

        public String getName() { return name; }
        public String getTitle() { return title; }
        public int getSize() { return size; }
        public Inventory getInventory() { return inventory; }
    }
}