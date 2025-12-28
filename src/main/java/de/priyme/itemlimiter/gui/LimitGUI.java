package de.priyme.itemlimiter.gui;

import de.priyme.itemlimiter.manager.LimitManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LimitGUI implements InventoryHolder {

    private final LimitManager limitManager;
    private final Inventory inventory;
    private final Player viewer;

    public LimitGUI(LimitManager limitManager, Player viewer) {
        this.limitManager = limitManager;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, 54, Component.text("§8Manage Item Limits"));
        initializeItems();
    }

    private void initializeItems() {
        inventory.clear();

        // Add Button
        ItemStack addHandItem = new ItemStack(Material.LIME_DYE);
        ItemMeta addMeta = addHandItem.getItemMeta();
        addMeta.displayName(Component.text("§a+ Limit Item in Hand"));
        addMeta.lore(List.of(Component.text("§7Click to add the item in your"), Component.text("§7hand to the limit list.")));
        addHandItem.setItemMeta(addMeta);
        inventory.setItem(4, addHandItem);

        // Filler
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);
        for (int i = 9; i < 18; i++) {
            inventory.setItem(i, filler);
        }

        // List Items
        int index = 18;
        for (Map.Entry<Material, Integer> entry : limitManager.getLimits().entrySet()) {
            if (index >= 54) break;

            Material mat = entry.getKey();
            int limit = entry.getValue();
            int currentAmount = limitManager.countItems(viewer, mat);

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<Component> lore = new ArrayList<>();
                
                String statusColor = (currentAmount >= limit) ? "§c" : "§a";
                
                lore.add(Component.text("§7Max Limit: §e" + limit));
                lore.add(Component.text("§7Your Inventory: " + statusColor + currentAmount + " / " + limit));
                lore.add(Component.text(" "));
                lore.add(Component.text("§7Left-Click: §a+1"));
                lore.add(Component.text("§7Right-Click: §c-1"));
                lore.add(Component.text("§7Shift-Click: §4Delete"));
                meta.lore(lore);
                item.setItemMeta(meta);
            }
            inventory.setItem(index++, item);
        }
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Slot 4: Add Hand Item
        if (event.getSlot() == 4) {
            ItemStack hand = viewer.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) {
                viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 1);
                return;
            }
            if (limitManager.getLimit(hand.getType()) != -1) {
                viewer.sendMessage("§cThis item is already limited.");
                return;
            }
            limitManager.setLimit(hand.getType(), 1);
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 2);
            initializeItems();
            return;
        }

        if (event.getSlot() < 18) return;

        Material type = clicked.getType();
        int currentLimit = limitManager.getLimit(type);
        if (currentLimit == -1) return;

        boolean changed = false;
        if (event.isShiftClick()) {
            limitManager.setLimit(type, 0);
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1, 1);
            changed = true;
        } else if (event.isLeftClick()) {
            limitManager.setLimit(type, currentLimit + 1);
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            changed = true;
        } else if (event.isRightClick()) {
            if (currentLimit > 1) {
                limitManager.setLimit(type, currentLimit - 1);
                viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            } else {
                limitManager.setLimit(type, 0);
                viewer.playSound(viewer.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1, 1);
            }
            changed = true;
        }

        if (changed) initializeItems();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
