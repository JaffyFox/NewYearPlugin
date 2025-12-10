package ua.jaffyfox.newYearPlugin.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.StringUtil;
import ua.jaffyfox.newYearPlugin.config.ConfigManager;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SpeedCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager configManager;

    public SpeedCommand(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭта команда только для игроков!");
            return true;
        }
        Player player = (Player) sender;

        // If no args: show current speed
        if (args.length == 0) {
            int current = getCurrentSpeedLevel(player);
            sender.sendMessage("§eТекущая скорость: §a" + current + "§7 (используйте /" + label + " <0-5> чтобы изменить)");
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cУровень должен быть числом 0-5");
            return true;
        }
        if (level < 0 || level > 5) {
            sender.sendMessage("§cУровень должен быть в диапазоне 0-5");
            return true;
        }

        player.removePotionEffect(PotionEffectType.SPEED);
        if (level > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, level - 1, false, false), true);
        }
        // Persist choice
        FileConfiguration data = configManager.getConfig("SpeedData");
        if (data != null) {
            data.set("players." + player.getUniqueId().toString(), level);
            configManager.saveConfig("SpeedData");
        }
        sender.sendMessage("§aСкорость установлена на " + level);
        return true;
    }

    private int getCurrentSpeedLevel(Player player) {
        // Prefer explicit potion effect
        PotionEffect eff = player.getPotionEffect(PotionEffectType.SPEED);
        if (eff != null) return eff.getAmplifier() + 1;
        // Fallback to saved data
        FileConfiguration data = configManager.getConfig("SpeedData");
        int saved = data != null ? data.getInt("players." + player.getUniqueId().toString(), -1) : -1;
        if (saved >= 0) return saved;
        // Fallback to default
        int def = configManager.getConfig("SpeedConfig").getInt("default_level", 0);
        return Math.max(0, Math.min(5, def));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> levels = Arrays.asList("0", "1", "2", "3", "4", "5");
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], levels, completions);
            return completions;
        }
        return Collections.emptyList();
    }
}
