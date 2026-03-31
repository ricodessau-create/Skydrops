package de.bierrang.plugin;

import org.bukkit.Material;
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
        
        List<?> list = dataConfig.getList("pool");
        if (list != null) {
            for (Object o : list) {
                if (o instanceof ItemStack item && item.getType() != Material.AIR) {
                    itemPool.add(item);
                } else if (o instanceof Map) {
                    try {
                        ItemStack item = ItemStack.deserialize((Map<String, Object>) o);
                        if (item != null && item.getType() != Material.AIR) itemPool.add(item);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Konnte Item nicht laden: " + e.getMessage());
                    }
                }
            }
        }

        plugin.getLogger().info("§a[BierSkyDrop] §7Geladene Items im Pool: §e" + itemPool.size());
        if (itemPool.isEmpty()) {
            plugin.getLogger().warning("§c[BierSkyDrop] Pool ist leer! Bitte nutze §e/skydrop setup");
        }

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

    public void save() {
        dataConfig.set("pool", itemPool);
        dataConfig.set("players", null);
        
        for (UUID uuid : weeklyDrops.keySet()) {
            String base = "players." + uuid.toString();
            dataConfig.set(base + ".drops", weeklyDrops.get(uuid));
            dataConfig.set(base + ".week", storedWeek.get(uuid));
        }

        try { dataConfig.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    private int getCurrentWeek() {
        return LocalDate.now().get(WeekFields.ISO.weekOfWeekBasedYear());
    }

    public void checkWeeklyReset(UUID uuid, int maxDrops) {
        int currentWeek = getCurrentWeek();
        int savedWeek = storedWeek.getOrDefault(uuid, -1);

        if (savedWeek != currentWeek) {
            weeklyDrops.put(uuid, maxDrops);
            storedWeek.put(uuid, currentWeek);
            save();
        }
    }

    public int getDrops(UUID uuid) {
        return weeklyDrops.getOrDefault(uuid, 0);
    }

    public boolean hasDrops(UUID uuid) {
        return getDrops(uuid) > 0;
    }

    public void useDrop(UUID uuid) {
        int current = weeklyDrops.getOrDefault(uuid, 0);
        if (current > 0) {
            weeklyDrops.put(uuid, current - 1);
            save();
        }
    }

    public List<ItemStack> getPool() { return itemPool; }

    public List<ItemStack> generateLoot() {
        if (itemPool.isEmpty()) {
            plugin.getLogger().warning("§c[BierSkyDrop] FEHLER: Keine Items zum Droppen! Pool ist leer.");
            return new ArrayList<>();
        }

        List<ItemStack> loot = new ArrayList<>();
        
        // 1. Kopie erstellen und von Müll befreien (AIR, null)
        List<ItemStack> poolCopy = new ArrayList<>();
        for (ItemStack item : itemPool) {
            if (item != null && item.getType() != Material.AIR) {
                poolCopy.add(item);
            }
        }
        
        if (poolCopy.isEmpty()) {
            return new ArrayList<>(); // Sollte nicht passieren, aber sicher ist sicher
        }

        Collections.shuffle(poolCopy);

        boolean headUsed = false;
        int itemsGenerated = 0;
        Random random = new Random();

        for (ItemStack poolItem : poolCopy) {
            if (itemsGenerated >= 5) break;

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

        // FIX: Falls itemsGenerated 0 ist (kommt hier nicht mehr vor, aber als Fallback)
        if (loot.isEmpty() && !poolCopy.isEmpty()) {
             ItemStack fallback = poolCopy.get(0).clone();
             fallback.setAmount(1);
             loot.add(fallback);
        }

        plugin.getLogger().info("§b[BierSkyDrop] Loot generiert: §f" + loot.size() + " Items.");
        return loot;
    }
}
