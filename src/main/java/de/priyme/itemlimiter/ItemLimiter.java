package de.priyme.itemlimiter;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;

public final class ItemLimiter extends JavaPlugin {

    private final HashMap<Material, Integer> limits = new HashMap<>();
    private List<String> enabledWorlds;

    @Override
    public void onEnable() {
        // Config laden
        saveDefaultConfig();
        loadConfigValues();

        // Listener registrieren
        getServer().getPluginManager().registerEvents(new ItemLimiterListener(this), this);
        
        // Command & GUI Listener registrieren (DAS IST NEU)
        LimitCommand cmd = new LimitCommand(this);
        getCommand("limit").setExecutor(cmd);
        getServer().getPluginManager().registerEvents(cmd, this);
        
        getLogger().info("ItemLimiter geladen!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ItemLimiter deaktiviert.");
    }

    public void loadConfigValues() {
        reloadConfig();
        FileConfiguration config = getConfig();
        limits.clear();

        enabledWorlds = config.getStringList("enabled-worlds");

        if (config.isConfigurationSection("limits")) {
            for (String key : config.getConfigurationSection("limits").getKeys(false)) {
                try {
                    Material mat = Material.valueOf(key.toUpperCase());
                    int amount = config.getInt("limits." + key);
                    limits.put(mat, amount);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Falsches Material in Config: " + key);
                }
            }
        }
    }

    public HashMap<Material, Integer> getLimits() {
        return limits;
    }

    public boolean isWorldEnabled(String worldName) {
        if (enabledWorlds == null || enabledWorlds.isEmpty()) {
            return true; 
        }
        return enabledWorlds.contains(worldName);
    }
}
