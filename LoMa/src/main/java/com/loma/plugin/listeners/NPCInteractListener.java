package com.loma.plugin.listeners;

import com.loma.plugin.LoMa;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class NPCInteractListener implements Listener {

    private final LoMa plugin;

    public NPCInteractListener(LoMa plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        NPC npc = event.getNPC();

        // Обработка взаимодействия с NPC
        plugin.getNPCManager().handleNPCInteract(player, npc.getId());
    }
}