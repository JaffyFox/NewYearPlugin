package com.loma.plugin.commands;

import com.loma.plugin.LoMa;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HolaBotCommand implements CommandExecutor {

    private final LoMa plugin;

    public HolaBotCommand(LoMa plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Проверка что команду отправил HoloBot
        if (!(sender instanceof Player)) {
            return true;
        }
        
        Player player = (Player) sender;
        if (!player.getName().equalsIgnoreCase("HoloBot")) {
            return true; // Игнорируем других игроков
        }

        // holabot_account_add <username> <passwordHash> <servers> <discordId>
        if (args.length < 4) {
            return true;
        }

        String username = args[0];
        String passwordHash = args[1];
        String servers = args[2];
        String discordId = args[3];

        // Логируем получение команды
        plugin.getLogger().info("HoloBot command received: creating account for " + username);
        
        // Отправляем данные в Velocity через plugin messaging
        plugin.createAccount(username, passwordHash, servers, discordId);
        
        return true;
    }
}
