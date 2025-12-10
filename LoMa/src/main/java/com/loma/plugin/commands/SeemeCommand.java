package com.loma.plugin.commands;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SeemeCommand implements CommandExecutor {
    private final LoMa plugin;

    public SeemeCommand(LoMa plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtils.send(sender, plugin.getMessage("general.player-only"));
            return true;
        }
        Player player = (Player) sender;

        boolean current = plugin.getPreferencesManager() == null
                || plugin.getPreferencesManager().isEnabled(player.getUniqueId(), "share_location", true);

        if (args.length == 0) {
            MessageUtils.send(player, current
                    ? plugin.getMessage("seeme.status.on")
                    : plugin.getMessage("seeme.status.off"));
            MessageUtils.send(player, plugin.getMessage("seeme.usage").replace("{label}", label));
            return true;
        }

        String arg = args[0].toLowerCase();
        if (arg.equals("on")) {
            if (plugin.getPreferencesManager() != null)
                plugin.getPreferencesManager().set(player.getUniqueId(), "share_location", true);
            plugin.setShareVisibility(player.getUniqueId(), true);
            MessageUtils.send(player, plugin.getMessage("seeme.enabled"));
        } else if (arg.equals("off")) {
            if (plugin.getPreferencesManager() != null)
                plugin.getPreferencesManager().set(player.getUniqueId(), "share_location", false);
            plugin.setShareVisibility(player.getUniqueId(), false);
            MessageUtils.send(player, plugin.getMessage("seeme.disabled"));
        } else {
            MessageUtils.send(player, plugin.getMessage("seeme.invalid-arg").replace("{label}", label));
        }
        return true;
    }
}
