package de.bierrang.plugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;

public class ChestSpawner {

    private final BierSkyDrop plugin;

    public ChestSpawner(BierSkyDrop plugin) {
        this.plugin = plugin;
    }

    public void spawnChest(Location targetLoc, List<ItemStack> loot) {
        if (loot == null || loot.isEmpty()) {
            plugin.getLogger().warning("Spawn abgebrochen: Kein Loot vorhanden.");
            return;
        }

        Location startLoc = targetLoc.clone().add(0, 25, 0);

        ArmorStand stand = (ArmorStand) startLoc.getWorld().spawnEntity(startLoc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setCanPickupItems(false);
        stand.setInvulnerable(true);
        stand.setMarker(false);
        
        stand.getEquipment().setHelmet(new ItemStack(Material.CHEST));

        new FallAnimation(stand, targetLoc, loot).runTaskTimer(plugin, 0L, 1L);
    }

    private class FallAnimation extends BukkitRunnable {
        private final ArmorStand stand;
        private final Location targetLoc;
        private final List<ItemStack> loot;
        
        private boolean placed = false;
        private double currentY;
        private final double targetY;
        private int ticks = 0;
        private final Random random = new Random();

        public FallAnimation(ArmorStand stand, Location targetLoc, List<ItemStack> loot) {
            this.stand = stand;
            this.targetLoc = targetLoc;
            this.loot = loot;
            this.currentY = stand.getLocation().getY();
            this.targetY = targetLoc.getY();
        }

        @Override
        public void run() {
            if (placed || stand.isDead()) {
                cancel();
                return;
            }

            ticks++;

            // 1. BEWEGUNG (Langsames Fallen)
            currentY -= 0.15; 
            
            Location newLoc = stand.getLocation().clone();
            newLoc.setY(currentY);
            
            double shakeX = (random.nextDouble() - 0.5) * 0.05;
            double shakeZ = (random.nextDouble() - 0.5) * 0.05;
            newLoc.add(shakeX, 0, shakeZ);
            
            stand.teleport(newLoc);

            // 2. ROTATION
            float yaw = (ticks * 5) % 360;
            stand.setRotation(yaw, 0);

            // 3. PARTIKEL (FIXED NAMES)
            // SMOKE_NORMAL -> SMOKE
            stand.getWorld().spawnParticle(Particle.SMOKE, stand.getLocation().add(0, -0.5, 0), 2, 0.1, 0.1, 0.1, 0.01);
            
            if (ticks % 10 == 0) {
                stand.getWorld().spawnParticle(Particle.END_ROD, stand.getLocation().add(0, 0.5, 0), 1, 0.2, 0.2, 0.2, 0);
            }

            // 4. LANDE CHECK
            if (currentY <= targetY) {
                placeChest();
                placed = true;
                cancel();
            }
        }

        private void placeChest() {
            stand.remove();

            Location loc = targetLoc.clone();
            Block block = loc.getBlock();

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
                chest.update(true);
                plugin.getLogger().info("Kiste sanft gelandet bei: " + block.getLocation());
            }

            // LANDE EFFECT (FIXED NAMES)
            // EXPLOSION_LARGE -> EXPLOSION
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_WOOD_PLACE, 2, 0.8f);
            block.getWorld().spawnParticle(Particle.CLOUD, block.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.2, 0.3, 0.05);
            block.getWorld().spawnParticle(Particle.EXPLOSION, block.getLocation().add(0.5, 0.5, 0.5), 1);
        }
    }
}
