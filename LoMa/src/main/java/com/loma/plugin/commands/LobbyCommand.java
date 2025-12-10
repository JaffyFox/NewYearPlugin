package com.loma.plugin.commands;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LobbyCommand implements CommandExecutor {

    private final LoMa plugin;

    public LobbyCommand(LoMa plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtils.send(sender, plugin.getMessage("general.player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("loma.lobby")) {
            MessageUtils.send(player, plugin.getMessage("general.no-permission"));
            return true;
        }

        // Проверка, не в лобби ли уже игрок
        String currentServer = plugin.getConfig().getString("bungeecord.server-name", "");
        String fallbackServer = plugin.getConfig().getString("bungeecord.fallback-server", "lobby");

        if (currentServer.toLowerCase().contains("lobby") || currentServer.toLowerCase().contains("hub")) {
            // Телепортация на спавн если уже в лобби
            plugin.getSpawnManager().teleportToSpawn(player);
            MessageUtils.send(player, plugin.getMessage("spawn.teleported"));
        } else {
            // Подключение к лобби серверу
            connectToLobby(player, fallbackServer);
        }

        return true;
    }

    private void connectToLobby(Player player, String lobbyServer) {
        // Регистрация канала BungeeCord если еще не зарегистрирован
        if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, "BungeeCord")) {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(lobbyServer);

        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());

        MessageUtils.send(player, plugin.getMessage("lobby.returning"));

        // Анимация
        MessageUtils.sendTitle(player,
                plugin.getMessage("titles.lobby.title"),
                plugin.getMessage("titles.lobby.subtitle"));
    }
}