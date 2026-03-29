package de.bierrang.plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
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

        if (args.length == 1 && args[0].equalsIgnoreCase("setup")) {
            if (!p.hasPermission("skydrop.admin")) {
                p.sendMessage(ChatColor.RED + "Keine Rechte.");
                return true;
            }
            openSetupGUI(p);
            return true;
        }

        // Claim Logik
        int level = getDropLevel(p);
        if (level == 0) {
            p.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für SkyDrops.");
            return true;
        }

        if (!plugin.getDropManager().canClaim(p.getUniqueId())) {
            p.sendMessage(ChatColor.RED + "Du hast deinen täglichen Drop bereits abgeholt.");
            return true;
        }

        // Drop ausführen
        plugin.getDropManager().setCooldown(p.getUniqueId());
        p.sendMessage(ChatColor.GREEN + "Dein SkyDrop fällt vom Himmel!");
        
        // `level` = Anzahl der Kisten
        for (int i = 0; i < level; i++) {
            // Spawn Chest
            new ChestSpawner(plugin).spawnChest(p.getLocation(), plugin.getDropManager().generateLoot());
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
        
        // Items laden
        for (ItemStack item : plugin.getDropManager().getPool()) {
            inv.addItem(item);
        }
        
        p.openInventory(inv);
    }
}
