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
        // 1. Inventar Inhalt (Main, Hotbar, Offhand, Armor)
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        // 2. Cursor (Mauszeiger)
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && cursor.getType() == material) {
            count += cursor.getAmount();
        }
        // 3. Crafting Grid (Nur für aktuellen Check, nicht zum Entfernen)
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

    // --- PICKUP ---
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

    // --- CLICK ---
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
        boolean takingFromExternal = (clickedInv != null && clickedInv != player.getInventory());

        // Nur blockieren, wenn man neue Items aus einer Kiste holt.
        // Bewegen im Inventar ist erlaubt (wird beim Schließen geprüft).
        if (currentCount >= limit && takingFromExternal) {
            event.setCancelled(true);
            sendFeedback(player, "Limit reached!");
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        // Dragging ist erlaubt, Überschuss wird beim Schließen entfernt.
    }

    // --- CRAFTING ---
    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!plugin.isWorldEnabled(player.getWorld().getName())) return;

        ItemStack result = event.getRecipe().getResult();
        Material mat = result.getType();

        if (plugin.getLimits().containsKey(mat)) {
            int limit = plugin.getLimits().get(mat);
            int currentCount = countItems(player, mat);
            
            if (currentCount + result.getAmount() > limit) {
                event.setCancelled(true);
                sendFeedback(player, "Limit reached via Crafting!");
            }
        }
    }

    // --- DER NEUE "BOUNCER" (ON CLOSE) ---
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!plugin.isWorldEnabled(player.getWorld().getName())) return;

        // Wir prüfen JEDES limitierte Material
        for (Material mat : plugin.getLimits().keySet()) {
            int limit = plugin.getLimits().get(mat);
            
            // 1. Alles zählen (Inv + Cursor)
            int totalCount = 0;
            
            // Inventar scannen
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == mat) {
                    totalCount += item.getAmount();
                }
            }
            
            // Cursor scannen
            ItemStack cursor = player.getItemOnCursor();
            if (cursor != null && cursor.getType() == mat) {
                totalCount += cursor.getAmount();
            }

            // 2. Wenn zu viel -> Droppen
            if (totalCount > limit) {
                int amountToRemove = totalCount - limit; // Das ist der Überschuss

                // Erstmal das Item droppen
                ItemStack dropStack = new ItemStack(mat, amountToRemove);
                player.getWorld().dropItem(player.getLocation(), dropStack);
                
                // JETZT VOM SPIELER LÖSCHEN (Cursor Priorität!)
                
                // Schritt A: Vom Cursor abziehen
                if (cursor != null && cursor.getType() == mat) {
                    if (cursor.getAmount() <= amountToRemove) {
                        // Der ganze Cursor ist Überschuss -> weg damit
                        amountToRemove -= cursor.getAmount();
                        player.setItemOnCursor(null); 
                    } else {
                        // Cursor ist größer als Überschuss -> verkleinern
                        cursor.setAmount(cursor.getAmount() - amountToRemove);
                        amountToRemove = 0; // Alles erledigt
                    }
                }

                // Schritt B: Wenn noch was übrig ist -> Aus dem Inventar abziehen
                if (amountToRemove > 0) {
                    player.getInventory().removeItem(new ItemStack(mat, amountToRemove));
                }

                player.sendMessage(Component.text("Excess items were dropped!", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            }
        }
    }
}
