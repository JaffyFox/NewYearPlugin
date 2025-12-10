package ua.jaffyfox.newYearPlugin.listeners;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import ua.jaffyfox.newYearPlugin.elevator.ElevatorFloor;
import ua.jaffyfox.newYearPlugin.elevator.ElevatorManager;
import ua.jaffyfox.newYearPlugin.config.ConfigManager;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ElevatorListener implements Listener {

    private final JavaPlugin plugin;
    private final ElevatorManager elevatorManager;
    private final ConfigManager configManager;
    private final Map<UUID, Boolean> inElevator = new HashMap<>();

    public ElevatorListener(JavaPlugin plugin, ElevatorManager elevatorManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.elevatorManager = elevatorManager;
        this.configManager = configManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        ElevatorFloor now = elevatorManager.getFloorByCoordinates(player.getLocation());
        boolean wasInside = inElevator.getOrDefault(id, false);
        boolean isInside = now != null;

        if (isInside && !wasInside) {
            // Just entered elevator zone, show selection once
            List<ElevatorFloor> allFloors = elevatorManager.getAllFloors();
            String header = configManager.getConfig("ElevatorConfig").getString("messages.floor_selection", "§e§lВыберите этаж:");
            TextComponent message = new TextComponent(header + "\n");
            for (ElevatorFloor f : allFloors) {
                String btnFormat = configManager.getConfig("ElevatorConfig").getString("messages.button_format", "§b[Этаж %floor%] ");
                TextComponent floorButton = new TextComponent(btnFormat.replace("%floor%", String.valueOf(f.getFloor())));
                floorButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/elevator_go " + f.getFloor()));
                message.addExtra(floorButton);
            }
            player.spigot().sendMessage(message);
            inElevator.put(id, true);
        } else if (!isInside && wasInside) {
            inElevator.put(id, false);
        }
    }
}