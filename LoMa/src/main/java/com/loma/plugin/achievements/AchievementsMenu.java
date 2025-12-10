package com.loma.plugin.achievements;

import com.loma.plugin.LoMa;
import com.loma.plugin.utils.ItemBuilder;
import com.loma.plugin.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class AchievementsMenu {
    public static void open(LoMa plugin, AchievementsManager manager, Player player) {
        String title = MessageUtils.color(
                plugin.getMessagesConfig() != null && plugin.getMessagesConfig().contains("achievements.menu.title")
                        ? plugin.getMessagesConfig().getString("achievements.menu.title")
                        : "&8Достижения"
        );
        int size = 54;
        Inventory inv = Bukkit.createInventory(null, size, title);

        // background
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setName(" ").build();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        // build items
        List<ItemStack> items = new ArrayList<>();
        for (Achievement a : manager.listAll()) {
            items.add(manager.buildIcon(player, a));
        }

        // place on grid with spacing
        List<Integer> slots = computeSpacedSlots(size);
        for (int i = 0; i < items.size() && i < slots.size(); i++) {
            inv.setItem(slots.get(i), items.get(i));
        }

        player.openInventory(inv);
    }

    // same pattern as MenuManager spacing (duplicated small helper)
    private static List<Integer> computeSpacedSlots(int size) {
        int rows = Math.max(1, size / 9);
        List<Integer> result = new ArrayList<>();
        for (int r = 1; r <= rows - 2; r += 2) {
            for (int c = 1; c <= 7; c += 2) {
                int slot = r * 9 + c;
                if (slot >= 0 && slot < size) result.add(slot);
            }
        }
        return result;
    }
}
