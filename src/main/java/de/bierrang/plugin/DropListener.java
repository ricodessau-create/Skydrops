package de.bierrang.plugin;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class DropListener implements Listener {

    private final BierSkyDrop plugin;

    public DropListener(BierSkyDrop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getView().getTitle().equals("§cSkyDrop Pool (Items reinlegen)")) {
            List<ItemStack> newPool = new ArrayList<>();
            Inventory inv = e.getInventory();

            for (ItemStack item : inv.getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    ItemStack clone = item.clone();
                    clone.setAmount(1); // Wir speichern nur das Item, nicht die Menge
                    newPool.add(clone);
                }
            }

            plugin.getDropManager().getPool().clear();
            plugin.getDropManager().getPool().addAll(newPool);
            plugin.getDropManager().save();

            e.getPlayer().sendMessage("§aSkyDrop Pool gespeichert! (" + newPool.size() + " Items)");
        }
    }
}
