package de.priyme.itemlimiter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class LimitCommand implements CommandExecutor, Listener {

    private final ItemLimiter plugin;
    // Translate Title
    private final Component GUI_TITLE = Component.text("ItemLimiter Management", NamedTextColor.DARK_PURPLE);

    public LimitCommand(ItemLimiter plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only!");
            return true;
        }

        if (!player.hasPermission("itemlimiter.admin")) {
            player.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return true;
        }

        openGUI(player);
        return true;
    }

    private void openGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);

        // Guide Item
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta meta = info.getItemMeta();
        meta.displayName(Component.text("Guide", NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("Left-Click: Limit +1", NamedTextColor.GRAY),
                Component.text("Right-Click: Limit -1", NamedTextColor.GRAY),
                Component.text("Shift-Click: Remove Limit", NamedTextColor.GRAY),
                Component.text("Click item in inventory: Add to list", NamedTextColor.YELLOW)
        ));
        info.setItemMeta(meta);
        gui.setItem(4, info);

        // List Limits
        int slot = 9;
        for (Map.Entry<Material, Integer> entry : plugin.getLimits().entrySet()) {
            if (slot >= 54) break;

            ItemStack item = new ItemStack(entry.getKey());
            ItemMeta itemMeta = item.getItemMeta();
            itemMeta.displayName(Component.text(entry.getKey().name(), NamedTextColor.AQUA));
            itemMeta.lore(List.of(
                    Component.text("Current Limit: ", NamedTextColor.GRAY)
                            .append(Component.text(entry.getValue(), NamedTextColor.GREEN))
            ));
            item.setItemMeta(itemMeta);
            
            item.setAmount(Math.min(entry.getValue(), 64));
            if (entry.getValue() < 1) item.setAmount(1);

            gui.setItem(slot++, item);
        }

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(GUI_TITLE)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Top Inventory (GUI) -> Edit
        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            Material mat = clicked.getType();
            if (!plugin.getLimits().containsKey(mat)) return;

            int current = plugin.getLimits().get(mat);

            if (event.isShiftClick()) {
                // Remove
                plugin.getLimits().remove(mat);
                plugin.getConfig().set("limits." + mat.name(), null);
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1f);
            } else if (event.isRightClick()) {
                // Decrease
                int newVal = Math.max(1, current - 1);
                plugin.getLimits().put(mat, newVal);
                plugin.getConfig().set("limits." + mat.name(), newVal);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            } else if (event.isLeftClick()) {
                // Increase
                int newVal = current + 1;
                plugin.getLimits().put(mat, newVal);
                plugin.getConfig().set("limits." + mat.name(), newVal);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            }
            plugin.saveConfig();
            openGUI(player); 
        } 
        // Bottom Inventory (Player) -> Add
        else {
            Material mat = clicked.getType();
            if (plugin.getLimits().containsKey(mat)) {
                player.sendMessage(Component.text("Item is already limited!", NamedTextColor.RED));
                return;
            }
            
            plugin.getLimits().put(mat, 1);
            plugin.getConfig().set("limits." + mat.name(), 1);
            plugin.saveConfig();
            
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            openGUI(player);
        }
    }
}
