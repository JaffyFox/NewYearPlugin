package ua.jaffyfox.newYearPlugin.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ua.jaffyfox.newYearPlugin.config.ConfigManager;
import ua.jaffyfox.newYearPlugin.fun.SitManager;

import java.util.Collections;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import org.bukkit.util.StringUtil;

public class SitCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager configManager;
    private final SitManager sitManager;

    public SitCommand(ConfigManager configManager, SitManager sitManager) {
        this.configManager = configManager;
        this.sitManager = sitManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭта команда только для игроков!");
            return true;
        }
        Player p = (Player) sender;

        // Toggle right-click mode via subcommands
        if (args.length == 1) {
            String arg = args[0].toLowerCase();
            if (arg.equals("on") || arg.equals("enable") || arg.equals("off") || arg.equals("disable")) {
                if (!p.hasPermission("newyear.sit.toggle")) {
                    p.sendMessage(configManager.getConfig("SitConfig").getString("messages.no_permission", "§cНедостаточно прав."));
                    return true;
                }
                boolean value = arg.equals("on") || arg.equals("enable");
                configManager.getConfig("SitConfig").set("right_click", value);
                configManager.saveConfig("SitConfig");
                p.sendMessage(value ? "§aСидение по ПКМ включено." : "§eСидение по ПКМ отключено.");
                return true;
            }
        }

        if (!sitManager.isEnabled() || !configManager.getConfig("SitConfig").getBoolean("allow_command", true)) {
            p.sendMessage(configManager.getConfig("SitConfig").getString("messages.disabled", "§cФункция сидения выключена."));
            return true;
        }
        if (!p.hasPermission("newyear.sit.use")) {
            p.sendMessage(configManager.getConfig("SitConfig").getString("messages.no_permission", "§cНедостаточно прав."));
            return true;
        }
        if (sitManager.isSitting(p)) {
            sitManager.stand(p);
            String msg = configManager.getConfig("SitConfig").getString("messages.stood_up", "§aВы встали.");
            if (msg != null && !msg.isEmpty()) p.sendMessage(msg);
            return true;
        }
        if (sitManager.trySitAnywhere(p)) return true;
        p.sendMessage(configManager.getConfig("SitConfig").getString("messages.already_sitting", "§eНе удалось сесть здесь."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> opts = Arrays.asList("on", "off", "enable", "disable");
            List<String> out = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], opts, out);
            return out;
        }
        return Collections.emptyList();
    }
}
