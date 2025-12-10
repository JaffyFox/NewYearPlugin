package com.loma.plugin.achievements;

import org.bukkit.Material;

public class Achievement {
    private final String key;
    private final String name;
    private final String description;
    private final Material icon;

    public Achievement(String key, String name, String description, Material icon) {
        this.key = key;
        this.name = name;
        this.description = description;
        this.icon = icon == null ? Material.PAPER : icon;
    }

    public String getKey() { return key; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Material getIcon() { return icon; }
}
