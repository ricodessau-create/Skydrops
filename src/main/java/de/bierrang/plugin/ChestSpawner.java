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
                int itemCount = 0;
                for (ItemStack item : loot) {
                    if (item != null && item.getType() != Material.AIR) {
                        chest.getBlockInventory().addItem(item);
                        itemCount++;
                    }
                }
                chest.update(true); // Force update
                
                // WICHTIGER DEBUG LOG
                plugin.getLogger().info("§a[BierSkyDrop] Kiste platziert. Items reingelegt: §e" + itemCount);
            } else {
                plugin.getLogger().severe("§c[BierSkyDrop] FEHLER: Konnte Kiste nicht initialisieren.");
            }

            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_WOOD_PLACE, 2, 0.8f);
            block.getWorld().spawnParticle(Particle.CLOUD, block.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.2, 0.3, 0.05);
            block.getWorld().spawnParticle(Particle.EXPLOSION, block.getLocation().add(0.5, 0.5, 0.5), 1);
        }
