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
    
    // NEU: Speichert, welche "Limitierten Items" ein Spieler schon bekommen hat (z.B. BEACON)
    private final Map<UUID, Set<Material>> receivedRareItems = new HashMap<>();

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
        weeklyDrops.clear();
        storedWeek.clear();
        receivedRareItems.clear();

        // Pool laden
        List<?> list = dataConfig.getList("pool");
        if (list != null) {
            for (Object o : list) {
                try {
                    ItemStack item = null;
                    if (o instanceof ItemStack stack) item = stack;
                    else if (o instanceof ConfigurationSection section) item = ItemStack.deserialize(section.getValues(true));
                    else if (o instanceof Map map) item = ItemStack.deserialize(map);

                    if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                        itemPool.add(item);
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Fehler beim Laden eines Pool-Items: " + e.getMessage());
                }
            }
        }

        // Spieler Daten laden
        if (dataConfig.isConfigurationSection("players")) {
            for (String key : dataConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int drops = dataConfig.getInt("players." + key + ".drops", 0);
                    int week = dataConfig.getInt("players." + key + ".week", getCurrentWeek());
                    weeklyDrops.put(uuid, drops);
                    storedWeek.put(uuid, week);
                    
                    // NEU: Laden der erhaltenen Rare-Items
                    List<String> rareList = dataConfig.getStringList("players." + key + ".rareItems");
                    Set<Material> rares = new HashSet<>();
                    for (String matName : rareList) {
                        try { rares.add(Material.valueOf(matName)); } catch (Exception ignored) {}
                    }
                    receivedRareItems.put(uuid, rares);
                    
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
            
            // NEU: Speichern der Rare-Items
            Set<Material> rares = receivedRareItems.get(uuid);
            if (rares != null) {
                List<String> rareNames = new ArrayList<>();
                for (Material m : rares) rareNames.add(m.name());
                dataConfig.set(base + ".rareItems", rareNames);
            }
        }

        try { dataConfig.save(dataFile); } 
        catch (IOException e) { e.printStackTrace(); }
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
            // NEU: Wochenreset löscht auch die erhaltenen Rare-Items
            receivedRareItems.put(uuid, new HashSet<>());
            save();
        }
    }
    
    // NEU: Prüfen ob Spieler dieses Item schon hatte
    public boolean hasReceivedRareItem(UUID uuid, Material mat) {
        return receivedRareItems.getOrDefault(uuid, new HashSet<>()).contains(mat);
    }
    
    // NEU: Item als erhalten markieren
    public void addRareItemReceived(UUID uuid, Material mat) {
        receivedRareItems.computeIfAbsent(uuid, k -> new HashSet<>()).add(mat);
        save();
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

    // NEU: generateLoot mit UUID für Limitierung
    public List<ItemStack> generateLoot(UUID playerUUID) {
        if (itemPool.isEmpty()) return new ArrayList<>();

        List<ItemStack> loot = new ArrayList<>();
        List<ItemStack> poolCopy = new ArrayList<>(itemPool);
        Collections.shuffle(poolCopy);

        boolean headUsed = false;
        boolean beaconUsed = false; // Wir erlauben nur 1 Beacon pro Drop, zusätzlich zur Wochenlimitierung
        int itemsGenerated = 0;
        Random random = new Random();

        // Temporäre Liste für mögliche Items
        List<ItemStack> possibleRewards = new ArrayList<>();

        // Erstmal schauen, was möglich ist
        for (ItemStack poolItem : poolCopy) {
             if (poolItem != null && poolItem.getType() != Material.AIR) {
                 possibleRewards.add(poolItem);
             }
        }

        // Mischen für Zufall
        Collections.shuffle(possibleRewards);

        for (ItemStack poolItem : possibleRewards) {
            if (itemsGenerated >= 5) break;

            Material mat = poolItem.getType();

            // Kopf Limitierung (nur 1 pro Drop)
            if (mat == Material.PLAYER_HEAD && headUsed) continue;

            // Beacon Limitierung (Wochenlimit & 1 pro Drop)
            if (mat == Material.BEACON) {
                if (beaconUsed) continue; // Schon einen Beacon im aktuellen Drop generiert?
                if (hasReceivedRareItem(playerUUID, Material.BEACON)) {
                    // Spieler hat diese Woche schon einen Beacon -> Überspringen
                    continue;
                }
            }

            // Item erstellen
            ItemStack reward = poolItem.clone();
            if (mat == Material.PLAYER_HEAD) {
                reward.setAmount(1);
                headUsed = true;
            } else if (mat == Material.BEACON) {
                reward.setAmount(1);
                beaconUsed = true;
                // Als erhalten markieren
                addRareItemReceived(playerUUID, Material.BEACON);
            } else {
                reward.setAmount(random.nextInt(5) + 1);
            }
            
            loot.add(reward);
            itemsGenerated++;
        }

        return loot;
    }
}
