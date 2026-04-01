package de.bierrang.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
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

            currentY -= 0.15; 
            
            Location newLoc = stand.getLocation().clone();
            newLoc.setY(currentY);
            
            double shakeX = (random.nextDouble() - 0.5) * 0.05;
            double shakeZ = (random.nextDouble() - 0.5) * 0.05;
            newLoc.add(shakeX, 0, shakeZ);
            
            stand.teleport(newLoc);

            float yaw = (ticks * 5) % 360;
            stand.setRotation(yaw, 0);

            stand.getWorld().spawnParticle(Particle.SMOKE, stand.getLocation().add(0, -0.5, 0), 2, 0.1, 0.1, 0.1, 0.01);
            
            if (ticks % 10 == 0) {
                stand.getWorld().spawnParticle(Particle.END_ROD, stand.getLocation().add(0, 0.5, 0), 1, 0.2, 0.2, 0.2, 0);
            }

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
            
            final Block finalBlock = block;
            final List<ItemStack> finalLoot = new ArrayList<>(loot);

            // FIX: TileEntity erzwingen (Standard getState) + 2 Ticks warten
            try {
                finalBlock.getState(); 
            } catch (Exception e) {
                e.printStackTrace();
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    if (!(finalBlock.getState() instanceof Chest chest)) {
                        plugin.getLogger().severe("Kiste nicht gefunden!");
                        safeDrop(finalBlock, finalLoot);
                        return;
                    }

                    Inventory inv = chest.getBlockInventory();
                    for (ItemStack item : finalLoot) {
                        if (item != null && item.getType() != Material.AIR) {
                            inv.addItem(item);
                        }
                    }
                    
                    chest.update(true);

                    int check = 0;
                    for (ItemStack i : inv.getContents()) {
                        if (i != null) check += i.getAmount();
                    }

                    if (check == 0) {
                        plugin.getLogger().warning("Inventar leer -> Safe Drop.");
                        safeDrop(finalBlock, finalLoot);
                    } else {
                        plugin.getLogger().info("§aErfolg! Items: " + check);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    safeDrop(finalBlock, finalLoot);
                }
            }, 2L);

            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_WOOD_PLACE, 2, 0.8f);
            block.getWorld().spawnParticle(Particle.CLOUD, block.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.2, 0.3, 0.05);
            block.getWorld().spawnParticle(Particle.EXPLOSION, block.getLocation().add(0.5, 0.5, 0.5), 1);
        }

        private void safeDrop(Block block, List<ItemStack> items) {
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) {
                    // Droppen im Block (unsichtbar, sicher)
                    block.getWorld().dropItem(block.getLocation().add(0.5, 0.5, 0.5), item);
                }
            }
        }
    }
}
