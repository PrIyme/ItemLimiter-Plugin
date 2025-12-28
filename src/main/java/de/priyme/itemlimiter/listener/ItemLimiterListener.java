package de.priyme.itemlimiter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

public class ItemLimiterListener implements Listener {

    private final ItemLimiter plugin;

    public ItemLimiterListener(ItemLimiter plugin) {
        this.plugin = plugin;
    }

    private int countItems(Player player, Material material) {
        int count = 0;
        // Inventory Content
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        // Cursor
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && cursor.getType() == material) {
            count += cursor.getAmount();
        }
        // Crafting Grid (Checks ingredients currently in grid)
        Inventory topInv = player.getOpenInventory().getTopInventory();
        if (topInv instanceof CraftingInventory) {
            for (ItemStack item : ((CraftingInventory) topInv).getMatrix()) {
                if (item != null && item.getType() == material) {
                    count += item.getAmount();
                }
            }
        }
        return count;
    }

    private void sendFeedback(Player player, String message) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
        player.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, player.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.0);
        player.sendActionBar(Component.text(message, NamedTextColor.RED));
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.isWorldEnabled(player.getWorld().getName())) return;

        Item itemEntity = event.getItem();
        ItemStack stack = itemEntity.getItemStack();
        Material mat = stack.getType();

        if (!plugin.getLimits().containsKey(mat)) return;

        int limit = plugin.getLimits().get(mat);
        int currentAmount = countItems(player, mat);

        if (currentAmount >= limit) {
            event.setCancelled(true);
            sendFeedback(player, "Limit reached!");
            return;
        }

        // Smart Split Logic
        int spaceLeft = limit - currentAmount;
        if (stack.getAmount() > spaceLeft) {
            event.setCancelled(true);
            ItemStack toGive = stack.clone();
            toGive.setAmount(spaceLeft);
            HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(toGive);
            if (!leftovers.isEmpty()) {
                for (ItemStack leftover : leftovers.values()) {
                    player.getWorld().dropItem(player.getLocation(), leftover);
                }
            }
            stack.setAmount(stack.getAmount() - spaceLeft);
            itemEntity.setItemStack(stack);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!plugin.isWorldEnabled(player.getWorld().getName())) return;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        Material matToCheck = null;
        if (cursor != null && plugin.getLimits().containsKey(cursor.getType())) matToCheck = cursor.getType();
        else if (current != null && plugin.getLimits().containsKey(current.getType())) matToCheck = current.getType();

        if (matToCheck == null) return;

        int limit = plugin.getLimits().get(matToCheck);
        int currentCount = countItems(player, matToCheck);

        Inventory clickedInv = event.getClickedInventory();
        
        // --- FIX HERE: RELAXED LOGIC ---
        // Only block if taking NEW items from a CHEST/CONTAINER (External Inventory).
        // Moving items inside your own inventory (including Crafting Slots) is ALWAYS allowed,
        // because onClose will catch any exploits.
        
        boolean takingFromExternal = (clickedInv != null && clickedInv != player.getInventory());

        if (currentCount >= limit) {
            if (takingFromExternal) {
                event.setCancelled(true);
                sendFeedback(player, "Limit reached!");
            }
            // We REMOVED the Shift-Click block for internal inventory!
            // This allows you to Shift-Click items into the Crafting Table.
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        // Dragging is usually fine because the item is already on the cursor (so it's already counted).
        // No strict check needed here anymore, onClose catches dupes.
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!plugin.isWorldEnabled(player.getWorld().getName())) return;

        ItemStack result = event.getRecipe().getResult();
        Material mat = result.getType();

        if (plugin.getLimits().containsKey(mat)) {
            int limit = plugin.getLimits().get(mat);
            int currentCount = countItems(player, mat);
            
            // Allow crafting if the RESULT does not push you over the limit.
            // Ingredients are consumed, but usually we check against the total AFTER crafting.
            // Since result is added: Current + Result > Limit = Block.
            if (currentCount + result.getAmount() > limit) {
                event.setCancelled(true);
                sendFeedback(player, "Limit reached via Crafting!");
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!plugin.isWorldEnabled(player.getWorld().getName())) return;

        // "The Bouncer" - Checks everything when you leave the inventory.
        for (Material mat : plugin.getLimits().keySet()) {
            int limit = plugin.getLimits().get(mat);
            int currentCount = 0;
            
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == mat) currentCount += item.getAmount();
            }
            ItemStack cursor = player.getItemOnCursor();
            if (cursor != null && cursor.getType() == mat) currentCount += cursor.getAmount();

            if (currentCount > limit) {
                int toRemove = currentCount - limit;
                ItemStack remainingToRemove = new ItemStack(mat, toRemove);
                HashMap<Integer, ItemStack> notRemoved = player.getInventory().removeItem(remainingToRemove);
                
                if (!notRemoved.isEmpty()) {
                    if (cursor != null && cursor.getType() == mat) {
                        int amountOnCursor = cursor.getAmount();
                        if (amountOnCursor <= toRemove) {
                            player.setItemOnCursor(null);
                            toRemove -= amountOnCursor;
                        } else {
                            cursor.setAmount(amountOnCursor - toRemove);
                            toRemove = 0;
                        }
                    }
                }

                int droppedAmount = (currentCount - limit) - (notRemoved.isEmpty() ? 0 : notRemoved.get(0).getAmount());
                if (droppedAmount > 0) {
                    ItemStack drop = new ItemStack(mat, droppedAmount);
                    player.getWorld().dropItem(player.getLocation(), drop);
                    player.sendMessage(Component.text("Excess items were dropped!", NamedTextColor.RED));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                }
            }
        }
    }
}
