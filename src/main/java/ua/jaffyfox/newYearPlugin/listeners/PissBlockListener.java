package ua.jaffyfox.newYearPlugin.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ua.jaffyfox.newYearPlugin.fun.PissManager;

import java.util.Iterator;

public class PissBlockListener implements Listener {

    private final PissManager pissManager;

    public PissBlockListener(PissManager pissManager) {
        this.pissManager = pissManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        // Unbreakable piss glass
        if (pissManager.isPissGlass(block)) {
            event.setCancelled(true);
            return;
        }
        // Piss carpet: allow breaking but no drops
        if (pissManager.isPissCarpet(block)) {
            event.setDropItems(false);
            pissManager.removePissCarpet(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        Block block = event.getBlock();
        if (pissManager.isPissCarpet(block)) {
            // Remove all drops to ensure nothing falls
            Iterator<org.bukkit.entity.Item> it = event.getItems().iterator();
            while (it.hasNext()) {
                org.bukkit.entity.Item item = it.next();
                ItemStack stack = item.getItemStack();
                // Remove any item (carpet or else)
                item.remove();
                it.remove();
            }
            pissManager.removePissCarpet(block);
        }
    }

    // Protect unbreakable glass from explosions
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(pissManager::isPissGlass);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(pissManager::isPissGlass);
    }
}
