package com.loma.plugin.commands;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LoMaCommand implements CommandExecutor {

    private final LoMa plugin;

    public LoMaCommand(LoMa plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                if (!sender.hasPermission("loma.reload")) {
                    MessageUtils.send(sender, plugin.getMessage("general.no-permission"));
                    return true;
                }

                plugin.reload();
                MessageUtils.send(sender, plugin.getMessage("general.reload-success"));
                return true;

            case "version":
                MessageUtils.send(sender, plugin.getMessage("commands.loma.version")
                        .replace("{version}", plugin.getDescription().getVersion()));
                return true;

            case "help":
                sendHelp(sender);
                return true;

            case "stats":
                if (!(sender instanceof Player)) {
                    MessageUtils.send(sender, LoMa.getInstance().getMessage("general.player-only"));
                    return true;
                }
                Player player = (Player) sender;
                if (LoMa.getInstance().getPlaytimeService() == null) {
                    MessageUtils.send(player, LoMa.getInstance().getMessage("errors.database-connection"));
                    return true;
                }
                long totalMin = LoMa.getInstance().getPlaytimeService().getTotalMinutes(player.getUniqueId());
                java.util.Map<String, Long> per = LoMa.getInstance().getPlaytimeService().getPerServerMinutes(player.getUniqueId());
                String header = LoMa.getInstance().getMessage("stats.header").replace("{player}", player.getName());
                MessageUtils.send(player, header);
                MessageUtils.send(player, LoMa.getInstance().getMessage("stats.total").replace("{hours}", com.loma.plugin.playtime.PlaytimeService.formatHours(totalMin)));
                if (per.isEmpty()) {
                    MessageUtils.send(player, LoMa.getInstance().getMessage("stats.empty"));
                } else {
                    for (java.util.Map.Entry<String, Long> e : per.entrySet()) {
                        String line = LoMa.getInstance().getMessage("stats.entry")
                                .replace("{server}", e.getKey())
                                .replace("{hours}", com.loma.plugin.playtime.PlaytimeService.formatHours(e.getValue()));
                        MessageUtils.send(player, line);
                    }
                }
                MessageUtils.send(player, LoMa.getInstance().getMessage("stats.footer"));
                return true;

            case "debug":
                if (!sender.hasPermission("loma.admin")) {
                    MessageUtils.send(sender, plugin.getMessage("general.no-permission"));
                    return true;
                }

                boolean debug = !plugin.getConfig().getBoolean("debug");
                plugin.getConfig().set("debug", debug);
                plugin.saveConfig();

                MessageUtils.send(sender, plugin.getMessage(debug ? "debug.enabled" : "debug.disabled"));
                return true;

            default:
                sendHelp(sender);
                return true;
        }

    }

    private void sendHelp(CommandSender sender) {
        MessageUtils.send(sender, plugin.getMessage("commands.loma.help-header"));

        if (sender.hasPermission("loma.admin")) {
            MessageUtils.send(sender, plugin.getMessage("commands.help.reload"));
            MessageUtils.send(sender, plugin.getMessage("commands.help.debug"));
        }

        MessageUtils.send(sender, plugin.getMessage("commands.help.version"));
        MessageUtils.send(sender, plugin.getMessage("commands.help.help"));
        MessageUtils.send(sender, plugin.getMessage("commands.help.stats"));

        if (sender.hasPermission("loma.npc")) {
            MessageUtils.send(sender, plugin.getMessage("commands.help.npc"));
        }

        if (sender.hasPermission("loma.spawn")) {
            MessageUtils.send(sender, plugin.getMessage("commands.help.spawn"));
        }

        if (sender.hasPermission("loma.setspawn")) {
            MessageUtils.send(sender, plugin.getMessage("commands.help.setspawn"));
        }

        MessageUtils.send(sender, plugin.getMessage("commands.loma.help-footer"));
    }
}