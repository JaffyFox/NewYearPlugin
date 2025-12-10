package com.loma.plugin.commands;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ServerCommand implements CommandExecutor {

    private final LoMa plugin;

    public ServerCommand(LoMa plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtils.send(sender, plugin.getMessage("general.player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("loma.server")) {
            MessageUtils.send(player, plugin.getMessage("general.no-permission"));
            return true;
        }
        if (args.length == 0) {
            MessageUtils.send(player, plugin.getMessage("commands.server.usage"));
            return true;
        }

        String serverName = args[0];
        connectToServer(player, serverName);

        return true;
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

        // Регистрация канала BungeeCord если еще не зарегистрирован
        if (!plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, "BungeeCord")) {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);

        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());

        MessageUtils.send(player, plugin.getMessage("server.connecting")
                .replace("{server}", server));

        // Title анимация
        MessageUtils.sendTitle(player,
                plugin.getMessage("titles.server-change.title"),
                plugin.getMessage("titles.server-change.subtitle").replace("{server}", server));

        // ActionBar
        MessageUtils.sendActionBar(player, plugin.getMessage("actionbar.connecting")
                .replace("{server}", server));
    }
}