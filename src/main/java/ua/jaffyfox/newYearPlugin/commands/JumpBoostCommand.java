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

public class JumpBoostCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager configManager;

    public JumpBoostCommand(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭта команда только для игроков!");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            int current = getCurrentJumpLevel(player);
            sender.sendMessage("§eТекущий Jump Boost: §a" + current + "§7 (используйте /" + label + " <1-5> чтобы изменить)");
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cЧисло невалидно!");
            return true;
        }

        if (level < 1 || level > 5) {
            sender.sendMessage("§cУровень должен быть от 1 до 5!");
            return true;
        }

        // Apply effect now
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, level - 1, false, false), true);

        // Persist per-player
        FileConfiguration data = configManager.getConfig("JumpData");
        if (data != null) {
            data.set("players." + player.getUniqueId().toString(), level);
            configManager.saveConfig("JumpData");
        }

        sender.sendMessage("§aУровень Jump Boost установлен на " + level);
        return true;
    }

    private int getCurrentJumpLevel(Player player) {
        PotionEffect eff = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
        if (eff != null) return eff.getAmplifier() + 1;
        FileConfiguration data = configManager.getConfig("JumpData");
        int saved = data != null ? data.getInt("players." + player.getUniqueId().toString(), -1) : -1;
        if (saved >= 1 && saved <= 5) return saved;
        int def = configManager.getConfig("JumpBoostConfig").getInt("default_level", 2);
        if (def < 1) def = 1; if (def > 5) def = 5;
        return def;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> levels = Arrays.asList("1", "2", "3", "4", "5");
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], levels, completions);
            return completions;
        }
        return Collections.emptyList();
    }
}