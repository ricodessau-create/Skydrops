package de.bierrang.plugin;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DropManager {

    private final BierSkyDrop plugin;
    private final File dataFile;
    private final FileConfiguration dataConfig;

    private final List<ItemStack> itemPool = new ArrayList<>();

    private final Map<UUID, Integer> availableDrops = new HashMap<>();
    private final Map<UUID, Long> lastAccrual = new HashMap<>();

    public DropManager(BierSkyDrop plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void load() {
        itemPool.clear();
        List<?> list = dataConfig.getList("pool");
        if (list != null) {
            for (Object o : list) {
                if (o instanceof ItemStack item) {
                    if (item.getType() != Material.AIR) {
                        itemPool.add(item);
                    }
                }
            }
        }

        availableDrops.clear();
        lastAccrual.clear();

        if (dataConfig.isConfigurationSection("players")) {
            for (String key : dataConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int drops = dataConfig.getInt("players." + key + ".drops", 0);
                    long last = dataConfig.getLong("players." + key + ".last", 0L);
                    availableDrops.put(uuid, drops);
                    lastAccrual.put(uuid, last);
                } catch (IllegalArgumentException ignored) {
                    // falls mal ein kaputter Key drin ist, ignorieren
                }
            }
        }
    }

    public void save() {
        dataConfig.set("pool", itemPool);

        dataConfig.set("players", null);
        for (Map.Entry<UUID, Integer> entry : availableDrops.entrySet()) {
            UUID uuid = entry.getKey();
            int drops = entry.getValue();
            long last = lastAccrual.getOrDefault(uuid, 0L);

            String base = "players." + uuid.toString();
            dataConfig.set(base + ".drops", drops);
            dataConfig.set(base + ".last", last);
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<ItemStack> getPool() {
        return itemPool;
    }

    // Stunden aus config.yml, z.B. cooldown-hours: 24
    private long getCooldownMillis() {
        long hours = plugin.getConfig().getLong("cooldown-hours", 24);
        return hours * 60L * 60L * 1000L;
    }

    // Maximal ansammelbare Drops, z.B. max-drops: 7
    private int getMaxDrops() {
        return plugin.getConfig().getInt("max-drops", 7);
    }

    // Rechnet neue Drops gut, basierend auf Zeit
    public void accrueDrops(UUID uuid) {
        long now = System.currentTimeMillis();
        long cooldownMs = getCooldownMillis();

        long last = lastAccrual.getOrDefault(uuid, 0L);
        if (last == 0L) {
            lastAccrual.put(uuid, now);
            return;
        }

        long diff = now - last;
        if (diff < cooldownMs) return;

        int gained = (int) (diff / cooldownMs);
        if (gained <= 0) return;

        int current = availableDrops.getOrDefault(uuid, 0);
        int max = getMaxDrops();
        int newTotal = Math.min(max, current + gained);

        availableDrops.put(uuid, newTotal);
        long usedTime = (long) gained * cooldownMs;
        lastAccrual.put(uuid, last + usedTime);
        save();
    }

    public int getAvailableDrops(UUID uuid) {
        return availableDrops.getOrDefault(uuid, 0);
    }

    public boolean hasDrops(UUID uuid) {
        return getAvailableDrops(uuid) > 0;
    }

    public void consumeDrop(UUID uuid) {
        int current = availableDrops.getOrDefault(uuid, 0);
        if (current <= 0) return;
        availableDrops.put(uuid, current - 1);
        save();
    }

    // Wird aufgerufen, wenn ein Spieler das erste Mal /skydrop nutzt
    public void initPlayer(UUID uuid) {
        if (!lastAccrual.containsKey(uuid)) {
            lastAccrual.put(uuid, System.currentTimeMillis());
            availableDrops.putIfAbsent(uuid, 0);
            save();
        }
    }

    // Loot-Regeln: max 5 verschiedene Items, 1–5 Stück, Köpfe max 1
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

            if (poolItem == null || poolItem.getType() == Material.AIR) continue;

            boolean isHead = poolItem.getType() == Material.PLAYER_HEAD;

            if (isHead && headUsed) continue;

            ItemStack reward = poolItem.clone();

            if (isHead) {
                reward.setAmount(1);
                headUsed = true;
            } else {
                int amount = random.nextInt(5) + 1; // 1–5
                reward.setAmount(amount);
            }

            loot.add(reward);
            itemsGenerated++;
        }

        return loot;
    }
}
