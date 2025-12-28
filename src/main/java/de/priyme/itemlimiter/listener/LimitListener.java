package de.priyme.itemlimiter.listener;

import de.priyme.itemlimiter.gui.LimitGUI;
import de.priyme.itemlimiter.manager.LimitManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

public class LimitListener implements Listener {

    private final LimitManager limitManager;

    public LimitListener(LimitManager limitManager) {
        this.limitManager = limitManager;
    }

    private void playRejectEffect(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.9f);
        player.sendActionBar(Component.text("§cInventory limit reached!"));
    }
    
    private void playItemParticles(Item item) {
        item.getWorld().spawnParticle(Particle.SMOKE, item.getLocation().add(0, 0.5, 0), 5, 0.1, 0.1, 0.1, 0.01);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof LimitGUI gui) {
            gui.handleClick(event);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Item itemEntity = event.getItem();
        ItemStack stackOnGround = itemEntity.getItemStack();
        Material type = stackOnGround.getType();
        int amountOnGround = stackOnGround.getAmount();

        int remainingSpace = limitManager.getRemainingSpace(player, type);

        // Case A: Enough space
        if (remainingSpace >= amountOnGround) {
            return;
        }

        // Case B: Full limit
        if (remainingSpace <= 0) {
            event.setCancelled(true);
            if (itemEntity.getPickupDelay() <= 0) {
                itemEntity.setPickupDelay(10);
                playRejectEffect(player);
                playItemParticles(itemEntity);
            }
            return;
        }

        // Case C: Partial pickup (Smart Split)
        event.setCancelled(true);

        ItemStack toGive = stackOnGround.clone();
        toGive.setAmount(remainingSpace);

        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(toGive);

        int notAdded = leftovers.isEmpty() ? 0 : leftovers.get(0).getAmount();
        int actuallyAdded = remainingSpace - notAdded;

        if (actuallyAdded > 0) {
            int newAmountOnGround = amountOnGround - actuallyAdded;
            stackOnGround.setAmount(newAmountOnGround);
            itemEntity.setItemStack(stackOnGround);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
        } else {
            playRejectEffect(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        Inventory clickedInv = event.getClickedInventory();
        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (clickedInv == null) return;
        
        boolean interactingWithPlayerInv = clickedInv.getType() == InventoryType.PLAYER;

        if (event.isShiftClick() && !interactingWithPlayerInv) {
             if (currentItem != null && currentItem.getType() != Material.AIR) {
                 if (!limitManager.canPickup(player, currentItem.getType(), currentItem.getAmount())) {
                     event.setCancelled(true);
                     playRejectEffect(player);
                 }
             }
             return;
        }

        if (event.getSlotType() == InventoryType.SlotType.RESULT && currentItem != null) {
            if (!limitManager.canPickup(player, currentItem.getType(), currentItem.getAmount())) {
                event.setCancelled(true);
                playRejectEffect(player);
            }
        }
    }
    
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        ItemStack dragged = event.getOldCursor();
        
        boolean involvesPlayerInv = false;
        for (int slot : event.getRawSlots()) {
            if (slot >= event.getView().getTopInventory().getSize()) {
                involvesPlayerInv = true;
                break;
            }
        }
        
        if (involvesPlayerInv) {
            if (!limitManager.canPickup(player, dragged.getType(), dragged.getAmount())) {
                event.setCancelled(true);
                playRejectEffect(player);
            }
        }
    }
}
