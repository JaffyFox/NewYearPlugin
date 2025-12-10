package ua.jaffyfox.newYearPlugin.listeners;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ua.jaffyfox.newYearPlugin.config.ConfigManager;
import ua.jaffyfox.newYearPlugin.fun.PissManager;
import java.util.HashMap;
import java.util.Map;

public class PissListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PissManager pissManager;
    private final Map<Player, Integer> waterDrink = new HashMap<>();
    private final Map<Player, Boolean> warned = new HashMap<>();

    public PissListener(JavaPlugin plugin, ConfigManager configManager, PissManager pissManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.pissManager = pissManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        waterDrink.put(p, 0);
        warned.put(p, false);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        waterDrink.remove(p);
        warned.remove(p);
    }

    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        if (!pissManager.isEnabled()) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item.getType() == Material.POTION) {
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                return;
            }
            int value = configManager.getConfig("PissConfig").getInt("intake.water_bottle", 1);
            addWater(player, value);
        } else if (item.getType() == Material.WATER_BUCKET) {
            int value = configManager.getConfig("PissConfig").getInt("intake.water_bucket", 2);
            addWater(player, value);
        }
    }

    private void addWater(Player player, int amount) {
        int current = waterDrink.getOrDefault(player, 0);
        current += amount;
        waterDrink.put(player, current);

        int capacity = configManager.getConfig("PissConfig").getInt("capacity", 5);
        int warningAt = configManager.getConfig("PissConfig").getInt("warning_at", Math.max(1, (int) Math.floor(capacity * 0.8)));

        // Action bar progress (like vomit)
        String pattern = configManager.getConfig("PissConfig").getString("messages.progress_actionbar", "§eПузырь: §6%current%§7/§6%capacity%");
        String text = pattern.replace("%current%", String.valueOf(current)).replace("%capacity%", String.valueOf(capacity));
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));

        if (current >= capacity) {
            // Broadcast trigger message (like vomit)
            String chat = configManager.getConfig("PissConfig").getString("messages.trigger_chat", "%player% мне очень нужно в туалет...");
            if (chat != null && !chat.isEmpty()) {
                player.getServer().broadcastMessage(chat.replace("%player%", "<" + player.getName() + ">"));
            }
            pissManager.startUrination(player);
            waterDrink.put(player, 0);
            warned.put(player, false);
            return;
        }

        if (current >= warningAt && !warned.getOrDefault(player, false)) {
            String msg = configManager.getConfig("PissConfig").getString("messages.piss_warning", "§c§lУ вас слишком переполненный мочевой пузырь!");
            if (msg != null && !msg.isEmpty()) player.sendMessage(msg);
            warned.put(player, true);
        }
    }
}