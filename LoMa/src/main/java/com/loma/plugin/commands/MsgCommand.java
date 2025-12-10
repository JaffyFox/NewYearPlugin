package com.loma.plugin.commands;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MsgCommand implements CommandExecutor {
    private final LoMa plugin;

    public MsgCommand(LoMa plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtils.send(sender, "&cКоманду может использовать только игрок.");
            return true;
        }
        if (!sender.hasPermission("loma.msg")) {
            MessageUtils.send(sender, plugin.getMessage("general.no-permission"));
            return true;
        }
        if (args.length < 2) {
            MessageUtils.send(sender, "&eИспользование: &f/" + label + " <игрок> <сообщение>");
            return true;
        }
        Player p = (Player) sender;
        String target = args[0];
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        if (target.equalsIgnoreCase(p.getName())) {
            MessageUtils.send(p, "&cНельзя отправлять сообщение самому себе.");
            return true;
        }

        // Отрисуем у отправителя и обновим /r
        plugin.setLastPmPartner(p.getUniqueId(), target);
        MessageUtils.send(p, "&d[ЛС] &7кому &f" + target + "&7: &r" + message);

        // Отправим на Velocity
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("PRIVATE_MSG");
            out.writeUTF(p.getName());
            out.writeUTF(p.getUniqueId().toString());
            out.writeUTF(target);
            out.writeUTF(message);
            p.sendPluginMessage(plugin, "loma:stats", out.toByteArray());
        } catch (Exception ex) {
            MessageUtils.send(p, "&cНе удалось отправить сообщение: " + ex.getMessage());
        }
        return true;
    }
}
