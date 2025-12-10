package ua.jaffyfox.newYearPlugin.listeners;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import ua.jaffyfox.newYearPlugin.config.ConfigManager;
import ua.jaffyfox.newYearPlugin.fun.VomitManager;
import ua.jaffyfox.newYearPlugin.fun.PoopManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VomitListener implements Listener {

    private final ConfigManager configManager;
    private final VomitManager vomitManager;
    private final PoopManager poopManager;
    private final Map<UUID, Integer> counters = new HashMap<>();
    private final Map<UUID, Boolean> nextIsVomit = new HashMap<>();

    public VomitListener(ConfigManager configManager, VomitManager vomitManager, PoopManager poopManager) {
        this.configManager = configManager;
        this.vomitManager = vomitManager;
        this.poopManager = poopManager;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!vomitManager.isEnabled()) return;
        if (event.getItem() == null) return;
        Material type = event.getItem().getType();
        if (type == null || !type.isEdible()) return; // считаем только еду
        handleEat(event.getPlayer());
    }

    // Учёт торта: клик по блоку CAKE засчитывается как приём пищи
    @EventHandler(ignoreCancelled = true)
    public void onCakeBite(PlayerInteractEvent event) {
        if (!vomitManager.isEnabled()) return;
        if (event.getHand() != EquipmentSlot.HAND) return; // чтобы не удваивать с offhand
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.CAKE) return;
        handleEat(event.getPlayer());
    }

    // Не меняем уровень сытости от еды (сытость не тратится/не меняется при поедании)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        // Разрешаем естественное изменение, но никогда не даём подняться до 20 (всегда не хватает пол сытости)
        int newLevel = event.getFoodLevel();
        if (newLevel > 19) {
            event.setFoodLevel(19);
        }
    }

    // На входе в игру — если уровень 20, опускаем до 19
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (p.getFoodLevel() > 19) p.setFoodLevel(19);
        counters.put(p.getUniqueId(), counters.getOrDefault(p.getUniqueId(), 0));
        nextIsVomit.putIfAbsent(p.getUniqueId(), true);
    }

    private void handleEat(Player player) {
        UUID id = player.getUniqueId();
        int threshold = configManager.getConfig("VomitConfig").getInt("threshold", 5);
        int count = counters.getOrDefault(id, 0) + 1;
        counters.put(id, count);

        if (count < threshold) {
            String pattern = configManager.getConfig("VomitConfig").getString("messages.progress_actionbar", "§eПереедание: §6%count%§7/§6%threshold%");
            String text = pattern.replace("%count%", String.valueOf(count)).replace("%threshold%", String.valueOf(threshold));
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
            return;
        }

        counters.put(id, 0);
        boolean vomitNow = nextIsVomit.getOrDefault(id, true);
        nextIsVomit.put(id, !vomitNow);

        if (vomitNow) {
            String chat = configManager.getConfig("VomitConfig").getString("messages.trigger_chat", "%player% мне что-то плохо...");
            if (chat != null && !chat.isEmpty()) {
                player.getServer().broadcastMessage(chat.replace("%player%", "<" + player.getName() + ">"));
            }
            vomitManager.startVomit(player);
        } else {
            String chat = configManager.getConfig("PoopConfig").getString("messages.trigger_chat", "%player% кажется, у меня понос...");
            if (chat != null && !chat.isEmpty()) {
                player.getServer().broadcastMessage(chat.replace("%player%", "<" + player.getName() + ">"));
            }
            poopManager.startPoop(player);
        }
    }
}
