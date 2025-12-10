package com.loma.plugin.commands;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class LoMaTabCompleter implements TabCompleter {
    private final LoMa plugin;

    public LoMaTabCompleter() {
        this.plugin = LoMa.getInstance();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("loma")) return null;

        List<String> empty = new ArrayList<>();
        if (args.length == 0) return empty;

        // root subcommands
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList(
                    "help", "reload", "menu", "spawn", "setspawn", "npc"
            ));
            if (sender.hasPermission("loma.admin")) {
                subs.add("give");
            }
            return filter(subs, args[0]);
        }

        // loma menu <name>
        if (args.length == 2 && args[0].equalsIgnoreCase("menu")) {
            List<String> menus = Arrays.asList("servers", "profile", "cosmetics", "lobbies");
            return filter(menus, args[1]);
        }

        // loma give <player>
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            if (sender instanceof Player) {
                return filter(
                        ((Player) sender).getServer().getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()),
                        args[1]
                );
            }
        }

        return empty;
    }

    private List<String> filter(List<String> values, String token) {
        final String lower = token.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted()
                .collect(Collectors.toList());
    }
}
