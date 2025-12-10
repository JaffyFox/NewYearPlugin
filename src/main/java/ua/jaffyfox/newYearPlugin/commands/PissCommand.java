package ua.jaffyfox.newYearPlugin.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ua.jaffyfox.newYearPlugin.config.ConfigManager;
import ua.jaffyfox.newYearPlugin.fun.PissManager;
import org.bukkit.util.StringUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PissCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager configManager;
    private final PissManager pissManager;

    public PissCommand(ConfigManager configManager, PissManager pissManager) {
        this.configManager = configManager;
        this.pissManager = pissManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!pissManager.isEnabled()) {
            String msg = configManager.getConfig("PissConfig").getString("messages.disabled", "§cФункция мочеиспускания выключена.");
            sender.sendMessage(msg);
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cИспользуйте: /" + label + " <ник>");
                return true;
            }
            Player player = (Player) sender;
            if (!sender.hasPermission("newyear.piss")) {
                sender.sendMessage(configManager.getConfig("PissConfig").getString("messages.no_permission", "§cНедостаточно прав."));
                return true;
            }
            pissManager.startUrination(player);
            String msg = configManager.getConfig("PissConfig").getString("messages.command_self", "§eВы начали мочеиспускание.");
            player.sendMessage(msg);
            return true;
        }

        // Targeted
        if (!sender.hasPermission("newyear.piss.others")) {
            sender.sendMessage(configManager.getConfig("PissConfig").getString("messages.no_permission", "§cНедостаточно прав."));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(configManager.getConfig("PissConfig").getString("messages.player_not_found", "§cИгрок не найден."));
            return true;
        }

        pissManager.startUrination(target);
        String msgOther = configManager.getConfig("PissConfig").getString("messages.command_other", "§eВы запустили мочеиспускание у %player%.");
        if (msgOther != null) sender.sendMessage(msgOther.replace("%player%", target.getName()));
        String msgTarget = configManager.getConfig("PissConfig").getString("messages.command_target", "§eУ вас началось мочеиспускание по команде %sender%.");
        if (msgTarget != null) target.sendMessage(msgTarget.replace("%sender%", sender.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], names, completions);
            Collections.sort(completions);
            return completions;
        }
        return Collections.emptyList();
    }
}
