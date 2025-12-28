package de.priyme.itemlimiter.manager;

import de.priyme.itemlimiter.ItemLimiter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class LimitManager {

    private final ItemLimiter plugin;
    private final Map<Material, Integer> limits = new HashMap<>();

    public LimitManager(ItemLimiter plugin) {
        this.plugin = plugin;
        loadLimits();
    }

    private void loadLimits() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("limits");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            try {
                Material mat = Material.valueOf(key);
                int amount = section.getInt(key);
                limits.put(mat, amount);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in config: " + key);
            }
        }
    }

    public void saveLimits() {
        plugin.getConfig().set("limits", null); // Reset
        for (Map.Entry<Material, Integer> entry : limits.entrySet()) {
            plugin.getConfig().set("limits." + entry.getKey().name(), entry.getValue());
        }
        plugin.saveConfig();
    }

    public Map<Material, Integer> getLimits() {
        return limits;
    }

    public void setLimit(Material material, int amount) {
        if (amount <= 0) {
            limits.remove(material);
        } else {
            limits.put(material, amount);
        }
        saveLimits();
    }

    public int getLimit(Material material) {
        return limits.getOrDefault(material, -1);
    }

    public int countItems(Player player, Material material) {
        int count = 0;
        
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && is.getType() == material) {
                count += is.getAmount();
            }
        }
        
        ItemStack cursor = player.getItemOnCursor();
        if (cursor.getType() == material) {
            count += cursor.getAmount();
        }

        return count;
    }

    public boolean canPickup(Player player, Material material, int incomingAmount) {
        int limit = getLimit(material);
        if (limit == -1) return true;

        int current = countItems(player, material);
        return (current + incomingAmount) <= limit;
    }

    /**
     * Returns how many more items of this type the player can pick up.
     * Returns Integer.MAX_VALUE if there is no limit.
     */
    public int getRemainingSpace(Player player, Material material) {
        int limit = getLimit(material);
        if (limit == -1) return Integer.MAX_VALUE;

        int current = countItems(player, material);
        return Math.max(0, limit - current);
    }
}
