package de.bierrang.plugin;

import org.bukkit.plugin.java.JavaPlugin;

public class BierSkyDrop extends JavaPlugin {

    private static BierSkyDrop instance;
    private DropManager dropManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        dropManager = new DropManager(this);
        dropManager.load();

        getCommand("skydrop").setExecutor(new SkyCommand(this));
        getServer().getPluginManager().registerEvents(new DropListener(this), this);

        getLogger().info("BierSkyDrop geladen!");
    }

    public static BierSkyDrop getInstance() {
        return instance;
    }

    public DropManager getDropManager() {
        return dropManager;
    }
}
