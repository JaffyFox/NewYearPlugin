package com.loma.plugin.commands;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.loma.plugin.LoMa;
import com.loma.plugin.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SrvCommand implements CommandExecutor {
    private final LoMa plugin;

    public SrvCommand(LoMa plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("loma.srv") && !sender.hasPermission("loma.admin")) {
            MessageUtils.send(sender, plugin.getMessage("general.no-permission"));
            return true;
        }

        if (args.length < 1 || args.length > 2) {
            MessageUtils.send(sender, "&eИспользование: &f/srv [server] <on|off>&7. Пример: &f/srv main_events on&7 или &f/srv off");
            return true;
        }

        String targetServer;
        String stateArg;
        if (args.length == 1) {
            // /srv on|off — целевой сервер берём из конфига этого Spigot-сервера
            stateArg = args[0];
            targetServer = plugin.getConfig().getString("bungeecord.server-name", "lobby");
        } else {
            targetServer = args[0];
            stateArg = args[1];
        }

        boolean open;
        if (stateArg.equalsIgnoreCase("on")) {
            open = true;
        } else if (stateArg.equalsIgnoreCase("off")) {
            open = false;
        } else {
            MessageUtils.send(sender, "&cУкажите состояние: &fon&c или &foff");
            return true;
        }

        // Нельзя закрыть лобби на уровне лобби-плагина (переадресуется прокси)
        if ("lobby".equalsIgnoreCase(targetServer) && !open) {
            MessageUtils.send(sender, "&eЗакрытие лобби не поддерживается этим плагином.");
            // Не считаем ошибкой — просто информируем
        }

        try {
            // Меняем open: в plugins/LoMa/menus/servers.yml (по server-имени или ключу секции)
            File menusDir = new File(plugin.getDataFolder(), "menus");
            if (!menusDir.exists()) menusDir.mkdirs();
            File serversFile = new File(menusDir, "servers.yml");
            if (!serversFile.exists()) {
                plugin.saveResource("menus/servers.yml", false);
            }
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(serversFile);
            ConfigurationSection servers = cfg.getConfigurationSection("servers");
            if (servers == null) {
                MessageUtils.send(sender, "&cВ конфиге menus/servers.yml не найдена секция servers.");
                return true;
            }

            String matchedKey = null;
            String serverInternal = null;
            List<String> allowList = new ArrayList<>();

            for (String key : servers.getKeys(false)) {
                ConfigurationSection s = servers.getConfigurationSection(key);
                if (s == null) continue;
                String internal = s.getString("server", key);
                if (key.equalsIgnoreCase(targetServer) || (internal != null && internal.equalsIgnoreCase(targetServer))) {
                    matchedKey = key;
                    serverInternal = internal != null ? internal : key;
                    // set open
                    s.set("open", open);
                    // read allow-permissions as usernames/perms for Velocity sync
                    List<String> raw = s.getStringList("allow-permissions");
                    for (String v : raw) if (v != null && !v.trim().isEmpty()) allowList.add(v.trim());
                    break;
                }
            }

            if (matchedKey == null || serverInternal == null) {
                MessageUtils.send(sender, "&cСервер не найден в menus/servers.yml: &f" + targetServer);
                return true;
            }

            cfg.save(serversFile);
            // Перезагрузим меню, чтобы обновить правила
            if (plugin.getMenuManager() != null) plugin.getMenuManager().reload();

            // Отправим состояние на Velocity, чтобы прокси тоже уважал закрытие
            sendServerOpenToProxy(serverInternal, open, allowList);

            MessageUtils.send(sender, (open ? "&aОткрыл доступ" : "&cЗакрыл доступ") + " к серверу &f" + serverInternal);
        } catch (Exception ex) {
            plugin.getLogger().warning("/srv error: " + ex.getMessage());
            MessageUtils.send(sender, "&cОшибка при обновлении состояния сервера: " + ex.getMessage());
        }

        return true;
    }

    private void sendServerOpenToProxy(String server, boolean open, List<String> allowNames) {
        try {
            Player carrier = null;
            for (Player p : Bukkit.getOnlinePlayers()) { carrier = p; break; }
            if (carrier == null) return; // некому отправить плагин-сообщение

            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("SET_SERVER_OPEN");
            out.writeUTF(server);
            out.writeBoolean(open);
            out.writeInt(allowNames.size());
            for (String n : allowNames) out.writeUTF(n);
            carrier.sendPluginMessage(plugin, "loma:stats", out.toByteArray());
        } catch (Exception ignored) {}
    }
}
