package ua.jaffyfox.newYearPlugin.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import ua.jaffyfox.newYearPlugin.config.ConfigManager;
import ua.jaffyfox.newYearPlugin.fun.VomitManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VomitCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager configManager;
    private final VomitManager vomitManager;

    public VomitCommand(ConfigManager configManager, VomitManager vomitManager) {
        this.configManager = configManager;
        this.vomitManager = vomitManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!vomitManager.isEnabled()) {
            String msg = configManager.getConfig("VomitConfig").getString("messages.disabled", "§cФункция блевоты выключена.");
            sender.sendMessage(msg);
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cИспользуйте: /" + label + " <ник>");
                return true;
            }
            Player player = (Player) sender;
            if (!sender.hasPermission("newyear.vomit")) {
                sender.sendMessage(configManager.getConfig("VomitConfig").getString("messages.no_permission", "§cНедостаточно прав."));
                return true;
            }
            vomitManager.startVomit(player);
            String msg = configManager.getConfig("VomitConfig").getString("messages.command_self", "§eВы вызвали блевоту.");
            if (msg != null && !msg.isEmpty()) player.sendMessage(msg);
            return true;
        }

        if (!sender.hasPermission("newyear.vomit.others")) {
            sender.sendMessage(configManager.getConfig("VomitConfig").getString("messages.no_permission", "§cНедостаточно прав."));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(configManager.getConfig("VomitConfig").getString("messages.player_not_found", "§cИгрок не найден."));
            return true;
        }

        vomitManager.startVomit(target);
        String msgOther = configManager.getConfig("VomitConfig").getString("messages.command_other", "§eВы вызвали блевоту у %player%.");
        if (msgOther != null) sender.sendMessage(msgOther.replace("%player%", target.getName()));
        String msgTarget = configManager.getConfig("VomitConfig").getString("messages.command_target", "§eУ вас началась блевота по команде %sender%.");
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
