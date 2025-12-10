package ua.jaffyfox.newYearPlugin.fun;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import ua.jaffyfox.newYearPlugin.config.ConfigManager;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class PoopManager {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    private final Set<String> poopWaterBlocks = new HashSet<>();
    private final Set<String> poopGroundBlocks = new HashSet<>();
    private final Map<String, BlockData> originalData = new HashMap<>();

    public PoopManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public boolean isEnabled() {
        return configManager.getConfig("PoopConfig").getBoolean("enabled", true);
    }

    public void startPoop(Player player) {
        final FileConfiguration cfg = configManager.getConfig("PoopConfig");
        int durationTicks = cfg.getInt("duration_seconds", 4) * 20;
        int tickPeriod = cfg.getInt("tick_period", 2);
        int perTick = cfg.getInt("drops.per_tick", 5);
        double backwardOffset = cfg.getDouble("spawn.backward_offset", 0.45);
        double spread = cfg.getDouble("spawn.spread", 0.10);
        double backSpeed = cfg.getDouble("spawn.back_speed", 0.14);
        double downSpeed = cfg.getDouble("spawn.down_speed", -0.22);

        // interactions
        double rayMaxDist = cfg.getDouble("interactions.raytrace_max_distance", 4.5);
        int samplesPerTick = Math.max(1, cfg.getInt("interactions.samples_per_tick", 3));
        boolean interactWater = cfg.getBoolean("water.enabled", true);
        boolean interactGround = cfg.getBoolean("ground.enabled", true);

        // optional effects
        if (cfg.getBoolean("effects.nausea.enabled", false)) {
            int amp = Math.max(0, cfg.getInt("effects.nausea.amplifier", 1) - 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, durationTicks, amp, false, false));
        }

        // start text and sound
        String msg = cfg.getString("messages.poop_effect", "§6§lУ вас понос...");
        if (msg != null && !msg.isEmpty()) player.sendMessage(msg);
        if (cfg.getBoolean("sound.enabled", false)) {
            String key = cfg.getString("sound.name", "minecraft:entity.player.burp");
            float vol = (float) cfg.getDouble("sound.volume", 0.8);
            float pitch = (float) cfg.getDouble("sound.pitch", 0.8);
            try { player.playSound(player.getLocation(), key, vol, pitch); } catch (Exception ignored) {}
        }

        List<String> materialNames = cfg.getStringList("drops.materials");
        Material[] materials;
        if (materialNames == null || materialNames.isEmpty()) {
            materials = new Material[]{
                    Material.BROWN_CONCRETE,
                    Material.BROWN_TERRACOTTA,
                    Material.BROWN_CONCRETE_POWDER,
                    Material.BROWN_WOOL,
                    Material.MUD
            };
        } else {
            materials = materialNames.stream().map(name -> {
                try { return Material.valueOf(name); } catch (IllegalArgumentException e) { return null; }
            }).filter(Objects::nonNull).toArray(Material[]::new);
            if (materials.length == 0) materials = new Material[]{Material.BROWN_CONCRETE, Material.BROWN_TERRACOTTA};
        }

        final Material[] mats = materials;
        new BukkitRunnable() {
            int elapsed = 0;
            final ThreadLocalRandom rnd = ThreadLocalRandom.current();
            @Override
            public void run() {
                if (!player.isOnline() || elapsed >= durationTicks) { cancel(); return; }

                // Base from behind player near pelvis
                Location base = player.getLocation().clone();
                base.setY(Math.floor(base.getY()) + 0.9);
                Vector back = player.getLocation().getDirection().multiply(-1).setY(0).normalize();
                if (Double.isNaN(back.length())) back = new Vector(0, 0, 0);
                base.add(back.clone().multiply(backwardOffset));

                // drops
                if (cfg.getBoolean("drops.enabled", true)) {
                    for (int i = 0; i < perTick; i++) {
                        Vector rand = new Vector(rnd.nextDouble(-spread, spread), 0, rnd.nextDouble(-spread, spread));
                        Vector velocity = back.clone().multiply(backSpeed).add(rand).setY(downSpeed);
                        Material m = mats[rnd.nextInt(mats.length)];
                        Item item = player.getWorld().dropItem(base, new ItemStack(m));
                        item.setPickupDelay(Integer.MAX_VALUE);
                        item.setVelocity(velocity);
                        plugin.getServer().getScheduler().runTaskLater(plugin, item::remove, 300L);
                    }
                }

                // interactions
                handleBlockInteractions(player, base, back, rayMaxDist, samplesPerTick, interactWater, interactGround, rnd);

                elapsed += tickPeriod;
            }
        }.runTaskTimer(plugin, 0L, tickPeriod);
    }

    private void handleBlockInteractions(Player player,
                                         Location base,
                                         Vector backFlat,
                                         double rayMaxDist,
                                         int samplesPerTick,
                                         boolean interactWater,
                                         boolean interactGround,
                                         ThreadLocalRandom rnd) {
        World world = player.getWorld();
        Vector dir = player.getLocation().getDirection().multiply(-1).normalize();
        if (Double.isNaN(dir.length())) dir = new Vector(0, -0.2, 0);
        if (dir.getY() > -0.1) dir.setY(dir.getY() - 0.2);

        double radius = configManager.getConfig("PoopConfig").getDouble("spawn_radius", 1.8);

        // Slightly away from the player's body backward
        Location origin = base.clone().add(player.getLocation().getDirection().multiply(-0.3));
        for (int i = 0; i < samplesPerTick; i++) {
            double rx = rnd.nextDouble(-radius, radius);
            double rz = rnd.nextDouble(-radius, radius);
            Location start = origin.clone().add(rx, 0.1, rz);

            RayTraceResult rFluids = world.rayTraceBlocks(start, dir, rayMaxDist, FluidCollisionMode.ALWAYS, false);
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
                while (top.getY() < world.getMaxHeight() && top.getRelative(BlockFace.UP).getType() == Material.WATER) {
                    top = top.getRelative(BlockFace.UP);
                }
                transformWaterToPoopBlock(top, rnd);
            } else if (interactGround) {
                placeGroundPoopBlock(hit, result.getHitBlockFace(), rnd);
            }
        }
    }

    private void transformWaterToPoopBlock(Block block, ThreadLocalRandom rnd) {
        final FileConfiguration cfg = configManager.getConfig("PoopConfig");
        List<String> names = cfg.getStringList("water.block_materials");
        Material[] options;
        if (names == null || names.isEmpty()) {
            options = new Material[]{Material.BROWN_CONCRETE, Material.BROWN_TERRACOTTA};
        } else {
            options = names.stream().map(n -> { try { return Material.valueOf(n); } catch (Exception e) { return null; } })
                    .filter(Objects::nonNull).toArray(Material[]::new);
            if (options.length == 0) options = new Material[]{Material.BROWN_CONCRETE, Material.BROWN_TERRACOTTA};
        }
        boolean unbreakable = cfg.getBoolean("water.unbreakable", true);
        int minSec = Math.max(1, cfg.getInt("water.revert_min_seconds", 12));
        int maxSec = Math.max(minSec, cfg.getInt("water.revert_max_seconds", 28));

        String key = locKey(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (poopWaterBlocks.contains(key)) return;

        BlockData prev = block.getBlockData().clone();
        originalData.put(key, prev);

        block.setType(options[rnd.nextInt(options.length)], false);
        if (unbreakable) poopWaterBlocks.add(key);

        int delaySec = rnd.nextInt(minSec, maxSec + 1);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!poopWaterBlocks.contains(key)) return;
            BlockData data = originalData.remove(key);
            poopWaterBlocks.remove(key);
            if (data != null) block.setBlockData(data, false);
            else block.setType(Material.WATER, false);
        }, delaySec * 20L);
    }

    private void placeGroundPoopBlock(Block hit, BlockFace face, ThreadLocalRandom rnd) {
        final FileConfiguration cfg = configManager.getConfig("PoopConfig");
        List<String> names = cfg.getStringList("ground.block_materials");
        Material[] options;
        if (names == null || names.isEmpty()) {
            options = new Material[]{Material.BROWN_CONCRETE_POWDER, Material.COARSE_DIRT, Material.PODZOL, Material.BROWN_TERRACOTTA};
        } else {
            options = names.stream().map(n -> { try { return Material.valueOf(n); } catch (Exception e) { return null; } })
                    .filter(Objects::nonNull).toArray(Material[]::new);
            if (options.length == 0) options = new Material[]{Material.BROWN_CONCRETE_POWDER, Material.BROWN_TERRACOTTA};
        }

        Block place;
        if (hit.getType() == Material.SNOW || hit.getType() == Material.SNOW_BLOCK) {
            place = hit;
        } else if (face != null) {
            place = hit.getRelative(face);
        } else {
            place = hit;
        }
        if (place.getType().isAir() || place.isPassable() || place.getType() == Material.SNOW || place.getType() == Material.SNOW_BLOCK) {
            place.setType(options[rnd.nextInt(options.length)], false);
            poopGroundBlocks.add(locKey(place.getWorld(), place.getX(), place.getY(), place.getZ()));
        }
    }

    private String locKey(World world, int x, int y, int z) {
        return world.getUID().toString() + ":" + x + ":" + y + ":" + z;
    }

    public boolean isPoopWater(Block block) {
        return poopWaterBlocks.contains(locKey(block.getWorld(), block.getX(), block.getY(), block.getZ()));
    }
    public boolean isPoopGround(Block block) {
        return poopGroundBlocks.contains(locKey(block.getWorld(), block.getX(), block.getY(), block.getZ()));
    }
    public void removePoopGround(Block block) {
        poopGroundBlocks.remove(locKey(block.getWorld(), block.getX(), block.getY(), block.getZ()));
    }
}
