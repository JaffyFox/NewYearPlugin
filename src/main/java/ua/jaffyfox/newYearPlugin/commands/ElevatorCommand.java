package ua.jaffyfox.newYearPlugin.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ua.jaffyfox.newYearPlugin.config.ConfigManager;
import ua.jaffyfox.newYearPlugin.elevator.ElevatorFloor;
import ua.jaffyfox.newYearPlugin.elevator.ElevatorManager;
import org.bukkit.util.StringUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ElevatorCommand implements CommandExecutor, TabCompleter {

    private final ElevatorManager elevatorManager;
    private final ConfigManager configManager;

    public ElevatorCommand(ConfigManager configManager, ElevatorManager elevatorManager) {
        this.configManager = configManager;
        this.elevatorManager = elevatorManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭта команда только для игроков!");
            return true;
        }
        Player player = (Player) sender;

        if (args.length != 1) {
            sender.sendMessage("§cИспользуйте: /" + label + " <этаж>");
            return true;
        }

        int targetFloor;
        try {
            targetFloor = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(configManager.getConfig("ElevatorConfig").getString("messages.invalid_floor", "§cЭтот этаж не существует!"));
            return true;
        }

        ElevatorFloor current = elevatorManager.getFloorByCoordinates(player.getLocation());
        if (current == null) {
            player.sendMessage(configManager.getConfig("ElevatorConfig").getString("messages.not_in_elevator", "§cВы не находитесь в лифте!"));
            return true;
        }

        ElevatorFloor dest = elevatorManager.getFloorByNumber(targetFloor);
        if (dest == null) {
            player.sendMessage(configManager.getConfig("ElevatorConfig").getString("messages.invalid_floor", "§cЭтот этаж не существует!"));
            return true;
        }

        elevatorManager.teleportToFloor(player, dest);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> floors = new ArrayList<>();
            for (ElevatorFloor f : elevatorManager.getAllFloors()) {
                floors.add(String.valueOf(f.getFloor()));
            }
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], floors, completions);
            Collections.sort(completions);
            return completions;
        }
        return Collections.emptyList();
    }
}
