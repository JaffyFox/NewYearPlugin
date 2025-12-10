package com.loma.plugin.commands;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StatsCommand implements CommandExecutor, TabCompleter {
    private final LoMa plugin;
    public StatsCommand(LoMa plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtils.send(sender, plugin.getMessage("general.player-only"));
            return true;
        }
        Player player = (Player) sender;

        UUID target = player.getUniqueId();
        if (args.length >= 1) {
            String name = args[0];
            OfflinePlayer op = Bukkit.getOfflinePlayer(name);
            if (op != null && op.getUniqueId() != null) {
                target = op.getUniqueId();
            } else {
                // попробуем запросить профиль по нику на прокси (когда придёт – кнопкой можно будет открыть)
                plugin.requestPlayerProfileByName(player, name);
            }
        }

        plugin.getMenuManager().openStatsMenu(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String pref = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pref)) out.add(p.getName());
            }
            int cap = 50;
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op.getName() == null) continue;
                String nm = op.getName();
                if (nm.toLowerCase().startsWith(pref)) {
                    out.add(nm);
                    if (--cap <= 0) break;
                }
            }
        }
        return out;
    }
}
