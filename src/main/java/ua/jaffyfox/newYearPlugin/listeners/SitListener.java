package ua.jaffyfox.newYearPlugin.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.block.Action;
import ua.jaffyfox.newYearPlugin.config.ConfigManager;
import ua.jaffyfox.newYearPlugin.fun.SitManager;

public class SitListener implements Listener {

    private final ConfigManager configManager;
    private final SitManager sitManager;

    public SitListener(ConfigManager configManager, SitManager sitManager) {
        this.configManager = configManager;
        this.sitManager = sitManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!sitManager.isEnabled()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!configManager.getConfig("SitConfig").getBoolean("right_click", true)) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("newyear.sit.use")) return;
        if (configManager.getConfig("SitConfig").getBoolean("require_empty_hand", false) && event.getItem() != null) return;
        // In Creative do not allow right-click sitting on slabs/stairs (command /sit still works)
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        // Only allow on top face
        if (event.getBlockFace() != BlockFace.UP) return;

        Material t = clicked.getType();
        if (t.isAir()) return;
        // Allow slabs and stairs; also allow any solid block if you want -- here we restrict to slabs/stairs only
        boolean ok = t.name().contains("SLAB") || t.name().contains("STAIRS");
        if (!ok) return;

        if (sitManager.trySitOnBlock(player, clicked)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player p = event.getPlayer();
        if (!configManager.getConfig("SitConfig").getBoolean("sneak_to_stand", true)) return;
        if (sitManager.isSitting(p)) {
            sitManager.stand(p);
            String msg = configManager.getConfig("SitConfig").getString("messages.stood_up", "§aВы встали.");
            if (msg != null && !msg.isEmpty()) p.sendMessage(msg);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sitManager.cleanup(event.getPlayer());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (sitManager.isSitting(event.getPlayer())) {
            sitManager.cleanup(event.getPlayer());
        }
    }
}
