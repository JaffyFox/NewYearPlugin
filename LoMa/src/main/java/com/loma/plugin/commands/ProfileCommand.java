package com.loma.plugin.commands;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ProfileCommand implements CommandExecutor {

    private final LoMa plugin;

    public ProfileCommand(LoMa plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtils.send(sender, plugin.getMessage("general.player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length >= 1) {
            String target = args[0];
            // Запрашиваем профиль у Velocity по нику (асинхронно)
            plugin.requestPlayerProfileByName(player, target);
        }

        // Открываем меню профиля (будет обновлено при приходе данных цели)
        plugin.getMenuManager().openMenu(player, "profile");
        return true;
    }
}
