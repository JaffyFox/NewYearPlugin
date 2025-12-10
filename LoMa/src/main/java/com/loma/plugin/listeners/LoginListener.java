package com.loma.plugin.listeners;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LoginListener implements Listener {

    private final LoMa plugin;
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    public LoginListener(LoMa plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String username = player.getName();
        
        // Скрываем HoloBot из таба и сообщений
        if (username.equalsIgnoreCase("HoloBot")) {
            event.setJoinMessage(null);
            // Устанавливаем спектатор режим и скрываем от всех игроков
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.setGameMode(GameMode.SPECTATOR);
                    // Скрываем бота от всех игроков
                    for (Player p : plugin.getServer().getOnlinePlayers()) {
                        if (!p.equals(player)) {
                            p.hidePlayer(plugin, player);
                        }
                    }
                }
            }, 1L);
            plugin.setPlayerAuthorized(player.getUniqueId(), true);
            return;
        }

        // Временно помечаем игрока как НЕ авторизованного (блокируем все действия)
        plugin.setPlayerAuthorized(player.getUniqueId(), false);
        
        // Скрываем HoloBot от этого игрока (если бот уже на сервере)
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase("HoloBot")) {
                player.hidePlayer(plugin, p);
            }
        }
        
        // Асинхронная проверка аккаунта
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.checkAccountExists(player, (exists, autoLogin) -> {
                if (!player.isOnline()) return;
                
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    
                    if (!exists) {
                        // Аккаунт не найден - моментально кикаем
                        player.kickPlayer(MessageUtils.color(
                            "&c&lДоступ запрещён\n\n" +
                            "&7У вас нет аккаунта на сервере.\n" +
                            "&7Создайте заявку в Discord:\n" +
                            "&f/minecraft nick add\n\n" +
                            "&eЕсли у вас нет доступа к Discord серверу -\n" +
                            "&eобратитесь к администратору сервера."
                        ));
                    } else if (autoLogin) {
                        // Авто-логин по IP
                        plugin.setPlayerAuthorized(player.getUniqueId(), true);
                        MessageUtils.send(player, "&aВы автоматически авторизованы по IP!");
                    } else {
                        // Требуется ввести пароль (оставляем неавторизованным)
                        // Сначала приветствие
                        MessageUtils.send(player, "&e&lДобро пожаловать!");
                        MessageUtils.sendTitle(player, "&eДобро пожаловать!", "&7Рады видеть вас в лобби");
                        // Затем через несколько секунд подсказка для логина
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (!player.isOnline() || plugin.isPlayerAuthorized(player.getUniqueId())) return;
                            MessageUtils.send(player, "&7Для продолжения введите: &f/login <пароль>");
                            MessageUtils.sendTitle(player, "&eАвторизация", "&7Введите /login <пароль>");
                        }, 60L);
                    }
                });
            });
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Скрываем HoloBot из таба
        if (player.getName().equalsIgnoreCase("HoloBot")) {
            event.setQuitMessage(null);
        }
        
        // Очищаем статус авторизации
        plugin.setPlayerAuthorized(player.getUniqueId(), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();

        // Пропускаем команды для HoloBot
        if (player.getName().equalsIgnoreCase("HoloBot")) {
            return;
        }

        // Если игрок не авторизован, блокируем все команды кроме алиасов логина
        if (!plugin.isPlayerAuthorized(player.getUniqueId())) {
            String base = command.split(" ")[0];
            boolean allowed = base.equals("/login") || base.equals("/l") || base.equals("/auth")
                    || base.equals("/л") || base.equals("/логин") || base.equals("/войти");
            if (!allowed) {
                event.setCancelled(true);
                MessageUtils.send(player, "&cСначала авторизуйтесь: &f/login <пароль>");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        // Пропускаем HoloBot
        if (player.getName().equalsIgnoreCase("HoloBot")) {
            return;
        }
        
        // Блокируем движение для неавторизованных игроков + редкое предупреждение
        if (!plugin.isPlayerAuthorized(player.getUniqueId())) {
            event.setCancelled(true);
            long now = System.currentTimeMillis();
            Long last = lastWarn.get(player.getUniqueId());
            if (last == null || (now - last) > 3000) {
                lastWarn.put(player.getUniqueId(), now);
                MessageUtils.send(player, "&cСначала авторизуйтесь: &f/login <пароль>");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        // Пропускаем HoloBot
        if (player.getName().equalsIgnoreCase("HoloBot")) {
            return;
        }
        
        // Блокируем взаимодействие для неавторизованных игроков (с предупреждением раз в 3с)
        if (!plugin.isPlayerAuthorized(player.getUniqueId())) {
            event.setCancelled(true);
            long now = System.currentTimeMillis();
            Long last = lastWarn.get(player.getUniqueId());
            if (last == null || (now - last) > 3000) {
                lastWarn.put(player.getUniqueId(), now);
                MessageUtils.send(player, "&cСначала авторизуйтесь: &f/login <пароль>");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        
        // Пропускаем HoloBot
        if (player.getName().equalsIgnoreCase("HoloBot")) {
            return;
        }
        
        // Блокируем чат для неавторизованных игроков
        if (!plugin.isPlayerAuthorized(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                MessageUtils.send(player, "&cСначала авторизуйтесь: &f/login <пароль>");
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        
        // Пропускаем HoloBot
        if (player.getName().equalsIgnoreCase("HoloBot")) {
            return;
        }
        
        // Блокируем инвентарь для неавторизованных игроков
        if (!plugin.isPlayerAuthorized(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        
        // Пропускаем HoloBot
        if (player.getName().equalsIgnoreCase("HoloBot")) {
            return;
        }
        
        // Блокируем открытие инвентарей для неавторизованных игроков
        if (!plugin.isPlayerAuthorized(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        
        // Пропускаем HoloBot
        if (player.getName().equalsIgnoreCase("HoloBot")) {
            return;
        }
        
        // Блокируем выбрасывание предметов для неавторизованных игроков
        if (!plugin.isPlayerAuthorized(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
