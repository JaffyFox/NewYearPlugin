package ua.jaffyfox.newYearPlugin.elevator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ua.jaffyfox.newYearPlugin.config.ConfigManager;
import java.util.ArrayList;
import java.util.List;

public class ElevatorManager {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final List<ElevatorFloor> floors = new ArrayList<>();
    
    public ElevatorManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        loadFloors();
    }

    private void loadFloors() {
        String[] floorNames = {"FirstFlor", "SecondFlor", "ThirdFlor", "FourthFlor"};

        for (String floorName : floorNames) {
            FileConfiguration config = configManager.getConfig(floorName);
            if (config != null && config.contains("flor")) {
                int floor = config.getInt("flor");
                double x1 = config.getDouble("first_coordinates.x");
                double y1 = config.getDouble("first_coordinates.y");
                double z1 = config.getDouble("first_coordinates.z");
                double x2 = config.getDouble("second_coordinates.x");
                double y2 = config.getDouble("second_coordinates.y");
                double z2 = config.getDouble("second_coordinates.z");

                boolean ySpecified = config.contains("first_coordinates.y");

                floors.add(new ElevatorFloor(floor, x1, y1, z1, x2, y2, z2, ySpecified));
            }
        }
    }

    public ElevatorFloor getFloorByCoordinates(Location loc) {
        for (ElevatorFloor floor : floors) {
            if (floor.isInZone(loc)) {
                return floor;
            }
        }
        return null;
    }

    public ElevatorFloor getFloorByNumber(int number) {
        for (ElevatorFloor f : floors) {
            if (f.getFloor() == number) return f;
        }
        return null;
    }

    public List<ElevatorFloor> getAllFloors() {
        return floors;
    }

    public void teleportToFloor(Player player, ElevatorFloor floor) {
        // Try to find a safe spot INSIDE the floor area first
        Location safe = findSafeLocationInFloor(player, floor);

        if (safe == null) {
            // final fallback: world surface at exact center XZ
            double centerX = floor.getCenterX();
            double centerZ = floor.getCenterZ();
            Location probe = new Location(player.getWorld(), centerX, player.getLocation().getY(), centerZ);
            int surfaceY = getHighestY(probe);
            safe = new Location(player.getWorld(), centerX, surfaceY + 1, centerZ);
        }

        player.teleport(safe);

        FileConfiguration config = configManager.getConfig("ElevatorConfig");
        String soundName = config.getString("elevator.sound", config.getString("sound", "minecraft:entity.enderman.teleport"));
        float volume = (float) config.getDouble("elevator.sound_volume", (float) config.getDouble("sound_volume", 1.0));
        float pitch = (float) config.getDouble("elevator.sound_pitch", (float) config.getDouble("sound_pitch", 1.0));
        boolean played = false;
        try {
            player.playSound(player.getLocation(), soundName, volume, pitch);
            played = true;
        } catch (Exception ignored) { }
        if (!played) {
            playLegacySound(player, config.getString("elevator.sound", config.getString("sound", "ENTITY_ENDERMAN_TELEPORT")), volume, pitch);
        }

        String message = config.getString("messages.teleport_message", config.getString("teleport_message", "§aТелепортация на этаж %floor%"));
        player.sendMessage(message.replace("%floor%", String.valueOf(floor.getFloor())));
    }

    private int getHighestY(Location loc) {
        for (int y = 320; y >= -64; y--) {
            Block block = loc.getWorld().getBlockAt(loc.getBlockX(), y, loc.getBlockZ());
            if (block.getType().isSolid()) {
                return y;
            }
        }
        return 0;
    }

    private boolean isSafeAt(Player player, int x, int y, int z) {
        Block ground = player.getWorld().getBlockAt(x, y - 1, z);
        Block feet = player.getWorld().getBlockAt(x, y, z);
        Block head = player.getWorld().getBlockAt(x, y + 1, z);
        boolean groundSafe = !ground.isPassable(); // supports slabs/stairs as ground
        boolean spaceClear = feet.getType().isAir() && head.getType().isAir();
        return groundSafe && spaceClear;
    }

    private Integer findSafeYInRange(Player player, int x, int z, int minY, int maxY) {
        // Prefer from top down to land player on the floor surface within the elevator volume
        for (int y = maxY; y >= minY; y--) {
            if (isSafeAt(player, x, y, z)) {
                return y;
            }
        }
        return null;
    }

    private Location findSafeLocationInFloor(Player player, ElevatorFloor floor) {
        int minX = floor.getMinXInt();
        int maxX = floor.getMaxXInt();
        int minZ = floor.getMinZInt();
        int maxZ = floor.getMaxZInt();
        int minY = floor.getMinYInt();
        int maxY = floor.getMaxYInt();

        int cx = (int) Math.round(floor.getCenterX());
        int cz = (int) Math.round(floor.getCenterZ());

        // If specific Y is provided and safe at center column, use it first
        if (floor.isYSpecified()) {
            int preferY = (int) Math.floor(floor.getSpecifiedY());
            if (preferY < minY) preferY = minY;
            if (preferY > maxY) preferY = maxY;
            if (cx >= minX && cx <= maxX && cz >= minZ && cz <= maxZ && isSafeAt(player, cx, preferY, cz)) {
                return new Location(player.getWorld(), cx + 0.5, preferY, cz + 0.5);
            }
        }

        // Spiral/ring search around center within bounds
        int maxRadius = Math.max(Math.max(cx - minX, maxX - cx), Math.max(cz - minZ, maxZ - cz));
        for (int r = 0; r <= maxRadius; r++) {
            for (int x = cx - r; x <= cx + r; x++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    if (x < minX || x > maxX || z < minZ || z > maxZ) continue;
                    // check only the ring perimeter to reduce checks
                    if (x != cx - r && x != cx + r && z != cz - r && z != cz + r) continue;
                    Integer y = findSafeYInRange(player, x, z, minY, maxY);
                    if (y != null) {
                        return new Location(player.getWorld(), x + 0.5, y, z + 0.5);
                    }
                }
            }
        }

        return null;
    }

    @SuppressWarnings({"deprecation", "removal"})
    private void playLegacySound(Player player, String enumName, float volume, float pitch) {
        try {
            org.bukkit.Sound s = org.bukkit.Sound.valueOf(enumName);
            player.playSound(player.getLocation(), s, volume, pitch);
        } catch (Exception ignored) { }
    }
}