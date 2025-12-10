package com.loma.plugin.commands;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {

    private final LoMa plugin;

    public SpawnCommand(LoMa plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Телепортация другого игрока
        if (args.length > 0) {
            if (!sender.hasPermission("loma.spawn.others")) {
                MessageUtils.send(sender, plugin.getMessage("spawn.other-no-permission"));
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                MessageUtils.send(sender, plugin.getMessage("general.player-not-found")
                        .replace("{player}", args[0]));
                return true;
            }

            Location spawn = plugin.getSpawnManager().getSpawn();
            if (spawn == null) {
                MessageUtils.send(sender, plugin.getMessage("spawn.not-set"));
                return true;
            }

            target.teleport(spawn);
            MessageUtils.send(target, plugin.getMessage("spawn.teleported"));
            MessageUtils.send(sender, plugin.getMessage("spawn.teleported-other")
                    .replace("{player}", target.getName()));

            // Анимация телепортации
            plugin.getAnimationManager().playTeleportAnimation(target);

            return true;
        }

        // Телепортация себя
        if (!(sender instanceof Player)) {
            MessageUtils.send(sender, plugin.getMessage("general.player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("loma.spawn")) {
            MessageUtils.send(player, plugin.getMessage("general.no-permission"));
            return true;
        }

        Location spawn = plugin.getSpawnManager().getSpawn();
        if (spawn == null) {
            MessageUtils.send(player, plugin.getMessage("spawn.not-set"));
            return true;
        }

        player.teleport(spawn);
        MessageUtils.send(player, plugin.getMessage("spawn.teleported"));

        // Анимация телепортации
        plugin.getAnimationManager().playTeleportAnimation(player);

        return true;
    }
}