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
        if (loot == null || loot.isEmpty()) return;

        Location spawnLoc = targetLoc.clone().add(0, 20, 0);
        Block targetBlock = targetLoc.getBlock();

        FallingBlock fb = spawnLoc.getWorld().spawnFallingBlock(spawnLoc, Material.CHEST.createBlockData());
        fb.setDropItem(false);

        new FallTracker(fb, targetBlock, loot).runTaskTimer(plugin, 1L, 1L);
    }

    private class FallTracker extends BukkitRunnable {
        private final FallingBlock fb;
        private Block targetBlock;
        private final List<ItemStack> loot;

        public FallTracker(FallingBlock fb, Block targetBlock, List<ItemStack> loot) {
            this.fb = fb;
            this.targetBlock = targetBlock;
            this.loot = loot;
        }

        @Override
        public void run() {
            if (fb.isDead() || fb.isOnGround()) {
                placeChest();
                cancel();
            }
        }

        private void placeChest() {
            Location loc = fb.getLocation();
            Block block = loc.getBlock();

            // Platz suchen
            if (block.getType().isSolid()) {
                block = block.getLocation().add(0, 1, 0).getBlock();
            }
            int attempts = 0;
            while (block.getType().isSolid() && attempts < 5) {
                block = block.getLocation().add(0, 1, 0).getBlock();
                attempts++;
            }

            block.setType(Material.CHEST);

            if (block.getState() instanceof Chest chest) {
                for (ItemStack item : loot) {
                    if (item != null && item.getType() != Material.AIR) {
                        chest.getBlockInventory().addItem(item);
                    }
                }
                chest.update();
            }

            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_WOOD_PLACE, 1, 1);
            block.getWorld().spawnParticle(Particle.CLOUD, block.getLocation().add(0.5, 0.5, 0.5), 10);
        }
    }
}
