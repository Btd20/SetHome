package beeted.sethome;

import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;

import java.util.List;

public class HomeDynamicCommand extends BukkitCommand {
    private final SetHome plugin;

    public HomeDynamicCommand(String name, SetHome plugin) {
        super(name);
        this.plugin = plugin;
        this.setDescription("Open the home menu.");
        this.setUsage("/" + name);
        this.setPermission("sethome.use");
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        return plugin.getCommandExecutor().onCommand(sender, this, label, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return new HomeTabCompleter(plugin).onTabComplete(sender, this, alias, args);
    }
}

