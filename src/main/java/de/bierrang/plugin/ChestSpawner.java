package de.bierrang.plugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.FallingBlock;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class ChestSpawner {

    private final BierSkyDrop plugin;

    public ChestSpawner(BierSkyDrop plugin) {
        this.plugin = plugin;
    }

    public void spawnChest(Location targetLoc, List<ItemStack> loot) {
        if (loot.isEmpty()) return;
        
        Location spawnLoc = targetLoc.clone().add(0, 15, 0);
        
        // FallingBlock spawn
        FallingBlock fb = spawnLoc.getWorld().spawnFallingBlock(spawnLoc, Material.CHEST.createBlockData());
        fb.setDropItem(false); // Kein Chest Item droppen, wenn er kaputt geht
        
        // Wir müssen tracken, wann er aufschlägt
        new FallTracker(fb, targetLoc.getBlock(), loot).runTaskTimer(plugin, 1L, 1L);
    }

    private class FallTracker extends BukkitRunnable {
        private final FallingBlock fb;
        private final Block targetBlock;
        private final List<ItemStack> loot;

        public FallTracker(FallingBlock fb, Block targetBlock, List<ItemStack> loot) {
            this.fb = fb;
            this.targetBlock = targetBlock;
            this.loot = loot;
        }

        @Override
        public void run() {
            if (fb.isDead()) {
                // Ist aufgeschlagen
                placeChest();
                cancel();
            }
        }

        private void placeChest() {
            // Block setzen
            if (targetBlock.getType().isSolid()) {
                targetBlock = targetBlock.getLocation().add(0, 1, 0).getBlock();
            }
            
            targetBlock.setType(Material.CHEST);
            
            if (targetBlock.getState() instanceof Chest chest) {
                for (ItemStack item : loot) {
                    chest.getBlockInventory().addItem(item);
                }
                chest.update();
            }
            
            // Effekte
            targetBlock.getWorld().playSound(targetBlock.getLocation(), Sound.BLOCK_WOOD_PLACE, 1, 1);
            targetBlock.getWorld().spawnParticle(Particle.CLOUD, targetBlock.getLocation().add(0.5, 0.5, 0.5), 10);
        }
    }
}
