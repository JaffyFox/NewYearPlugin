package com.loma.plugin.commands;

import com.loma.plugin.LoMa;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ProfileTabCompleter implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            // online players
            for (Player p : Bukkit.getOnlinePlayers()) {
                String name = p.getName();
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(name);
            }
            // offline players (limit)
            int cap = 50;
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op.getName() == null) continue;
                String name = op.getName();
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(name);
                    if (--cap <= 0) break;
                }
            }
            // cached profiles (from velocity responses) if any
            try {
                LoMa plugin = LoMa.getInstance();
                java.lang.reflect.Field f = LoMa.class.getDeclaredField("profileCache");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Map<UUID, LoMa.PlayerProfileCache> cache = (java.util.Map<UUID, LoMa.PlayerProfileCache>) f.get(plugin);
                for (LoMa.PlayerProfileCache pc : cache.values()) {
                    if (pc.username != null && pc.username.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(pc.username);
                    }
                }
            } catch (Exception ignored) {}
        }
        return out;
    }
}
