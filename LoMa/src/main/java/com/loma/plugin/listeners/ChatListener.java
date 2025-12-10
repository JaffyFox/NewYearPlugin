package com.loma.plugin.listeners;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChatListener implements Listener {

    private final LoMa plugin;
    private final Map<UUID, Long> lastMessage;
    private final Map<UUID, String> lastMessageContent;

    public ChatListener(LoMa plugin) {
        this.plugin = plugin;
        this.lastMessage = new HashMap<>();
        this.lastMessageContent = new HashMap<>();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfig().getBoolean("features.custom-chat.enabled")) {
            return;
        }

        Player player = event.getPlayer();
        String message = event.getMessage();

        // Антиспам отключён по требованию: не блокируем и не предупреждаем

        // Форматирование чата
        String format = plugin.getConfig().getString("features.custom-chat.format");

        // Получение префикса (Vault интеграция)
        String prefix = "";
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            // Здесь можно добавить получение префикса через Vault
        }

        format = format.replace("{prefix}", prefix)
                .replace("{player}", player.getName())
                .replace("{displayname}", player.getDisplayName())
                .replace("{message}", message);

        // PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            format = PlaceholderAPI.setPlaceholders(player, format);
        }

        event.setFormat(MessageUtils.color(format));
    }

    private boolean isSimilar(String msg1, String msg2) {
        // Простая проверка схожести
        msg1 = msg1.toLowerCase().replaceAll("[^a-z0-9]", "");
        msg2 = msg2.toLowerCase().replaceAll("[^a-z0-9]", "");

        if (msg1.equals(msg2)) return true;

        // Проверка на содержание одной строки в другой
        if (msg1.length() > 5 && msg2.length() > 5) {
            return msg1.contains(msg2) || msg2.contains(msg1);
        }

        return false;
    }
}