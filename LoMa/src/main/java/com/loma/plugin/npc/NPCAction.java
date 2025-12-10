package com.loma.plugin.npc;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface NPCAction {
    /**
     * Выполняет действие при взаимодействии с NPC
     *
     * @param player Игрок, который взаимодействует с NPC
     * @param npc NPC с которым происходит взаимодействие
     * @param args Аргументы действия
     */
    void execute(Player player, CustomNPC npc, String[] args);
}