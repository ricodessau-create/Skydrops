// ... imports ...

public class SkyCommand implements CommandExecutor {

    // ... Constructor etc ...

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        // ... Permission Checks (bleiben gleich) ...
        
        // WICHTIG: UUID mit übergeben
        List<ItemStack> loot = plugin.getDropManager().generateLoot(p.getUniqueId());
        
        // ... Rest bleibt gleich ...
        
        if (loot.isEmpty()) {
             p.sendMessage(ChatColor.RED + "Keine Items generiert (Pool leer oder Limits erreicht).");
             return true;
        }
        
        // ... Spawn Chest Logic ...
        new ChestSpawner(plugin).spawnChest(dropLoc, loot);
        
        // ...
        return true;
    }
    // ...
}
