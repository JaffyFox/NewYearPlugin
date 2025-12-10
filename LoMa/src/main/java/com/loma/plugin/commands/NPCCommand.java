package com.loma.plugin.commands;

import com.loma.plugin.LoMa;
import com.loma.plugin.npc.CustomNPC;
import com.loma.plugin.utils.MessageUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NPCCommand implements CommandExecutor, TabCompleter {

    private final LoMa plugin;

    public NPCCommand(LoMa plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("loma.npc")) {
            MessageUtils.send(sender, plugin.getMessage("general.no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                if (!(sender instanceof Player)) {
                    MessageUtils.send(sender, plugin.getMessage("general.player-only"));
                    return true;
                }

                if (args.length < 2) {
                    MessageUtils.send(sender, plugin.getMessage("npc.create-usage"));
                    return true;
                }

                Player player = (Player) sender;
                String npcName = args[1];
                String skinName = args.length > 2 ? args[2] : player.getName();

                CustomNPC npc = plugin.getNPCManager().createNPC(npcName, player.getLocation(), skinName);
                if (npc != null) {
                    MessageUtils.send(sender, plugin.getMessage("npc.created")
                            .replace("{name}", npcName));
                } else {
                    MessageUtils.send(sender, plugin.getMessage("errors.citizens-required"));
                }
                return true;

            case "remove":
            case "delete":
                if (args.length < 2) {
                    MessageUtils.send(sender, plugin.getMessage("commands.npc.remove-usage"));
                    return true;
                }

                CustomNPC toRemove = plugin.getNPCManager().getNPCByIdentifier(args[1]);
                if (toRemove == null) {
                    MessageUtils.send(sender, plugin.getMessage("npc.not-found"));
                    return true;
                }

                plugin.getNPCManager().removeNPC(toRemove.getId());
                MessageUtils.send(sender, plugin.getMessage("npc.removed")
                        .replace("{name}", toRemove.getName()));
                return true;

            case "list":
                sendNPCList(sender);
                return true;

            case "edit":
                if (args.length < 4) {
                    MessageUtils.send(sender, plugin.getMessage("commands.npc.edit-usage"));
                    return true;
                }

                editNPC(sender, args);
                return true;

            case "lookplayer":
                if (args.length < 3) {
                    MessageUtils.send(sender, plugin.getMessage("commands.npc.edit-usage"));
                    return true;
                }
                handleLookPlayerToggle(sender, args[1], args[2]);
                return true;

            case "teleport":
            case "tp":
                if (!(sender instanceof Player)) {
                    MessageUtils.send(sender, plugin.getMessage("general.player-only"));
                    return true;
                }

                if (args.length < 2) {
                    MessageUtils.send(sender, plugin.getMessage("commands.npc.tp-usage"));
                    return true;
                }

                teleportToNPC((Player) sender, args[1]);
                return true;

            case "action":
                if (args.length < 4) {
                    MessageUtils.send(sender, plugin.getMessage("commands.npc.action-usage"));
                    return true;
                }

                addAction(sender, args);
                return true;

            case "reload":
                plugin.getNPCManager().reload();
                MessageUtils.send(sender, plugin.getMessage("npc.reloaded"));
                return true;

            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        MessageUtils.send(sender, plugin.getMessage("commands.loma.help-header"));
        MessageUtils.send(sender, plugin.getMessage("commands.npc.create-usage"));
        MessageUtils.send(sender, plugin.getMessage("commands.npc.remove-usage"));
        MessageUtils.send(sender, plugin.getMessage("commands.npc.usage"));
        MessageUtils.send(sender, plugin.getMessage("commands.npc.edit-usage"));
        MessageUtils.send(sender, plugin.getMessage("commands.npc.tp-usage"));
        MessageUtils.send(sender, plugin.getMessage("commands.npc.action-usage"));
        MessageUtils.send(sender, plugin.getMessage("commands.loma.help-footer"));
    }

    private void sendNPCList(CommandSender sender) {
        MessageUtils.send(sender, plugin.getMessage("npc.list-header"));

        if (plugin.getNPCManager().getAllNPCs().isEmpty()) {
            MessageUtils.send(sender, plugin.getMessage("npc.list-empty"));
            return;
        }

        for (CustomNPC npc : plugin.getNPCManager().getAllNPCs()) {
            Location loc = npc.getLocation();
            String locationStr = String.format("%s: %.1f, %.1f, %.1f",
                    loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());

            MessageUtils.send(sender, plugin.getMessage("npc.list-entry")
                    .replace("{id}", String.valueOf(npc.getId()))
                    .replace("{name}", npc.getName())
                    .replace("{location}", locationStr));
        }
    }

    private void editNPC(CommandSender sender, String[] args) {
        CustomNPC npc = plugin.getNPCManager().getNPCByIdentifier(args[1]);
        if (npc == null) {
            MessageUtils.send(sender, plugin.getMessage("npc.not-found"));
            return;
        }

        String property = args[2].toLowerCase();
        String value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

            switch (property) {
                case "name":
                    npc.setName(value);
                    MessageUtils.send(sender, plugin.getMessage("npc.updated.name"));
                    break;

                case "skin":
                    npc.setSkinName(value);
                    MessageUtils.send(sender, plugin.getMessage("npc.updated.skin"));
                    break;

                case "permission":
                    npc.setPermission(value.equalsIgnoreCase("none") ? null : value);
                    MessageUtils.send(sender, plugin.getMessage("npc.updated.permission"));
                    break;

                case "cooldown":
                    int cd = Integer.parseInt(value);
                    if (cd < 0) throw new NumberFormatException();
                    npc.setCooldown(cd);
                    MessageUtils.send(sender, plugin.getMessage("npc.updated.cooldown"));
                    break;

                case "glowing":
                    Boolean glow = parseBooleanFlexible(value);
                    if (glow == null) {
                        MessageUtils.send(sender, plugin.getMessage("general.invalid-args").replace("{usage}", "/lmnpc edit <id|name> glowing <true|false>"));
                        return;
                    }
                    npc.setGlowing(glow);
                    MessageUtils.send(sender, plugin.getMessage("npc.updated.glowing"));
                    break;

                case "lookplayer":
                  Boolean lp = parseBooleanFlexible(value);
                  if (lp == null) {
                      MessageUtils.send(sender, plugin.getMessage("general.invalid-args").replace("{usage}", "/lmnpc edit <id|name> lookplayer <true|false>"));
                      return;
                  }
                  npc.setLookAtPlayer(lp);
                  MessageUtils.send(sender, plugin.getMessage("npc.updated.lookplayer"));
                  // Мгновенно повернём NPC при включении режима слежения
                  if (lp) {
                      try { plugin.getNPCManager().forceLookAtNearest(npc.getId()); } catch (Exception ignored) {}
                  }
                  break;

                default:
                    MessageUtils.send(sender, plugin.getMessage("errors.unknown-property").replace("{property}", property));
                    MessageUtils.send(sender, plugin.getMessage("npc.properties"));
                    return;
            }

            // Обновляем NPC в Citizens и сохраняем файл
            try {
                plugin.getNPCManager().updateNPCSettings(npc.getId());
                plugin.getNPCManager().saveNPCs();
            } catch (NumberFormatException ex) {
                MessageUtils.send(sender, plugin.getMessage("general.invalid-number"));
            } catch (Exception ex) {
                MessageUtils.send(sender, plugin.getMessage("errors.npc-change").replace("{error}", String.valueOf(ex.getMessage())));
            }
    }

    private void handleLookPlayerToggle(CommandSender sender, String idOrName, String value) {
        CustomNPC npc = plugin.getNPCManager().getNPCByIdentifier(idOrName);
        if (npc == null) {
            MessageUtils.send(sender, plugin.getMessage("npc.not-found"));
            return;
        }
        Boolean flag = parseBooleanFlexible(value);
        if (flag == null) {
            MessageUtils.send(sender, plugin.getMessage("general.invalid-args").replace("{usage}", "/lmnpc lookplayer <id|name> <true|false>"));
            return;
        }
        try {
            npc.setLookAtPlayer(flag);
            plugin.getNPCManager().updateNPCSettings(npc.getId());
            plugin.getNPCManager().saveNPCs();
            MessageUtils.send(sender, plugin.getMessage("npc.updated.lookplayer"));
            if (flag) {
                plugin.getNPCManager().forceLookAtNearest(npc.getId());
            }
        } catch (Exception ex) {
            MessageUtils.send(sender, plugin.getMessage("errors.npc-change").replace("{error}", String.valueOf(ex.getMessage())));
        }
    }

    private Boolean parseBooleanFlexible(String value) {
        String v = value.toLowerCase();
        if (v.equals("true") || v.equals("1") || v.equals("yes") || v.equals("on")) return true;
        if (v.equals("false") || v.equals("0") || v.equals("no") || v.equals("off")) return false;
        return null;
    }

    private void teleportToNPC(Player player, String idOrName) {
        CustomNPC npc = plugin.getNPCManager().getNPCByIdentifier(idOrName);
        if (npc == null) {
            MessageUtils.send(player, plugin.getMessage("npc.not-found"));
            return;
        }
        player.teleport(npc.getLocation());
        MessageUtils.send(player, plugin.getMessage("npc.teleported-to").replace("{name}", npc.getName()));
    }

    private void addAction(CommandSender sender, String[] args) {
        CustomNPC npc = plugin.getNPCManager().getNPCByIdentifier(args[1]);
        if (npc == null) {
            MessageUtils.send(sender, plugin.getMessage("npc.not-found"));
            return;
        }

        String actionType = args[2].toLowerCase();
        String[] actionArgs = Arrays.copyOfRange(args, 3, args.length);

        // Проверка существования типа действия
        if (!plugin.getNPCManager().getActions().containsKey(actionType)) {
            MessageUtils.send(sender, plugin.getMessage("errors.unknown-action").replace("{action}", actionType));
            MessageUtils.send(sender, plugin.getMessage("npc.actions.available").replace("{list}", String.join(", ", plugin.getNPCManager().getActions().keySet())));
            return;
        }

        npc.addAction(actionType, actionArgs);
        plugin.getNPCManager().saveNPCs();

        MessageUtils.send(sender, plugin.getMessage("npc.action-added"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("create", "remove", "list", "edit",
                    "tp", "action", "lookplayer", "reload");
            String input = args[0].toLowerCase();

            for (String sub : subCommands) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("remove") || subCommand.equals("edit") ||
                    subCommand.equals("tp") || subCommand.equals("action") || subCommand.equals("lookplayer")) {
                // Автодополнение идентификатора NPC: и ID, и имя
                String input = args[1].toLowerCase();
                for (CustomNPC npc : plugin.getNPCManager().getAllNPCs()) {
                    String id = String.valueOf(npc.getId());
                    if (id.startsWith(input)) completions.add(id);
                    if (npc.getName() != null && npc.getName().toLowerCase().startsWith(input)) completions.add(npc.getName());
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("edit")) {
                List<String> properties = Arrays.asList("name", "skin", "permission",
                        "cooldown", "glowing", "lookplayer");
                String input = args[2].toLowerCase();

                for (String prop : properties) {
                    if (prop.startsWith(input)) {
                        completions.add(prop);
                    }
                }
            } else if (args[0].equalsIgnoreCase("action")) {
                // Автодополнение типов действий
                String input = args[2].toLowerCase();
                for (String actionType : plugin.getNPCManager().getActions().keySet()) {
                    if (actionType.startsWith(input)) {
                        completions.add(actionType);
                    }
                }
            } else if (args[0].equalsIgnoreCase("lookplayer")) {
                completions.addAll(filterByPrefix(Arrays.asList("true", "false"), args[2]));
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("edit")) {
                String property = args[2].toLowerCase();
                String prefix = args[3].toLowerCase();
                switch (property) {
                    case "glowing":
                    case "lookplayer":
                        completions.addAll(filterByPrefix(Arrays.asList("true", "false", "1", "0"), prefix));
                        break;
                    case "cooldown":
                        completions.addAll(filterByPrefix(Arrays.asList("0", "1", "2", "3", "5", "10", "15"), prefix));
                        break;
                    case "permission":
                        completions.addAll(filterByPrefix(Arrays.asList("none", "loma.npc.use"), prefix));
                        break;
                    default:
                        break;
                }
            } else if (args[0].equalsIgnoreCase("action")) {
                // /lmnpc action <id> particle <animation>
                String actionType = args[2].toLowerCase();
                String prefix = args[3].toLowerCase();
                if (actionType.equals("particle")) {
                    for (String anim : plugin.getAnimationManager().getAnimationNames()) {
                        if (anim.toLowerCase().startsWith(prefix)) completions.add(anim);
                    }
                }
            }
        } else if (args.length == 5 && args[0].equalsIgnoreCase("action")) {
            // /lmnpc action <id> particle <animation> <particle>
            if (args[2].equalsIgnoreCase("particle")) {
                String prefix = args[4].toLowerCase();
                for (org.bukkit.Particle p : org.bukkit.Particle.values()) {
                    String name = p.name().toLowerCase();
                    if (name.startsWith(prefix)) completions.add(p.name());
                }
            }
        }

        return completions;
    }

    private List<String> filterByPrefix(List<String> options, String prefix) {
        String p = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(p)).collect(Collectors.toList());
    }
}