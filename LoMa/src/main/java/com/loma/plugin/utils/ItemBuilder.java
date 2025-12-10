package com.loma.plugin.utils;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ItemBuilder {

    private ItemStack item;
    private ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
        this.meta = this.item.getItemMeta();
    }

    public ItemBuilder setAmount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder setName(String name) {
        if (meta != null) {
            meta.setDisplayName(MessageUtils.color(name));
        }
        return this;
    }

    public ItemBuilder setLore(String... lore) {
        if (meta != null) {
            meta.setLore(MessageUtils.color(Arrays.asList(lore)));
        }
        return this;
    }

    public ItemBuilder setLore(List<String> lore) {
        if (meta != null) {
            meta.setLore(MessageUtils.color(lore));
        }
        return this;
    }

    public ItemBuilder addLoreLine(String line) {
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore == null) {
                lore = new ArrayList<>();
            }
            lore.add(MessageUtils.color(line));
            meta.setLore(lore);
        }
        return this;
    }

    public ItemBuilder addEnchantment(Enchantment enchantment, int level) {
        if (meta != null) {
            meta.addEnchant(enchantment, level, true);
        }
        return this;
    }

    public ItemBuilder removeEnchantment(Enchantment enchantment) {
        if (meta != null) {
            meta.removeEnchant(enchantment);
        }
        return this;
    }

    public ItemBuilder addItemFlags(ItemFlag... flags) {
        if (meta != null) {
            meta.addItemFlags(flags);
        }
        return this;
    }

    public ItemBuilder removeItemFlags(ItemFlag... flags) {
        if (meta != null) {
            meta.removeItemFlags(flags);
        }
        return this;
    }

    public ItemBuilder setUnbreakable(boolean unbreakable) {
        if (meta != null) {
            meta.setUnbreakable(unbreakable);
        }
        return this;
    }

    public ItemBuilder setCustomModelData(int data) {
        if (meta != null) {
            meta.setCustomModelData(data);
        }
        return this;
    }

    public ItemBuilder setGlowing(boolean glowing) {
        if (glowing) {
            addEnchantment(Enchantment.UNBREAKING, 1);
            addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            removeEnchantment(Enchantment.UNBREAKING);
            removeItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemBuilder setSkullOwner(String playerName) {
        if (meta instanceof SkullMeta) {
            SkullMeta skullMeta = (SkullMeta) meta;
            skullMeta.setOwner(playerName);
        }
        return this;
    }

    public ItemBuilder setLeatherArmorColor(Color color) {
        if (meta instanceof LeatherArmorMeta) {
            LeatherArmorMeta leatherMeta = (LeatherArmorMeta) meta;
            leatherMeta.setColor(color);
        }
        return this;
    }

    public ItemBuilder addNBTTag(String key, String value) {
        if (meta != null) {
            Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("LoMa");
            if (plugin != null) {
                NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
                meta.getPersistentDataContainer().set(namespacedKey, PersistentDataType.STRING, value);
            }
        }
        return this;
    }

    public ItemBuilder addNBTTag(String key, int value) {
        if (meta != null) {
            Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("LoMa");
            if (plugin != null) {
                NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
                meta.getPersistentDataContainer().set(namespacedKey, PersistentDataType.INTEGER, value);
            }
        }
        return this;
    }

    public ItemBuilder hideAllAttributes() {
        addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_PLACED_ON, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack buildCopy() {
        return build().clone();
    }
}