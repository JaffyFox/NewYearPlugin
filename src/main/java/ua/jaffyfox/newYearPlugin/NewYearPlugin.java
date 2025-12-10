package ua.jaffyfox.newYearPlugin;

import org.bukkit.plugin.java.JavaPlugin;
import ua.jaffyfox.newYearPlugin.config.ConfigManager;
import ua.jaffyfox.newYearPlugin.listeners.DoubleJumpListener;
import ua.jaffyfox.newYearPlugin.listeners.ElevatorListener;
import ua.jaffyfox.newYearPlugin.listeners.PissListener;
import ua.jaffyfox.newYearPlugin.listeners.VomitListener;
import ua.jaffyfox.newYearPlugin.listeners.SitListener;
import ua.jaffyfox.newYearPlugin.listeners.PissBlockListener;
import ua.jaffyfox.newYearPlugin.listeners.VomitBlockListener;
import ua.jaffyfox.newYearPlugin.listeners.SpeedListener;
import ua.jaffyfox.newYearPlugin.listeners.PoopBlockListener;
import ua.jaffyfox.newYearPlugin.commands.JumpBoostCommand;
import ua.jaffyfox.newYearPlugin.commands.GamemodeCommand;
import ua.jaffyfox.newYearPlugin.commands.ElevatorCommand;
import ua.jaffyfox.newYearPlugin.elevator.ElevatorManager;
import ua.jaffyfox.newYearPlugin.commands.PissCommand;
import ua.jaffyfox.newYearPlugin.fun.PissManager;
import ua.jaffyfox.newYearPlugin.fun.VomitManager;
import ua.jaffyfox.newYearPlugin.fun.PoopManager;
import ua.jaffyfox.newYearPlugin.commands.SpeedCommand;
import ua.jaffyfox.newYearPlugin.commands.SpawnCommand;
import ua.jaffyfox.newYearPlugin.commands.VomitCommand;
import ua.jaffyfox.newYearPlugin.commands.SitCommand;
import ua.jaffyfox.newYearPlugin.commands.PoopCommand;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class NewYearPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private ElevatorManager elevatorManager;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.loadConfigs();

        elevatorManager = new ElevatorManager(this, configManager);
        PissManager pissManager = new PissManager(this, configManager);
        VomitManager vomitManager = new VomitManager(this, configManager);
        PoopManager poopManager = new PoopManager(this, configManager);
        ua.jaffyfox.newYearPlugin.fun.SitManager sitManager = new ua.jaffyfox.newYearPlugin.fun.SitManager(this, configManager);

        getServer().getPluginManager().registerEvents(new ElevatorListener(this, elevatorManager, configManager), this);
        getServer().getPluginManager().registerEvents(new DoubleJumpListener(this, configManager), this);
        getServer().getPluginManager().registerEvents(new PissListener(this, configManager, pissManager), this);
        getServer().getPluginManager().registerEvents(new PissBlockListener(pissManager), this);
        getServer().getPluginManager().registerEvents(new VomitListener(configManager, vomitManager, poopManager), this);
        getServer().getPluginManager().registerEvents(new VomitBlockListener(vomitManager), this);
        getServer().getPluginManager().registerEvents(new PoopBlockListener(configManager, poopManager), this);
        getServer().getPluginManager().registerEvents(new SitListener(configManager, sitManager), this);
        getServer().getPluginManager().registerEvents(new SpeedListener(configManager), this);

        JumpBoostCommand jbCmd = new JumpBoostCommand(configManager);
        getCommand("jump_boost").setExecutor(jbCmd);
        getCommand("jump_boost").setTabCompleter(jbCmd);
        GamemodeCommand gmCmd = new GamemodeCommand();
        getCommand("gamemode").setExecutor(gmCmd);
        getCommand("gamemode").setTabCompleter(gmCmd);

        ElevatorCommand elCmd = new ElevatorCommand(configManager, elevatorManager);
        getCommand("elevator_go").setExecutor(elCmd);
        getCommand("elevator_go").setTabCompleter(elCmd);

        PissCommand pissCmd = new PissCommand(configManager, pissManager);
        getCommand("piss").setExecutor(pissCmd);
        getCommand("piss").setTabCompleter(pissCmd);

        SpeedCommand speedCmd = new SpeedCommand(configManager);
        getCommand("speed").setExecutor(speedCmd);
        getCommand("speed").setTabCompleter(speedCmd);

        // Register /spawn and apply world spawn from config
        getCommand("spawn").setExecutor(new SpawnCommand(configManager));
        try {
            double sx = configManager.getConfig("SpawnConfig").getDouble("x", 0);
            double sy = configManager.getConfig("SpawnConfig").getDouble("y", 64);
            double sz = configManager.getConfig("SpawnConfig").getDouble("z", 0);
            World world = Bukkit.getWorlds().get(0);
            world.setSpawnLocation((int) Math.floor(sx), (int) Math.floor(sy), (int) Math.floor(sz));
        } catch (Exception e) {
            getLogger().warning("Failed to set world spawn from SpawnConfig: " + e.getMessage());
        }

        // /vomit command
        VomitCommand vomitCmd = new VomitCommand(configManager, vomitManager);
        getCommand("vomit").setExecutor(vomitCmd);
        getCommand("vomit").setTabCompleter(vomitCmd);

        // /poop command
        PoopCommand poopCmd = new PoopCommand(configManager, poopManager);
        getCommand("poop").setExecutor(poopCmd);
        getCommand("poop").setTabCompleter(poopCmd);

        // /sit command
        SitCommand sitCmd = new SitCommand(configManager, sitManager);
        getCommand("sit").setExecutor(sitCmd);
        getCommand("sit").setTabCompleter(sitCmd);

        getLogger().info("NewYearPlugin enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("NewYearPlugin disabled!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ElevatorManager getElevatorManager() {
        return elevatorManager;
    }
}