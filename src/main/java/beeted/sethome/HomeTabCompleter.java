package beeted.sethome;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class HomeTabCompleter implements TabCompleter {

    private final SetHome plugin;

    public HomeTabCompleter(SetHome plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        plugin.getLogger().info("TabComplete called for command: " + command.getName());

        List<String> suggestions = new ArrayList<>();
        FileConfiguration config = plugin.getConfig();
        String configuredCommand = config.getString("menu.open-command", "/home").replace("/", "");

        if (command.getName().equalsIgnoreCase(configuredCommand) || alias.equalsIgnoreCase(configuredCommand)) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                if (args.length == 1) {
                    if (player.hasPermission("sethome.reload")) {
                        suggestions.add("reload");
                    }
                    if (player.hasPermission("sethome.import.essentials")) {
                        suggestions.add("import");
                    }
                }
                if (args.length == 2) {
                    if (player.hasPermission("sethome.import.essentials")) {
                        suggestions.add("Essentials");
                    }
                    if (player.hasPermission("sethome.import.huskhomes")) {
                        suggestions.add("HuskHomes");
                    }
                }
            }
        }

        return suggestions;
    }


}
