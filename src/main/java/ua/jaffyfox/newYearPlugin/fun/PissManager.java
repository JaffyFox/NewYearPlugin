package ua.jaffyfox.newYearPlugin.fun;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import ua.jaffyfox.newYearPlugin.config.ConfigManager;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.util.Vector;
import org.bukkit.FluidCollisionMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.RayTraceResult;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PissManager {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    // Registries for transformed blocks
    private final Set<String> pissGlassBlocks = new HashSet<>();
    private final Set<String> pissCarpetBlocks = new HashSet<>();
    private final Map<String, BlockData> originalData = new HashMap<>();

    public PissManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public boolean isEnabled() {
        FileConfiguration cfg = configManager.getConfig("PissConfig");
        return cfg.getBoolean("enabled", true);
    }

    public void startUrination(Player player) {
        FileConfiguration cfg = configManager.getConfig("PissConfig");
        int durationTicks = cfg.getInt("duration_seconds", 5) * 20;
        int tickPeriod = cfg.getInt("tick_period", 2);
        int perTick = cfg.getInt("drops.per_tick", 5);
        double forwardOffset = cfg.getDouble("spawn.forward_offset", 0.7);
        double spreadCfg = cfg.getDouble("spawn.spread", 0.08);
        double forwardSpeed = cfg.getDouble("spawn.forward_speed", 0.18);
        double downSpeed = cfg.getDouble("spawn.down_speed", -0.08);
        // interactions
        double rayMaxDist = cfg.getDouble("interactions.raytrace_max_distance", 8.0);
        int samplesPerTick = Math.max(1, cfg.getInt("interactions.samples_per_tick", 6));
        boolean interactWater = cfg.getBoolean("water.enabled", true);
        boolean interactSnow = cfg.getBoolean("snow.enabled", true);

        // Effects
        if (cfg.getBoolean("effects.nausea.enabled", true)) {
            int amp = Math.max(0, cfg.getInt("effects.nausea.amplifier", 1) - 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, durationTicks, amp, false, false));
        }
        if (cfg.getBoolean("effects.slowness.enabled", true)) {
            int amp = Math.max(0, cfg.getInt("effects.slowness.amplifier", 1) - 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, amp, false, false));
        }

        // Start message and sound
        String startMsg = cfg.getString("messages.piss_effect", "§6§lПроцесс мочеиспускания...");
        if (startMsg != null && !startMsg.isEmpty()) player.sendMessage(startMsg);
        if (cfg.getBoolean("sound.enabled", false)) {
            String key = cfg.getString("sound.name", "minecraft:entity.player.burp");
            float vol = (float) cfg.getDouble("sound.volume", 1.0);
            float pitch = (float) cfg.getDouble("sound.pitch", 1.0);
            boolean played = false;
            try {
                player.playSound(player.getLocation(), key, vol, pitch);
                played = true;
            } catch (Exception ignored) { }
            if (!played) {
                playLegacySound(player, cfg.getString("sound.name", "ENTITY_PLAYER_BURP"), vol, pitch);
            }
        }

        List<String> materialNames = cfg.getStringList("drops.materials");
        Material[] materials;
        if (materialNames == null || materialNames.isEmpty()) {
            materials = new Material[]{Material.YELLOW_WOOL, Material.YELLOW_STAINED_GLASS, Material.YELLOW_CONCRETE};
        } else {
            materials = materialNames.stream()
                    .map(name -> {
                        try { return Material.valueOf(name); } catch (IllegalArgumentException e) { return null; }
                    })
                    .filter(m -> m != null)
                    .toArray(Material[]::new);
            if (materials.length == 0) {
                materials = new Material[]{Material.YELLOW_WOOL, Material.YELLOW_STAINED_GLASS, Material.YELLOW_CONCRETE};
            }
        }

        final Material[] finalMaterials = materials;
        new BukkitRunnable() {
            int elapsed = 0;
            final ThreadLocalRandom rnd = ThreadLocalRandom.current();
            @Override
            public void run() {
                if (!player.isOnline() || elapsed >= durationTicks) {
                    cancel();
                    return;
                }

                // Compute base and forward each tick
                Location base = player.getLocation().clone();
                base.setY(Math.floor(base.getY()) + 0.2);
                Vector forward = player.getLocation().getDirection().setY(0).normalize();
                if (Double.isNaN(forward.length())) {
                    forward = new Vector(0, 0, 0);
                }
                base.add(forward.clone().multiply(forwardOffset));

                // Drops (visual stream)
                if (cfg.getBoolean("drops.enabled", true)) {
                    for (int i = 0; i < perTick; i++) {
                        double spread = spreadCfg;
                        Vector rand = new Vector(rnd.nextDouble(-spread, spread), 0, rnd.nextDouble(-spread, spread));
                        Vector velocity = forward.clone().multiply(forwardSpeed).add(rand).setY(downSpeed);

                        Material m = finalMaterials[rnd.nextInt(finalMaterials.length)];
                        ItemStack stack = new ItemStack(m);
                        Item item = player.getWorld().dropItem(base, stack);
                        item.setPickupDelay(Integer.MAX_VALUE);
                        item.setVelocity(velocity);
                        plugin.getServer().getScheduler().runTaskLater(plugin, item::remove, 300L);
                    }
                }

                // Interactions with blocks (water/snow)
                handleBlockInteractions(player, base, forward, rayMaxDist, samplesPerTick, interactWater, interactSnow, rnd);

                elapsed += tickPeriod;
            }
        }.runTaskTimer(plugin, 0L, tickPeriod);
    }

    private void handleBlockInteractions(Player player,
                                         Location base,
                                         Vector forwardFlat,
                                         double rayMaxDist,
                                         int samplesPerTick,
                                         boolean interactWater,
                                         boolean interactSnow,
                                         ThreadLocalRandom rnd) {
        World world = player.getWorld();

        // Slight downward bias for the pee trajectory
        Vector dir = player.getLocation().getDirection().normalize();
        if (Double.isNaN(dir.length())) dir = new Vector(0, -0.15, 0);
        if (dir.getY() > -0.1) dir.setY(dir.getY() - 0.15);

        double radius = configManager.getConfig("PissConfig").getDouble("spawn_radius", 1.6);

        // Start rays from a point around the player's pelvis but slightly elevated and forward
        Location origin = player.getEyeLocation().clone().add(player.getLocation().getDirection().multiply(0.5)).add(0, -0.4, 0);
        for (int i = 0; i < samplesPerTick; i++) {
            double rx = rnd.nextDouble(-radius, radius);
            double rz = rnd.nextDouble(-radius, radius);
            Location start = origin.clone().add(rx, 0, rz);

            // Trace 1: ignore passables, include fluids (good for water)
            RayTraceResult rFluids = world.rayTraceBlocks(start, dir, rayMaxDist, FluidCollisionMode.ALWAYS, false);
            double dFluids = rFluids != null ? rFluids.getHitPosition().distance(start.toVector()) : Double.POSITIVE_INFINITY;

            // Trace 2: include passables, do not consider fluids (good for snow layer and carpets)
            RayTraceResult rPass = world.rayTraceBlocks(start, dir, rayMaxDist, FluidCollisionMode.NEVER, false);
            double dPass = rPass != null ? rPass.getHitPosition().distance(start.toVector()) : Double.POSITIVE_INFINITY;

            RayTraceResult result = dFluids <= dPass ? rFluids : rPass;
            if (result == null) continue;
            Block hit = result.getHitBlock();
            if (hit == null) continue;

            Material type = hit.getType();
            if (interactWater && type == Material.WATER) {
                Block top = hit;
                while (top.getY() < world.getMaxHeight() && top.getRelative(BlockFace.UP).getType() == Material.WATER) {
                    top = top.getRelative(BlockFace.UP);
                }
                transformWaterToGlass(top);
            } else if (interactSnow && (type == Material.SNOW || type == Material.SNOW_BLOCK)) {
                transformSnowToCarpet(hit);
            }
        }
    }

    private void transformWaterToGlass(Block block) {
        FileConfiguration cfg = configManager.getConfig("PissConfig");
        Material glassMat;
        try {
            glassMat = Material.valueOf(cfg.getString("water.glass_material", "YELLOW_STAINED_GLASS"));
        } catch (Exception e) {
            glassMat = Material.YELLOW_STAINED_GLASS;
        }
        boolean unbreakable = cfg.getBoolean("water.unbreakable", true);
        int minSec = Math.max(1, cfg.getInt("water.revert_min_seconds", 10));
        int maxSec = Math.max(minSec, cfg.getInt("water.revert_max_seconds", 25));

        String key = locKey(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (pissGlassBlocks.contains(key)) return; // already transformed

        // Save original block data for reversion
        BlockData prev = block.getBlockData().clone();
        originalData.put(key, prev);

        block.setType(glassMat, false);
        if (unbreakable) pissGlassBlocks.add(key);

        // Schedule reversion
        int delaySec = ThreadLocalRandom.current().nextInt(minSec, maxSec + 1);
        long delayTicks = delaySec * 20L;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // If block changed by player, just cleanup
            if (!block.getType().name().contains("GLASS")) {
                pissGlassBlocks.remove(key);
                originalData.remove(key);
                return;
            }
            BlockData data = originalData.remove(key);
            pissGlassBlocks.remove(key);
            if (data != null) {
                block.setBlockData(data, false);
            } else {
                block.setType(Material.WATER, false);
            }
        }, delayTicks);
    }

    private void transformSnowToCarpet(Block block) {
        FileConfiguration cfg = configManager.getConfig("PissConfig");
        Material carpetMat;
        try {
            carpetMat = Material.valueOf(cfg.getString("snow.carpet_material", "YELLOW_CARPET"));
        } catch (Exception e) {
            carpetMat = Material.YELLOW_CARPET;
        }

        String key = locKey(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (pissCarpetBlocks.contains(key)) return; // already transformed

        block.setType(carpetMat, false); // no reversion
        pissCarpetBlocks.add(key);
    }

    private String locKey(World world, int x, int y, int z) {
        return world.getUID().toString() + ":" + x + ":" + y + ":" + z;
    }

    public boolean isPissGlass(Block block) {
        String key = locKey(block.getWorld(), block.getX(), block.getY(), block.getZ());
        return pissGlassBlocks.contains(key);
    }

    public boolean isPissCarpet(Block block) {
        String key = locKey(block.getWorld(), block.getX(), block.getY(), block.getZ());
        return pissCarpetBlocks.contains(key);
    }

    public void removePissCarpet(Block block) {
        String key = locKey(block.getWorld(), block.getX(), block.getY(), block.getZ());
        pissCarpetBlocks.remove(key);
    }

    @SuppressWarnings({"deprecation", "removal"})
    private void playLegacySound(Player player, String enumName, float volume, float pitch) {
        try {
            org.bukkit.Sound s = org.bukkit.Sound.valueOf(enumName);
            player.playSound(player.getLocation(), s, volume, pitch);
        } catch (Exception ignored) { }
    }
}
