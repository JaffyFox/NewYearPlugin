package com.loma.plugin.commands;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReplyCommand implements CommandExecutor {
    private final LoMa plugin;

    public ReplyCommand(LoMa plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtils.send(sender, "&cКоманду может использовать только игрок.");
            return true;
        }
        if (!sender.hasPermission("loma.msg.reply")) {
            MessageUtils.send(sender, plugin.getMessage("general.no-permission"));
            return true;
        }
        if (args.length < 1) {
            MessageUtils.send(sender, "&eИспользование: &f/" + label + " <сообщение>");
            return true;
        }
        Player p = (Player) sender;
        String partner = plugin.getLastPmPartner(p.getUniqueId());
        if (partner == null || partner.isEmpty()) {
            MessageUtils.send(p, "&cНекому отвечать. Вы ещё не получали сообщений.");
            return true;
        }
        String message = String.join(" ", args);

        if (partner.equalsIgnoreCase(p.getName())) {
            MessageUtils.send(p, "&cНельзя отправлять сообщение самому себе.");
            return true;
        }

        // Отрисуем у отправителя и обновим /r
        plugin.setLastPmPartner(p.getUniqueId(), partner);
        MessageUtils.send(p, "&d[ЛС] &7кому &f" + partner + "&7: &r" + message);

        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("PRIVATE_MSG");
            out.writeUTF(p.getName());
            out.writeUTF(p.getUniqueId().toString());
            out.writeUTF(partner);
            out.writeUTF(message);
            p.sendPluginMessage(plugin, "loma:stats", out.toByteArray());
        } catch (Exception ex) {
            MessageUtils.send(p, "&cНе удалось отправить сообщение: " + ex.getMessage());
        }
        return true;
    }
}
