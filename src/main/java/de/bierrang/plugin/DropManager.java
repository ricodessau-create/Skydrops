package de.bierrang.plugin;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;

public class DropManager {

    private final BierSkyDrop plugin;
    private final File dataFile;
    private final FileConfiguration dataConfig;

    private final List<ItemStack> itemPool = new ArrayList<>();
    private final Map<UUID, Integer> weeklyDrops = new HashMap<>();
    private final Map<UUID, Integer> storedWeek = new HashMap<>();

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
        
        // ... (Logging Kram kannst du behalten oder löschen) ...
        
        List<?> list = dataConfig.getList("pool");
        
        if (list != null) {
            for (Object o : list) {
                try {
                    ItemStack item = null;

                    if (o instanceof ItemStack stack) {
                        item = stack;
                    }
                    else if (o instanceof ConfigurationSection section) {
                        Map<String, Object> map = section.getValues(true);
                        item = ItemStack.deserialize(map);
                    }
                    else if (o instanceof Map map) {
                        item = ItemStack.deserialize(map);
                    }

                    // VALIDIERUNG FÜR 1.21
                    if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                        itemPool.add(item);
                    } else {
                         plugin.getLogger().warning("Ungültiges Item im Pool gefunden (Air, Amount 0 oder null) - wird übersprungen.");
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Konnte Item nicht laden: " + e.getMessage());
                    // Dies passiert oft bei alten NBT Daten in 1.21
                }
            }
        }
        
        // Spieler Daten laden...
        weeklyDrops.clear();
        storedWeek.clear();
        if (dataConfig.isConfigurationSection("players")) {
            for (String key : dataConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int drops = dataConfig.getInt("players." + key + ".drops", 0);
                    int week = dataConfig.getInt("players." + key + ".week", getCurrentWeek());
                    weeklyDrops.put(uuid, drops);
                    storedWeek.put(uuid, week);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }
    
    // ... Rest der Klasse bleibt gleich (save, checkWeeklyReset, etc.) ...
    // Achte darauf, dass save() existiert (siehe dein Code oben).
    
    public void save() {
        dataConfig.set("pool", itemPool);
        dataConfig.set("players", null);
        
        for (UUID uuid : weeklyDrops.keySet()) {
            String base = "players." + uuid.toString();
            dataConfig.set(base + ".drops", weeklyDrops.get(uuid));
            dataConfig.set(base + ".week", storedWeek.get(uuid));
        }

        try { 
            dataConfig.save(dataFile); 
        } catch (IOException e) { 
            e.printStackTrace(); 
        }
    }

    private int getCurrentWeek() {
        return LocalDate.now().get(WeekFields.ISO.weekOfWeekBasedYear());
    }
    
    // ... Getter und generateLoot bleiben gleich ...
    public void checkWeeklyReset(UUID uuid, int maxDrops) {
        int currentWeek = getCurrentWeek();
        int savedWeek = storedWeek.getOrDefault(uuid, -1);
        if (savedWeek != currentWeek) {
            weeklyDrops.put(uuid, maxDrops);
            storedWeek.put(uuid, currentWeek);
            save();
        }
    }

    public int getDrops(UUID uuid) { return weeklyDrops.getOrDefault(uuid, 0); }
    public boolean hasDrops(UUID uuid) { return getDrops(uuid) > 0; }
    public void useDrop(UUID uuid) {
        int current = weeklyDrops.getOrDefault(uuid, 0);
        if (current > 0) {
            weeklyDrops.put(uuid, current - 1);
            save();
        }
    }
    public List<ItemStack> getPool() { return itemPool; }

    public List<ItemStack> generateLoot() {
        if (itemPool.isEmpty()) return new ArrayList<>();
        
        List<ItemStack> loot = new ArrayList<>();
        List<ItemStack> poolCopy = new ArrayList<>(itemPool);
        Collections.shuffle(poolCopy);

        boolean headUsed = false;
        int itemsGenerated = 0;
        Random random = new Random();

        for (ItemStack poolItem : poolCopy) {
            if (itemsGenerated >= 5) break;
            
            // Sicherheitscheck
            if (poolItem == null || poolItem.getType() == Material.AIR) continue;

            boolean isHead = poolItem.getType() == Material.PLAYER_HEAD;
            if (isHead && headUsed) continue;

            ItemStack reward = poolItem.clone();
            if (isHead) {
                reward.setAmount(1);
                headUsed = true;
            } else {
                reward.setAmount(random.nextInt(5) + 1);
            }
            loot.add(reward);
            itemsGenerated++;
        }
        return loot;
    }
}
