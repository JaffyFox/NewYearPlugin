package ua.jaffyfox.newYearPlugin.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ua.jaffyfox.newYearPlugin.config.ConfigManager;

public class SpawnCommand implements CommandExecutor {

    private final ConfigManager configManager;

    public SpawnCommand(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭта команда только для игроков!");
            return true;
        }
        Player player = (Player) sender;

        double x = configManager.getConfig("SpawnConfig").getDouble("x", 0);
        double y = configManager.getConfig("SpawnConfig").getDouble("y", 64);
        double z = configManager.getConfig("SpawnConfig").getDouble("z", 0);

        World world = Bukkit.getWorlds().get(0);
        Location loc = new Location(world, x + 0.5, y, z + 0.5);
        player.teleport(loc);
        player.sendMessage("§aТелепортировано на спавн.");
        return true;
    }
}
