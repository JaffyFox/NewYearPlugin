package ua.jaffyfox.newYearPlugin.fun;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import ua.jaffyfox.newYearPlugin.config.ConfigManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SitManager {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Map<UUID, ArmorStand> seats = new HashMap<>();

    public SitManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public boolean isEnabled() { return configManager.getConfig("SitConfig").getBoolean("enabled", true); }

    public boolean isSitting(Player p) { return seats.containsKey(p.getUniqueId()); }

    public boolean stand(Player player) {
        ArmorStand as = seats.remove(player.getUniqueId());
        if (as != null && !as.isDead()) {
            // Ensure player dismounted
            if (player.getVehicle() != null && player.getVehicle().getUniqueId().equals(as.getUniqueId())) {
                as.removePassenger(player);
            }
            as.remove();
            return true;
        }
        return false;
    }

    public boolean trySitOnBlock(Player player, Block block) {
        if (!canSit(player)) return false;
        // require headroom
        Block above = block.getRelative(org.bukkit.block.BlockFace.UP);
        if (!above.isPassable()) return false;
        double offset = offsetFor(block);
        if (offset < -0.5) return false; // unsupported
        Location loc = center(block.getLocation());
        loc.add(0, offset, 0);
        loc.setYaw(player.getLocation().getYaw());
        return spawnSeat(player, loc);
    }

    public boolean trySitAnywhere(Player player) {
        if (!canSit(player)) return false;
        Block under = player.getLocation().clone().subtract(0, 0.1, 0).getBlock();
        // find solid ground up to 3 blocks below
        World w = player.getWorld();
        Block base = under;
        for (int i = 0; i < 3; i++) {
            if (base.getType().isAir()) {
                base = w.getBlockAt(base.getX(), base.getY() - 1, base.getZ());
            } else break;
        }
        if (base.getType().isAir()) return false;
        // If standing on slab or stairs — use their offsets directly
        String name = base.getType().name();
        if (name.contains("SLAB") || name.contains("STAIRS")) {
            return trySitOnBlock(player, base);
        }
        // For full blocks: sit on TOP of the block plus small offset
        double topOffset = configManager.getConfig("SitConfig").getDouble("offsets.anywhere_top", 0.20);
        // require headroom
        Block above = base.getRelative(org.bukkit.block.BlockFace.UP);
        if (!above.isPassable()) return false;
        Location loc = center(base.getLocation()).add(0, 1.0 + topOffset, 0);
        loc.setYaw(player.getLocation().getYaw());
        return spawnSeat(player, loc);
    }

    private boolean spawnSeat(Player player, Location loc) {
        FileConfiguration cfg = configManager.getConfig("SitConfig");
        if (!cfg.getBoolean("enabled", true)) return false;
        if (isSitting(player)) return true; // already
        if (player.isInsideVehicle() || player.isSleeping() || player.isDead()) return false;

        ArmorStand seat = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setBasePlate(false);
            as.setSmall(false);
            try { as.setCollidable(false); } catch (NoSuchMethodError ignored) {}
            as.setCustomNameVisible(false);
            as.setPersistent(false);
        });
        if (!seat.addPassenger(player)) {
            seat.remove();
            return false;
        }
        seats.put(player.getUniqueId(), seat);

        if (cfg.getBoolean("sound.enabled", false)) {
            String key = cfg.getString("sound.name", "minecraft:block.wood.place");
            float vol = (float) cfg.getDouble("sound.volume", 1.0);
            float pitch = (float) cfg.getDouble("sound.pitch", 1.0);
            try { player.playSound(loc, key, vol, pitch); } catch (Exception ignored) {}
        }
        String msg = cfg.getString("messages.started_sitting", "§aВы сели.");
        if (msg != null && !msg.isEmpty()) player.sendMessage(msg);
        return true;
    }

    private boolean canSit(Player player) {
        if (!isEnabled()) return false;
        if (isSitting(player)) return false;
        if (player.getVehicle() != null) return false;
        if (player.isFlying() || player.isGliding() || player.isSwimming()) return false;
        return true;
    }

    private double offsetFor(Block block) {
        FileConfiguration cfg = configManager.getConfig("SitConfig");
        if (block == null) return cfg.getDouble("offsets.default", 0.45);
        Material t = block.getType();
        if (t == Material.AIR) return -1;
        var data = block.getBlockData();
        if (data instanceof Slab slab) {
            if (slab.getType() == Slab.Type.DOUBLE) return -1; // avoid double slabs
            if (slab.getType() == Slab.Type.TOP) return cfg.getDouble("offsets.slab_top", 0.95);
            return cfg.getDouble("offsets.slab_bottom", 0.45);
        }
        if (data instanceof Stairs stairs) {
            if (stairs.getHalf() == Bisected.Half.TOP) return cfg.getDouble("offsets.slab_top", 0.95);
            return cfg.getDouble("offsets.stairs", 0.52);
        }
        return cfg.getDouble("offsets.default", 0.45);
    }

    private Location center(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX() + 0.5, loc.getBlockY(), loc.getBlockZ() + 0.5);
    }

    public void cleanup(Player player) { stand(player); }
}
