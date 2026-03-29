package de.bierrang.plugin;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class DropManager {

    private final BierSkyDrop plugin;
    private final File dataFile;
    private final FileConfiguration dataConfig;
    private final List<ItemStack> itemPool = new ArrayList<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public DropManager(BierSkyDrop plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void load() {
        itemPool.clear();
        List<?> list = dataConfig.getList("pool");
        if (list != null) {
            for (Object o : list) {
                if (o instanceof ItemStack item) itemPool.add(item);
            }
        }
        
        cooldowns.clear();
        for (String key : dataConfig.getKeys(false)) {
            if (key.equals("pool")) continue;
            cooldowns.put(UUID.fromString(key), dataConfig.getLong(key));
        }
    }

    public void save() {
        dataConfig.set("pool", itemPool);
        for (Map.Entry<UUID, Long> entry : cooldowns.entrySet()) {
            dataConfig.set(entry.getKey().toString(), entry.getValue());
        }
        try { dataConfig.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public List<ItemStack> getPool() { return itemPool; }

    public boolean canClaim(UUID uuid) {
        long last = cooldowns.getOrDefault(uuid, 0L);
        long now = System.currentTimeMillis();
        
        // Liest die Stunden aus der Config und rechnet sie in Millisekunden um
        long hours = plugin.getConfig().getLong("cooldown-hours", 24);
        long cooldownMs = hours * 60 * 60 * 1000;
        
        return (now - last) > cooldownMs;
    }

    public void setCooldown(UUID uuid) {
        cooldowns.put(uuid, System.currentTimeMillis());
        save();
    }

    // Zieht 5 Items aus dem Pool. Beachtet Head-Regel.
    public List<ItemStack> generateLoot() {
        if (itemPool.isEmpty()) return new ArrayList<>();
        
        List<ItemStack> loot = new ArrayList<>();
        List<ItemStack> poolCopy = new ArrayList<>(itemPool);
        Collections.shuffle(poolCopy);
        
        boolean headUsed = false;
        int itemsGenerated = 0;

        for (ItemStack poolItem : poolCopy) {
            if (itemsGenerated >= 5) break;

            boolean isHead = poolItem.getType() == Material.PLAYER_HEAD;
            
            if (isHead && headUsed) continue;

            ItemStack reward = poolItem.clone();
            
            if (isHead) {
                reward.setAmount(1);
                headUsed = true;
            } else {
                int amount = new Random().nextInt(5) + 1;
                reward.setAmount(amount);
            }
            
            loot.add(reward);
            itemsGenerated++;
        }
        
        return loot;
    }
}
