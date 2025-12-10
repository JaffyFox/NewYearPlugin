package ua.jaffyfox.newYearPlugin.commands;

import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GamemodeCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭта команда только для игроков!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage("§cИспользуйте: /gamemode <survival|creative|adventure|spectator>");
            return true;
        }

        GameMode mode;

        try {
            mode = GameMode.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cНеизвестный режим: " + args[0]);
            return true;
        }

        player.setGameMode(mode);
        player.sendMessage("§aГеймод изменён на " + mode.name().toLowerCase());

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> modes = Arrays.asList("survival", "creative", "adventure", "spectator");
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], modes, completions);
            return completions;
        }
        return Collections.emptyList();
    }
}