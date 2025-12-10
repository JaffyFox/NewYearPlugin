package com.loma.plugin.commands;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetSpawnCommand implements CommandExecutor {

    private final LoMa plugin;

    public SetSpawnCommand(LoMa plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtils.send(sender, plugin.getMessage("general.player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("loma.setspawn")) {
            MessageUtils.send(player, plugin.getMessage("general.no-permission"));
            return true;
        }

        Location location = player.getLocation();
        plugin.getSpawnManager().setSpawn(location);

        MessageUtils.send(player, plugin.getMessage("spawn.set"));

        // Визуальный эффект
        plugin.getAnimationManager().playParticleEffect(player, "explosion");

        // Обновление конфигурации мира
        if (plugin.getConfig().getBoolean("spawn.world-settings.lock-time")) {
            location.getWorld().setTime(plugin.getConfig().getLong("spawn.world-settings.time", 6000));
            location.getWorld().setGameRuleValue("doDaylightCycle", "false");
        }

        if (plugin.getConfig().getBoolean("spawn.world-settings.lock-weather")) {
            location.getWorld().setStorm(false);
            location.getWorld().setThundering(false);
            location.getWorld().setGameRuleValue("doWeatherCycle", "false");
        }

        return true;
    }
}