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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

public class ItemLimiterListener implements Listener {

    private final ItemLimiter plugin;

    public ItemLimiterListener(ItemLimiter plugin) {
        this.plugin = plugin;
    }

    /**
     * Zählt Items im Inventar, im Cursor UND im Crafting-Grid.
     */
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

        // 3. Crafting Grid (NEU: Damit wird Crafting als "Besitz" gezählt)
        Inventory topInv = player.getOpenInventory().getTopInventory();
        if (topInv instanceof CraftingInventory crafting) {
            // getMatrix() ignoriert den Output-Slot, wir zählen nur Inputs
            for (ItemStack item : crafting.getMatrix()) {
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

    // --- CLICK (Hier ist der wichtigste Fix) ---
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!plugin.isWorldEnabled(player.getWorld().getName())) return;

        // TEIL 1: Verhinderung des "Parkens" im Crafting-Grid
        // Wenn man in ein Crafting-Inventar klickt UND einen limitierten Gegenstand platziert
        if (event.getClickedInventory() instanceof CraftingInventory && event.getSlotType() == InventoryType.SlotType.CRAFTING) {
            ItemStack cursor = event.getCursor();
            
            // Prüfen, ob Cursor limitiert ist
            if (cursor != null && plugin.getLimits().containsKey(cursor.getType())) {
                Material mat = cursor.getType();
                int limit = plugin.getLimits().get(mat);
                int current = countItems(player, mat); // Zählt jetzt Crafting mit!
                
                // Da countItems den Cursor bereits mitzählt, müssen wir prüfen:
                // Würde das Platzieren (was countItems schon inkludiert hat im Cursor) das Limit sprengen?
                // Logik: countItems = (Inv + Crafting + Cursor). 
                // Wenn countItems > limit, ist der Spieler bereits drüber oder am Limit.
                
                // Wir wollen verhindern, dass er Items im Crafting Grid "versteckt".
                // Da countItems das Grid jetzt mitzählt, ist die Logik einfach:
                if (current > limit) {
                    event.setCancelled(true);
                    sendFeedback(player, "Limit reached (Crafting)!");
                    return; // Event hier abbrechen
                }
            }
        }

        // TEIL 2: Bestehende Logik für Kisten/Inventar
        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        Material matToCheck = null;
        if (cursor != null && plugin.getLimits().containsKey(cursor.getType())) matToCheck = cursor.getType();
        else if (currentItem != null && plugin.getLimits().containsKey(currentItem.getType())) matToCheck = currentItem.getType();

        if (matToCheck == null) return;

        int limit = plugin.getLimits().get(matToCheck);
        int currentCount = countItems(player, matToCheck);

        Inventory clickedInv = event.getClickedInventory();
        boolean takingFromExternal = (clickedInv != null && clickedInv != player.getInventory() && !(clickedInv instanceof CraftingInventory));

        // Nur blockieren, wenn man neue Items aus einer externen Kiste holt.
        if (currentCount >= limit && takingFromExternal) {
            // Ausnahme: Wenn man Items in die Leere klickt oder droppt, nicht blockieren
            event.setCancelled(true);
            sendFeedback(player, "Limit reached!");
        }
    }

    // --- DRAG (Verhindert Verteilen über Crafting Slots) ---
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!plugin.isWorldEnabled(player.getWorld().getName())) return;

        // Prüfen ob das Drag-Event Slots im Crafting-Grid betrifft
        boolean involvesCrafting = event.getInventory() instanceof CraftingInventory;
        if (!involvesCrafting) return;

        ItemStack dragged = event.getOldCursor();
        if (dragged == null || !plugin.getLimits().containsKey(dragged.getType())) return;

        // Wenn einer der betroffenen Slots im Crafting-Bereich ist
        for (int slot : event.getRawSlots()) {
             // In einer CraftingInventory View (z.B. Workbench) sind Slots 1-9 das Grid (ungefähr, variiert je nach Typ)
             // Sicherer Weg: Wir zählen einfach alles.
             
             Material mat = dragged.getType();
             int limit = plugin.getLimits().get(mat);
             int current = countItems(player, mat);

             // Wenn das Limit durch das Item am Cursor (das gerade verteilt wird) bereits erreicht/überschritten ist
             if (current > limit) {
                 event.setCancelled(true);
                 sendFeedback(player, "Limit reached!");
                 return;
             }
        }
    }

    // --- CRAFTING (Resultat nehmen) ---
    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!plugin.isWorldEnabled(player.getWorld().getName())) return;

        ItemStack result = event.getRecipe().getResult();
        Material mat = result.getType();

        if (plugin.getLimits().containsKey(mat)) {
            int limit = plugin.getLimits().get(mat);
            
            // Hier müssen wir aufpassen: countItems zählt die Zutaten im Grid mit.
            // Wenn die Zutaten dasselbe Material sind wie das Ergebnis (z.B. Block zu Ingot), 
            // verringert sich die Anzahl erst NACH dem Craften.
            // Bukkit CraftItemEvent ist "Pre-Craft".
            
            int currentCount = countItems(player, mat);
            
            // Simple logic: Wenn current + result > limit -> Block
            // (Etwas streng bei Umcrafting, aber sicher gegen Exploits)
            if (currentCount + result.getAmount() > limit) {
                event.setCancelled(true);
                sendFeedback(player, "Limit reached via Crafting!");
            }
        }
    }

    // --- DER "BOUNCER" (ON CLOSE) - Als Safety Net ---
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!plugin.isWorldEnabled(player.getWorld().getName())) return;

        for (Material mat : plugin.getLimits().keySet()) {
            int limit = plugin.getLimits().get(mat);
            
            // 1. Zählen (Benutzt jetzt die neue Methode, die auch Crafting Items im "Return Queue" erkennt)
            int totalCount = countItems(player, mat);
            
            // 2. Wenn zu viel -> Droppen
            if (totalCount > limit) {
                int amountToRemove = totalCount - limit;

                // Erstmal das Item droppen
                ItemStack dropStack = new ItemStack(mat, amountToRemove);
                player.getWorld().dropItem(player.getLocation(), dropStack);
                
                // LÖSCHEN
                
                // A: Cursor
                ItemStack cursor = player.getItemOnCursor();
                if (cursor != null && cursor.getType() == mat) {
                    if (cursor.getAmount() <= amountToRemove) {
                        amountToRemove -= cursor.getAmount();
                        player.setItemOnCursor(null); 
                    } else {
                        cursor.setAmount(cursor.getAmount() - amountToRemove);
                        amountToRemove = 0;
                    }
                }

                // B: Inventar
                if (amountToRemove > 0) {
                    // remove ist sicher, da Crafting-Items beim Close automatisch ins Inv zurückfallen
                    // oder gedroppt werden von Bukkit, bevor dieses Event fertig ist.
                    player.getInventory().removeItem(new ItemStack(mat, amountToRemove));
                }

                player.sendMessage(Component.text("Excess items were dropped!", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            }
        }
    }
}
