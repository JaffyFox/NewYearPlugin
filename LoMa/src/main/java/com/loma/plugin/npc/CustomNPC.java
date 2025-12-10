package com.loma.plugin.npc;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CustomNPC {

    private final int id;
    private String name;
    private Location location;
    private String skinName;
    private String permission;
    private int cooldown; // в секундах
    private boolean glowing;
    private boolean lookAtPlayer;
    private final Map<String, String[]> actions;
    private final Map<UUID, Long> lastInteraction;

    public CustomNPC(int id, String name, Location location, String skinName) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.skinName = skinName;
        this.actions = new HashMap<>();
        this.lastInteraction = new HashMap<>();
        this.cooldown = 0;
        this.glowing = false;
        this.lookAtPlayer = true;
    }

    public void addAction(String actionType, String[] args) {
        actions.put(actionType, args);
    }

    public void removeAction(String actionType) {
        actions.remove(actionType);
    }

    public boolean canInteract(Player player) {
        if (cooldown <= 0) return true;

        UUID uuid = player.getUniqueId();
        if (!lastInteraction.containsKey(uuid)) return true;

        long lastTime = lastInteraction.get(uuid);
        long currentTime = System.currentTimeMillis();

        return (currentTime - lastTime) >= (cooldown * 1000L);
    }

    public long getCooldownRemaining(Player player) {
        if (cooldown <= 0) return 0;

        UUID uuid = player.getUniqueId();
        if (!lastInteraction.containsKey(uuid)) return 0;

        long lastTime = lastInteraction.get(uuid);
        long currentTime = System.currentTimeMillis();
        long timePassed = currentTime - lastTime;
        long cooldownMs = cooldown * 1000L;

        if (timePassed >= cooldownMs) return 0;

        return cooldownMs - timePassed;
    }

    public void setLastInteraction(Player player) {
        lastInteraction.put(player.getUniqueId(), System.currentTimeMillis());
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getSkinName() {
        return skinName;
    }

    public void setSkinName(String skinName) {
        this.skinName = skinName;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public int getCooldown() {
        return cooldown;
    }

    public void setCooldown(int cooldown) {
        this.cooldown = cooldown;
    }

    public boolean isGlowing() {
        return glowing;
    }

    public void setGlowing(boolean glowing) {
        this.glowing = glowing;
    }

    public boolean isLookAtPlayer() {
        return lookAtPlayer;
    }

    public void setLookAtPlayer(boolean lookAtPlayer) {
        this.lookAtPlayer = lookAtPlayer;
    }

    public Map<String, String[]> getActions() {
        return actions;
    }
}