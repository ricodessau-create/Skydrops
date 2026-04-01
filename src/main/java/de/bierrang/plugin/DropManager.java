package de.bierrang.plugin;

import org.bukkit.Bukkit;
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
        
        plugin.getLogger().info("==========================================");
        plugin.getLogger().info("[BierSkyDrop] Starte Laden der Daten...");
        plugin.getLogger().info("Datei existiert: " + dataFile.exists());
        plugin.getLogger().info("Pfad: " + dataFile.getAbsolutePath());

        List<?> list = dataConfig.getList("pool");
        plugin.getLogger().info("Liste aus Config geholt: " + (list != null ? list.size() + " Einträge" : "NULL"));

        if (list != null) {
            for (Object o : list) {
                try {
                    ItemStack item = null;

                    if (o instanceof ItemStack stack) {
                        item = stack;
                        plugin.getLogger().info(" - Item direkt gefunden: " + item.getType());
                    }
                    else if (o instanceof ConfigurationSection section) {
                        Map<String, Object> map = section.getValues(true);
                        item = ItemStack.deserialize(map);
                        plugin.getLogger().info(" - Item aus Section geladen: " + (item != null ? item.getType() : "NULL"));
                    }
                    else if (o instanceof Map map) {
                        item = ItemStack.deserialize(map);
                        plugin.getLogger().info(" - Item aus Map geladen: " + (item != null ? item.getType() : "NULL"));
                    }
                    else {
                        plugin.getLogger().warning(" - Unbekanntes Objekt im Pool: " + o.getClass().getName());
                    }

                    if (item != null && item.getType() != Material.AIR) {
                        itemPool.add(item);
                    } else {
                         plugin.getLogger().warning(" - Item war null oder AIR nach dem Laden!");
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("FEHLER beim Laden eines Items: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        plugin.getLogger().info("==========================================");
        plugin.getLogger().info("§a[BierSkyDrop] §7Endgültig geladene Items im Pool: §e" + itemPool.size());
        plugin.getLogger().info("==========================================");

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

        try { 
            dataConfig.save(dataFile); 
            plugin.getLogger().info("[BierSkyDrop] Daten gespeichert.");
        } catch (IOException e) { 
            plugin.getLogger().severe("[BierSkyDrop] Konnte data.yml NICHT speichern!");
            e.printStackTrace(); 
        }
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
        plugin.getLogger().info("--- GENERATE LOOT START ---");
        plugin.getLogger().info("Pool Größe: " + itemPool.size());
        
        if (itemPool.isEmpty()) {
            plugin.getLogger().warning("§c[BierSkyDrop] FEHLER: Pool ist leer beim Generieren!");
            return new ArrayList<>();
        }

        List<ItemStack> loot = new ArrayList<>();
        List<ItemStack> poolCopy = new ArrayList<>();
        
        for (ItemStack item : itemPool) {
            if (item != null && item.getType() != Material.AIR) poolCopy.add(item);
        }
        
        if (poolCopy.isEmpty()) {
             plugin.getLogger().warning("PoolCopy ist leer (Items waren null oder AIR?)");
             return new ArrayList<>();
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
            plugin.getLogger().info("Loot hinzugefügt: " + reward.getType() + " x" + reward.getAmount());
            itemsGenerated++;
        }
        
        if (loot.isEmpty()) {
            ItemStack fallback = poolCopy.get(0).clone();
            fallback.setAmount(1);
            loot.add(fallback);
             plugin.getLogger().warning("Loot war leer, Fallback genutzt!");
        }

        plugin.getLogger().info("--- GENERATE LOOT ENDE: " + loot.size() + " Items ---");
        return loot;
    }
}
