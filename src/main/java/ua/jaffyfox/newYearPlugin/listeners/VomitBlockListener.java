package ua.jaffyfox.newYearPlugin.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.block.Block;
import ua.jaffyfox.newYearPlugin.fun.VomitManager;

import java.util.Iterator;

public class VomitBlockListener implements Listener {

    private final VomitManager vomitManager;

    public VomitBlockListener(VomitManager vomitManager) {
        this.vomitManager = vomitManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        // Unbreakable vomit glass
        if (vomitManager.isVomitGlass(block)) {
            event.setCancelled(true);
            return;
        }
        // Vomit carpet: allow breaking but no drops
        if (vomitManager.isVomitCarpet(block)) {
            event.setDropItems(false);
            vomitManager.removeVomitCarpet(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        Block block = event.getBlock();
        if (vomitManager.isVomitCarpet(block)) {
            Iterator<org.bukkit.entity.Item> it = event.getItems().iterator();
            while (it.hasNext()) {
                org.bukkit.entity.Item item = it.next();
                item.remove();
                it.remove();
            }
            vomitManager.removeVomitCarpet(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(vomitManager::isVomitGlass);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(vomitManager::isVomitGlass);
    }
}
