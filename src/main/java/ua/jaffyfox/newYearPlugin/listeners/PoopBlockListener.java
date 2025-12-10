package ua.jaffyfox.newYearPlugin.listeners;

import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import ua.jaffyfox.newYearPlugin.config.ConfigManager;
import ua.jaffyfox.newYearPlugin.fun.PoopManager;

import java.util.Iterator;

public class PoopBlockListener implements Listener {

    private final ConfigManager configManager;
    private final PoopManager poopManager;

    public PoopBlockListener(ConfigManager configManager, PoopManager poopManager) {
        this.configManager = configManager;
        this.poopManager = poopManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        FileConfiguration cfg = configManager.getConfig("PoopConfig");
        boolean unbreakableWater = cfg.getBoolean("water.unbreakable", true);
        boolean groundNoDrops = cfg.getBoolean("ground.no_drops", true);

        if (poopManager.isPoopWater(block) && unbreakableWater) {
            event.setCancelled(true);
            return;
        }
        if (poopManager.isPoopGround(block) && groundNoDrops) {
            event.setDropItems(false);
            poopManager.removePoopGround(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        Block block = event.getBlock();
        boolean groundNoDrops = configManager.getConfig("PoopConfig").getBoolean("ground.no_drops", true);
        if (groundNoDrops && poopManager.isPoopGround(block)) {
            Iterator<org.bukkit.entity.Item> it = event.getItems().iterator();
            while (it.hasNext()) {
                org.bukkit.entity.Item item = it.next();
                item.remove();
                it.remove();
            }
            poopManager.removePoopGround(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        boolean unbreakableWater = configManager.getConfig("PoopConfig").getBoolean("water.unbreakable", true);
        if (unbreakableWater) {
            event.blockList().removeIf(poopManager::isPoopWater);
        }
        // Clean up ground registry for exploded blocks
        event.blockList().forEach(b -> { if (poopManager.isPoopGround(b)) poopManager.removePoopGround(b); });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        boolean unbreakableWater = configManager.getConfig("PoopConfig").getBoolean("water.unbreakable", true);
        if (unbreakableWater) {
            event.blockList().removeIf(poopManager::isPoopWater);
        }
        event.blockList().forEach(b -> { if (poopManager.isPoopGround(b)) poopManager.removePoopGround(b); });
    }
}
