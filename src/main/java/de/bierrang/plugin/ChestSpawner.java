package de.bierrang.plugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

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

        Location startLoc = targetLoc.clone().add(0, 25, 0); // Start etwas höher

        // ArmorStand erstellen (unsichtbar, trägt Kiste)
        ArmorStand stand = (ArmorStand) startLoc.getWorld().spawnEntity(startLoc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setCanPickupItems(false);
        stand.setInvulnerable(true);
        stand.setMarker(false); // Marker false, damit er angeschubst werden kann, aber wir bewegen ihn manuell
        
        // Kiste auf den Kopf setzen
        stand.getEquipment().setHelmet(new ItemStack(Material.CHEST));

        // Animation starten
        new FallAnimation(stand, targetLoc, loot).runTaskTimer(plugin, 0L, 1L); // Jeden Tick updaten
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
            // Geschwindigkeit: 0.15 Blöcke pro Tick (ca. 3 Blöcke pro Sekunde)
            // Kannst du anpassen: 0.1 = sehr langsam, 0.4 = schneller
            currentY -= 0.15; 
            
            // Neue Position setzen
            Location newLoc = stand.getLocation().clone();
            newLoc.setY(currentY);
            
            // Leichtes Wackeln/Shake Effekt
            double shakeX = (random.nextDouble() - 0.5) * 0.05;
            double shakeZ = (random.nextDouble() - 0.5) * 0.05;
            newLoc.add(shakeX, 0, shakeZ);
            
            stand.teleport(newLoc);

            // 2. ROTATION (Kiste dreht sich beim Fallen)
            float yaw = (ticks * 5) % 360; // Dreht sich kontinuierlich
            stand.setRotation(yaw, 0);

            // 3. PARTIKEL (Rauch/Sterne unter der Kiste)
            stand.getWorld().spawnParticle(Particle.SMOKE_NORMAL, stand.getLocation().add(0, -0.5, 0), 2, 0.1, 0.1, 0.1, 0.01);
            
            // Alle 10 Ticks ein "Magie" Funken
            if (ticks % 10 == 0) {
                stand.getWorld().spawnParticle(Particle.END_ROD, stand.getLocation().add(0, 0.5, 0), 1, 0.2, 0.2, 0.2, 0);
            }

            // 4. LANDE CHECK
            // Wenn wir die Zielhöhe unterschreiten oder Block berühren
            if (currentY <= targetY) {
                placeChest();
                placed = true;
                cancel();
            }
        }

        private void placeChest() {
            // ArmorStand entfernen
            stand.remove();

            Location loc = targetLoc.clone();
            Block block = loc.getBlock();

            // Wenn Block fest ist, eins drüber setzen
            if (block.getType().isSolid()) {
                block = block.getLocation().add(0, 1, 0).getBlock();
            }
            
            // Falls immer noch fest, suchen wir Platz
            int attempts = 0;
            while (block.getType().isSolid() && attempts < 5) {
                block = block.getLocation().add(0, 1, 0).getBlock();
                attempts++;
            }

            // Block setzen
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

            // LANDE EFFECT
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_WOOD_PLACE, 2, 0.8f); // Tieferer Ton
            block.getWorld().spawnParticle(Particle.CLOUD, block.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.2, 0.3, 0.05);
            block.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, block.getLocation().add(0.5, 0.5, 0.5), 1); // Kleiner Knall-Effekt
        }
    }
}
