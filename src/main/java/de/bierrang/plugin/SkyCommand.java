package de.bierrang.plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class SkyCommand implements CommandExecutor {

    private final BierSkyDrop plugin;

    public SkyCommand(BierSkyDrop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (args.length == 1 && args[0].equalsIgnoreCase("setup")) {
            if (!p.hasPermission("skydrop.admin")) {
                p.sendMessage(ChatColor.RED + "Keine Rechte.");
                return true;
            }
            openSetupGUI(p);
            return true;
        }

        int maxDrops = getWeeklyDrops(p);
        if (maxDrops == 0) {
            p.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für SkyDrops.");
            return true;
        }

        plugin.getDropManager().checkWeeklyReset(p.getUniqueId(), maxDrops);

        if (!plugin.getDropManager().hasDrops(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Du hast deine wöchentlichen SkyDrops bereits verbraucht.");
            return true;
        }

        if (plugin.getDropManager().getPool().isEmpty()) {
            p.sendMessage(ChatColor.RED + "Fehler: Es wurden keine Items für SkyDrops konfiguriert.");
            p.sendMessage(ChatColor.GRAY + "Ein Admin muss '/skydrop setup' nutzen.");
            return true;
        }

        // --- FIX START ---
        // 1. Loot generieren (mit Spieler UUID für Beacon Limit)
        List<ItemStack> loot = plugin.getDropManager().generateLoot(p.getUniqueId());
        
        // 2. Prüfen ob Loot leer ist
        if (loot == null || loot.isEmpty()) {
            p.sendMessage(ChatColor.RED + "Fehler: Konnte keinen Loot generieren. Pool defekt?");
            return true;
        }

        // 3. Drop verbrauchen (erst jetzt!)
        plugin.getDropManager().useDrop(p.getUniqueId());
        // --- FIX ENDE ---

        World world = p.getWorld();
        Location playerLoc = p.getLocation();

        double offsetX = (Math.random() - 0.5) * 10;
        double offsetZ = (Math.random() - 0.5) * 10;
        int x = (int) (playerLoc.getX() + offsetX);
        int z = (int) (playerLoc.getZ() + offsetZ);
        int y = world.getHighestBlockYAt(x, z);

        Location dropLoc = new Location(world, x + 0.5, y + 1, z + 0.5);

        // 4. Kiste spawnen mit garantiertem Loot
        new ChestSpawner(plugin).spawnChest(dropLoc, loot);

        int remaining = plugin.getDropManager().getDrops(p.getUniqueId());
        p.sendMessage(ChatColor.GREEN + "SkyDrop abgeworfen! Noch übrig: " + remaining);

        return true;
    }

    private int getWeeklyDrops(Player p) {
        for (int i = 9; i >= 1; i--) {
            if (p.hasPermission("skydrop.use." + i)) return i;
        }
        return 0;
    }

    private void openSetupGUI(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§cSkyDrop Pool (Items reinlegen)");
        for (ItemStack item : plugin.getDropManager().getPool()) {
            inv.addItem(item);
        }
        p.openInventory(inv);
    }
}
