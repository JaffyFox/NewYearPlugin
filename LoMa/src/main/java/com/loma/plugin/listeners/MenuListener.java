package com.loma.plugin.listeners;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class MenuListener implements Listener {

    private final LoMa plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey lobbyItemKey;
    private final NamespacedKey hatKey;

    public MenuListener(LoMa plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "action");
        this.lobbyItemKey = new NamespacedKey(plugin, "lobbyitem");
        this.hatKey = new NamespacedKey(plugin, "hat");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();

        if (item == null || !item.hasItemMeta()) {
            // также проверим предмет на курсоре
            ItemStack cursor = event.getCursor();
            if (cursor == null || !cursor.hasItemMeta()) {
                return;
            }
            ItemMeta cmeta = cursor.getItemMeta();
            PersistentDataContainer cdata = cmeta.getPersistentDataContainer();
            if (cdata.has(lobbyItemKey, PersistentDataType.STRING) || cdata.has(hatKey, PersistentDataType.STRING)) {
                event.setCancelled(true);
            }
            return;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Блокировка перемещения предметов лобби в любом инвентаре
        if (container.has(lobbyItemKey, PersistentDataType.STRING) || container.has(hatKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
            return;
        }

        // Проверка, является ли это одним из наших меню
        if (!plugin.getMenuManager().isMenu(event.getInventory())) {
            return;
        }

        event.setCancelled(true);

        // Получение действия
        if (container.has(actionKey, PersistentDataType.STRING)) {
            String action = container.get(actionKey, PersistentDataType.STRING);

            // Звук клика
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

            // Обработка действия
            plugin.getMenuManager().handleClick(player, action);
        }
    }

    @org.bukkit.event.EventHandler
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        // Если среди перетаскиваемых есть предмет с меткой lobbyitem — отменить
        ItemStack oldCursor = event.getOldCursor();
        if (oldCursor != null && oldCursor.hasItemMeta()) {
            ItemMeta meta = oldCursor.getItemMeta();
            if (meta != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                if (pdc.has(lobbyItemKey, PersistentDataType.STRING) || pdc.has(hatKey, PersistentDataType.STRING)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @org.bukkit.event.EventHandler
    public void onDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (stack != null && stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                if (pdc.has(lobbyItemKey, PersistentDataType.STRING) || pdc.has(hatKey, PersistentDataType.STRING)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();

        // Звук закрытия
        if (plugin.getMenuManager().isMenu(event.getInventory())) {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.0f);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Проверка, является ли это предметом лобби
        if (!container.has(lobbyItemKey, PersistentDataType.STRING)) {
            return;
        }

        event.setCancelled(true);

        // Получение действия
        if (container.has(actionKey, PersistentDataType.STRING)) {
            String action = container.get(actionKey, PersistentDataType.STRING);

            // Специальная обработка для переключения видимости
            if (action.equals("visibility")) {
                PlayerJoinListener joinListener = getJoinListener();
                if (joinListener != null) {
                    joinListener.togglePlayerVisibility(player);

                    // Обновление предмета
                    updateVisibilityItem(player, item);
                }
                return;
            }

            // Запрет прямого подключения к серверам через предметы лобби (например, компас)
            if (action.startsWith("server:")) {
                // Ничего не делаем: подключение к серверам разрешено только из GUI-меню
                return;
            }

            // Обработка действий без восстановления позиции
            plugin.getMenuManager().handleClick(player, action);
        }
    }

    private void updateVisibilityItem(Player player, ItemStack item) {
        PlayerJoinListener joinListener = getJoinListener();
        if (joinListener == null) return;

        boolean hidden = joinListener.isPlayerHidden(player);

        org.bukkit.configuration.file.FileConfiguration msgs = plugin.getMessagesConfig();
        if (hidden) {
            item.setType(org.bukkit.Material.GRAY_DYE);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String name = msgs != null && msgs.contains("visibility.item.hidden.name")
                        ? msgs.getString("visibility.item.hidden.name")
                        : "&c&lВидимость игроков &7(ПКМ)";
                java.util.List<String> lore = new java.util.ArrayList<>();
                String l1 = msgs != null && msgs.contains("visibility.item.hidden.lore1")
                        ? msgs.getString("visibility.item.hidden.lore1")
                        : "&7Игроки сейчас &cскрыты";
                String l2 = msgs != null && msgs.contains("visibility.item.hidden.lore2")
                        ? msgs.getString("visibility.item.hidden.lore2")
                        : "&7Нажмите, чтобы показать их!";
                lore.add(l1); lore.add(l2);
                meta.setDisplayName(MessageUtils.color(name));
                meta.setLore(MessageUtils.color(lore));
                item.setItemMeta(meta);
            }
        } else {
            item.setType(org.bukkit.Material.LIME_DYE);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String name = msgs != null && msgs.contains("visibility.item.shown.name")
                        ? msgs.getString("visibility.item.shown.name")
                        : "&a&lВидимость игроков &7(ПКМ)";
                java.util.List<String> lore = new java.util.ArrayList<>();
                String l1 = msgs != null && msgs.contains("visibility.item.shown.lore1")
                        ? msgs.getString("visibility.item.shown.lore1")
                        : "&7Игроки сейчас &aвидимы";
                String l2 = msgs != null && msgs.contains("visibility.item.shown.lore2")
                        ? msgs.getString("visibility.item.shown.lore2")
                        : "&7Нажмите, чтобы скрыть их!";
                lore.add(l1); lore.add(l2);
                meta.setDisplayName(MessageUtils.color(name));
                meta.setLore(MessageUtils.color(lore));
                item.setItemMeta(meta);
            }
        }
    }

    private PlayerJoinListener getJoinListener() {
        for (org.bukkit.plugin.RegisteredListener listener :
                org.bukkit.event.player.PlayerJoinEvent.getHandlerList().getRegisteredListeners()) {
            if (listener.getListener() instanceof PlayerJoinListener) {
                return (PlayerJoinListener) listener.getListener();
            }
        }
        return null;
    }
}