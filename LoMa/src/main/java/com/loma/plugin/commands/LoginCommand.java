package com.loma.plugin.commands;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LoginCommand implements CommandExecutor {

    private final LoMa plugin;

    public LoginCommand(LoMa plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtils.send(sender, "&cЭта команда доступна только игрокам.");
            return true;
        }

        Player player = (Player) sender;

        // Проверка, что игрок уже не авторизован
        if (plugin.isPlayerAuthorized(player.getUniqueId())) {
            MessageUtils.send(player, "&aВы уже авторизованы!");
            return true;
        }

        if (args.length == 0) {
            MessageUtils.send(player, "&cИспользование: /login <пароль>");
            return true;
        }

        String password = args[0];

        // Асинхронно проверяем пароль через Velocity
        plugin.checkLogin(player, password, (success, allowedServers) -> {
            if (success) {
                plugin.setPlayerAuthorized(player.getUniqueId(), true);
                MessageUtils.send(player, "&aВы успешно авторизовались!");
                
                if (allowedServers != null && !allowedServers.isEmpty()) {
                    MessageUtils.send(player, "&7Доступные сервера: &f" + String.join(", ", allowedServers));
                }
                
                MessageUtils.sendTitle(player, "&aУспешно!", "&7Вы вошли в аккаунт");
            } else {
                MessageUtils.send(player, "&cНеверный пароль или аккаунт не найден.");
                MessageUtils.send(player, "&7Если у вас нет аккаунта, создайте заявку в Discord: /minecraft nick add");
            }
        });

        return true;
    }
}
