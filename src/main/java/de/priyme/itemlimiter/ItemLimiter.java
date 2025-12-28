package de.priyme.itemlimiter;

import de.priyme.itemlimiter.commands.LimitCommand;
import de.priyme.itemlimiter.listener.LimitListener;
import de.priyme.itemlimiter.manager.LimitManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemLimiter extends JavaPlugin {

    private LimitManager limitManager;

    @Override
    public void onEnable() {
        // Config erstellen falls nicht vorhanden
        saveDefaultConfig();

        // Manager initialisieren
        this.limitManager = new LimitManager(this);

        // Events registrieren
        getServer().getPluginManager().registerEvents(new LimitListener(limitManager), this);

        // Commands registrieren
        getCommand("limit").setExecutor(new LimitCommand(limitManager));
        
        getLogger().info("ItemLimiter von Priyme aktiviert!");
    }

    @Override
    public void onDisable() {
        if (limitManager != null) {
            limitManager.saveLimits();
        }
    }
}
