package ua.jaffyfox.newYearPlugin.elevator;

import org.bukkit.Location;
import org.bukkit.World;

public class ElevatorFloor {

    private final int floor;
    private final double minX, minY, minZ, maxX, maxY, maxZ;
    private final boolean ySpecified; // if true, use minY as desired teleport Y

    public ElevatorFloor(int floor, double x1, double y1, double z1, double x2, double y2, double z2, boolean ySpecified) {
        this.floor = floor;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.ySpecified = ySpecified;
    }

    public int getFloor() {
        return floor;
    }

    public boolean isInZone(Location loc) {
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();

        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public double getCenterX() { return (minX + maxX) / 2.0; }
    public double getCenterZ() { return (minZ + maxZ) / 2.0; }
    public boolean isYSpecified() { return ySpecified; }
    public double getSpecifiedY() { return minY; }
    public double getMinY() { return minY; }
    public double getMaxY() { return maxY; }
    public int getMinYInt() { return (int) Math.floor(minY); }
    public int getMaxYInt() { return (int) Math.floor(maxY); }
    public int getMinXInt() { return (int) Math.floor(minX); }
    public int getMaxXInt() { return (int) Math.floor(maxX); }
    public int getMinZInt() { return (int) Math.floor(minZ); }
    public int getMaxZInt() { return (int) Math.floor(maxZ); }
}