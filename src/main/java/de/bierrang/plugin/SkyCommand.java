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

public class SkyCommand implements CommandExecutor {

    private final BierSkyDrop plugin;

    public SkyCommand(BierSkyDrop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        // Admin Setup
        if (args.length == 1 && args[0].equalsIgnoreCase("setup")) {
            if (!p.hasPermission("skydrop.admin")) {
                p.sendMessage(ChatColor.RED + "Keine Rechte.");
                return true;
            }
            openSetupGUI(p);
            return true;
        }

        // Spieler-Logik
        int level = getDropLevel(p);
        if (level == 0) {
            p.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für SkyDrops.");
            return true;
        }

        // Spieler initialisieren & Drops gutschreiben
        plugin.getDropManager().initPlayer(p.getUniqueId());
        plugin.getDropManager().accrueDrops(p.getUniqueId());

        int available = plugin.getDropManager().getAvailableDrops(p.getUniqueId());
        if (available <= 0) {
            p.sendMessage(ChatColor.RED + "Du hast aktuell keine verfügbaren SkyDrops.");
            return true;
        }

        World farmWorld = Bukkit.getWorld("farmwelt");
        if (farmWorld == null) {
            p.sendMessage(ChatColor.RED + "Die Welt 'farmwelt' wurde nicht gefunden.");
            return true;
        }

        p.sendMessage(ChatColor.GREEN + "Deine SkyDrops fallen in der Farmwelt! Verfügbare Drops: " + available);

        int dropsToUse = available;

        for (int d = 0; d < dropsToUse; d++) {
            for (int i = 0; i < level; i++) {
                Location playerLoc = p.getLocation();
                double baseX = playerLoc.getX();
                double baseZ = playerLoc.getZ();

                double offsetX = (Math.random() - 0.5) * 20; // -10 bis +10
                double offsetZ = (Math.random() - 0.5) * 20;

                int targetX = (int) Math.round(baseX + offsetX);
                int targetZ = (int) Math.round(baseZ + offsetZ);

                int groundY = farmWorld.getHighestBlockYAt(targetX, targetZ);
                Location targetLoc = new Location(farmWorld, targetX + 0.5, groundY + 1, targetZ + 0.5);

                new ChestSpawner(plugin).spawnChest(targetLoc, plugin.getDropManager().generateLoot());
            }

            plugin.getDropManager().consumeDrop(p.getUniqueId());
        }

        return true;
    }

    private int getDropLevel(Player p) {
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
