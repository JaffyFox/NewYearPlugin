package ua.jaffyfox.newYearPlugin.fun;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import ua.jaffyfox.newYearPlugin.config.ConfigManager;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.FluidCollisionMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.RayTraceResult;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class VomitManager {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    // Registries for transformed blocks
    private final Set<String> vomitGlassBlocks = new HashSet<>();
    private final Set<String> vomitCarpetBlocks = new HashSet<>();
    private final Map<String, BlockData> originalData = new HashMap<>();

    public VomitManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public boolean isEnabled() {
        return configManager.getConfig("VomitConfig").getBoolean("enabled", true);
    }

    public void startVomit(Player player) {
        final var cfg = configManager.getConfig("VomitConfig");
        int durationTicks = cfg.getInt("duration_seconds", 4) * 20;
        int perTick = cfg.getInt("drops.per_tick", 6);
        int tickPeriod = cfg.getInt("tick_period", 2);
        double forwardOffset = cfg.getDouble("spawn.forward_offset", 0.35);
        double spread = cfg.getDouble("spawn.spread", 0.10);
        double forwardSpeed = cfg.getDouble("spawn.forward_speed", 0.18);
        double downSpeed = cfg.getDouble("spawn.down_speed", -0.20);
        // interactions
        double rayMaxDist = cfg.getDouble("interactions.raytrace_max_distance", 5.0);
        int samplesPerTick = Math.max(1, cfg.getInt("interactions.samples_per_tick", 3));
        boolean interactWater = cfg.getBoolean("water.enabled", true);
        boolean interactSnow = cfg.getBoolean("snow.enabled", true);

        // Effects (optional)
        if (cfg.getBoolean("effects.nausea.enabled", true)) {
            int amp = Math.max(0, cfg.getInt("effects.nausea.amplifier", 1) - 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, durationTicks, amp, false, false));
        }
        if (cfg.getBoolean("effects.slowness.enabled", false)) {
            int amp = Math.max(0, cfg.getInt("effects.slowness.amplifier", 1) - 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, amp, false, false));
        }

        // Start text and sound
        String msg = cfg.getString("messages.vomit_effect", "§eВас стошнило...");
        if (msg != null && !msg.isEmpty()) player.sendMessage(msg);
        if (cfg.getBoolean("sound.enabled", false)) {
            String key = cfg.getString("sound.name", "minecraft:entity.player.burp");
            float vol = (float) cfg.getDouble("sound.volume", 1.0);
            float pitch = (float) cfg.getDouble("sound.pitch", 0.9);
            try { player.playSound(player.getLocation(), key, vol, pitch); } catch (Exception ignored) {}
        }

        List<String> materialNames = cfg.getStringList("drops.materials");
        Material[] materials;
        if (materialNames == null || materialNames.isEmpty()) {
            materials = new Material[]{
                    Material.MOSS_BLOCK,
                    Material.SLIME_BLOCK,
                    Material.GREEN_WOOL,
                    Material.LIME_WOOL,
                    Material.LIME_TERRACOTTA,
                    Material.GREEN_TERRACOTTA,
                    Material.LIME_CONCRETE,
                    Material.GREEN_CONCRETE,
                    Material.LIME_CONCRETE_POWDER,
                    Material.GREEN_CONCRETE_POWDER,
                    Material.LIME_STAINED_GLASS,
                    Material.GREEN_STAINED_GLASS,
                    Material.ORANGE_STAINED_GLASS,
                    Material.BROWN_STAINED_GLASS,
                    Material.BROWN_CONCRETE_POWDER,
                    Material.ORANGE_CONCRETE_POWDER
            };
        } else {
            materials = materialNames.stream().map(name -> {
                try { return Material.valueOf(name); } catch (IllegalArgumentException e) { return null; }
            }).filter(m -> m != null).toArray(Material[]::new);
            if (materials.length == 0) {
                materials = new Material[]{Material.SLIME_BLOCK, Material.GREEN_WOOL, Material.LIME_WOOL};
            }
        }

        final Material[] mats = materials;
        new BukkitRunnable() {
            int elapsed = 0;
            final ThreadLocalRandom rnd = ThreadLocalRandom.current();
            @Override
            public void run() {
                if (!player.isOnline() || elapsed >= durationTicks) {
                    cancel();
                    return;
                }
                // Spawn from mouth: near eye height, a bit forward
                Location base = player.getEyeLocation().clone();
                base.setY(base.getY() - 0.2);
                Vector forward = player.getLocation().getDirection().setY(0).normalize();
                if (Double.isNaN(forward.length())) forward = new Vector(0, 0, 0);
                base.add(forward.clone().multiply(forwardOffset));

                for (int i = 0; i < perTick; i++) {
                    Vector rand = new Vector(rnd.nextDouble(-spread, spread), 0, rnd.nextDouble(-spread, spread));
                    Vector velocity = forward.clone().multiply(forwardSpeed).add(rand).setY(downSpeed);
                    Material m = mats[rnd.nextInt(mats.length)];
                    Item item = player.getWorld().dropItem(base, new ItemStack(m));
                    item.setPickupDelay(Integer.MAX_VALUE);
                    item.setVelocity(velocity);
                    plugin.getServer().getScheduler().runTaskLater(plugin, item::remove, 300L);
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

        // Slight downward bias for vomit trajectory
        Vector dir = player.getLocation().getDirection().normalize();
        if (Double.isNaN(dir.length())) dir = new Vector(0, -0.2, 0);
        dir = dir.clone();
        if (dir.getY() > -0.1) dir.setY(dir.getY() - 0.2);

        double radius = configManager.getConfig("VomitConfig").getDouble("spawn_radius", 2.0);

        // Slightly forward from mouth
        Location origin = player.getEyeLocation().clone().add(player.getLocation().getDirection().multiply(0.4));
        for (int i = 0; i < samplesPerTick; i++) {
            double rx = rnd.nextDouble(-radius, radius);
            double rz = rnd.nextDouble(-radius, radius);
            Location start = origin.clone().add(rx, -0.1, rz);

            // Trace for fluids (ignore passables) and for passables (no fluids)
            RayTraceResult rFluids = world.rayTraceBlocks(start, dir, rayMaxDist, FluidCollisionMode.ALWAYS, true);
            double dFluids = rFluids != null ? rFluids.getHitPosition().distance(start.toVector()) : Double.POSITIVE_INFINITY;
            RayTraceResult rPass = world.rayTraceBlocks(start, dir, rayMaxDist, FluidCollisionMode.NEVER, false);
            double dPass = rPass != null ? rPass.getHitPosition().distance(start.toVector()) : Double.POSITIVE_INFINITY;

            RayTraceResult result = dFluids <= dPass ? rFluids : rPass;
            if (result == null) continue;
            Block hit = result.getHitBlock();
            if (hit == null) continue;

            Material type = hit.getType();
            if (interactWater && type == Material.WATER) {
                Block top = hit;
                while (top.getY() < world.getMaxHeight() && top.getRelative(org.bukkit.block.BlockFace.UP).getType() == Material.WATER) {
                    top = top.getRelative(org.bukkit.block.BlockFace.UP);
                }
                transformWaterToGlass(top, rnd);
            } else if (interactSnow && (type == Material.SNOW || type == Material.SNOW_BLOCK)) {
                transformSnowToCarpet(hit, rnd);
            }
        }
    }

    private void transformWaterToGlass(Block block, ThreadLocalRandom rnd) {
        final var cfg = configManager.getConfig("VomitConfig");
        List<String> names = cfg.getStringList("water.glass_materials");
        Material[] options;
        if (names == null || names.isEmpty()) {
            options = new Material[]{Material.GREEN_STAINED_GLASS, Material.LIME_STAINED_GLASS};
        } else {
            options = names.stream().map(n -> { try { return Material.valueOf(n); } catch (Exception e) { return null; } })
                    .filter(m -> m != null).toArray(Material[]::new);
            if (options.length == 0) options = new Material[]{Material.GREEN_STAINED_GLASS, Material.LIME_STAINED_GLASS};
        }
        boolean unbreakable = cfg.getBoolean("water.unbreakable", true);
        int minSec = Math.max(1, cfg.getInt("water.revert_min_seconds", 10));
        int maxSec = Math.max(minSec, cfg.getInt("water.revert_max_seconds", 25));

        String key = locKey(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (vomitGlassBlocks.contains(key)) return;

        BlockData prev = block.getBlockData().clone();
        originalData.put(key, prev);

        block.setType(options[rnd.nextInt(options.length)], false);
        if (unbreakable) vomitGlassBlocks.add(key);

        int delaySec = rnd.nextInt(minSec, maxSec + 1);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!block.getType().name().contains("GLASS")) {
                vomitGlassBlocks.remove(key);
                originalData.remove(key);
                return;
            }
            BlockData data = originalData.remove(key);
            vomitGlassBlocks.remove(key);
            if (data != null) block.setBlockData(data, false);
            else block.setType(Material.WATER, false);
        }, delaySec * 20L);
    }

    private void transformSnowToCarpet(Block block, ThreadLocalRandom rnd) {
        final var cfg = configManager.getConfig("VomitConfig");
        List<String> names = cfg.getStringList("snow.carpet_materials");
        Material[] options;
        if (names == null || names.isEmpty()) {
            options = new Material[]{Material.GREEN_CARPET, Material.LIME_CARPET};
        } else {
            options = names.stream().map(n -> { try { return Material.valueOf(n); } catch (Exception e) { return null; } })
                    .filter(m -> m != null).toArray(Material[]::new);
            if (options.length == 0) options = new Material[]{Material.GREEN_CARPET, Material.LIME_CARPET};
        }

        String key = locKey(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (vomitCarpetBlocks.contains(key)) return;

        block.setType(options[rnd.nextInt(options.length)], false);
        vomitCarpetBlocks.add(key);
    }

    private String locKey(World world, int x, int y, int z) {
        return world.getUID().toString() + ":" + x + ":" + y + ":" + z;
    }

    public boolean isVomitGlass(Block block) {
        String key = locKey(block.getWorld(), block.getX(), block.getY(), block.getZ());
        return vomitGlassBlocks.contains(key);
    }

    public boolean isVomitCarpet(Block block) {
        String key = locKey(block.getWorld(), block.getX(), block.getY(), block.getZ());
        return vomitCarpetBlocks.contains(key);
    }

    public void removeVomitCarpet(Block block) {
        String key = locKey(block.getWorld(), block.getX(), block.getY(), block.getZ());
        vomitCarpetBlocks.remove(key);
    }
}
