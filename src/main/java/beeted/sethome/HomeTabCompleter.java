package beeted.sethome;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HomeTabCompleter implements TabCompleter {

    private final SetHome plugin;

    public HomeTabCompleter(SetHome plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        List<String> suggestions = new ArrayList<>();
        FileConfiguration config = plugin.getConfig();
        String configuredCommand = config.getString("menu.open-command", "/home").replace("/", "");

        if (command.getName().equalsIgnoreCase(configuredCommand) || alias.equalsIgnoreCase(configuredCommand)) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                // /homegui <...>
                if (args.length == 1) {
                    if (player.hasPermission("sethome.reload")) suggestions.add("reload");
                    if (player.hasPermission("sethome.import.essentials") || player.hasPermission("sethome.import.huskhomes")) suggestions.add("import");
                    if (player.hasPermission("sethome.admin")) suggestions.add("admin");
                    suggestions.add("create");
                    suggestions.add("delete");
                }

                // /homegui open <jugador>
                if (args.length == 2 && args[0].equalsIgnoreCase("open")) {
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        suggestions.add(onlinePlayer.getName());
                    }
                }

                // /homegui import <Essentials/HuskHomes>
                if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
                    if (player.hasPermission("sethome.import.essentials")) suggestions.add("Essentials");
                    if (player.hasPermission("sethome.import.huskhomes")) suggestions.add("HuskHomes");
                }

                // /homegui create <HOME_NAME>
                if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
                    suggestions.add("<home_name>");
                }

                // /homegui delete <HOME_NAME>
                if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
                    File playerFile = plugin.getPlayerDataFile(player.getUniqueId());
                    if (playerFile.exists()) {
                        suggestions.addAll(plugin.getHomesFor(player));
                    }
                }

                // /homegui admin <create/delete>
                if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
                    suggestions.add("create");
                    suggestions.add("delete");
                    suggestions.add("seeplayer");
                }

                // /homegui admin create <jugador>
                if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("create")) {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        suggestions.add(online.getName());
                    }
                }

                // /homegui admin create <jugador> <home_name>
                if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("create")) {
                    suggestions.add("<home_name>");
                }

                // /homegui admin create <jugador> <home_name> <X/Y/Z>
                if (args.length >= 5 && args.length <= 6 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("create")) {
                    suggestions.add("<x>");
                    suggestions.add("<y>");
                }

                if (args.length == 7 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("create")) {
                    suggestions.add("<z>");
                }

                // /homegui admin delete <jugador>
                if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("delete")) {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        suggestions.add(online.getName());
                    }
                }

                // /homegui admin seeplayer <jugador>
                if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("seeplayer")) {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        suggestions.add(online.getName());
                    }
                }

                // /homegui admin delete <jugador> <home_name>
                if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("delete")) {
                    Player target = Bukkit.getPlayer(args[2]);
                    if (target != null) {
                        suggestions.addAll(plugin.getHomesFor(target));
                    } else {
                        suggestions.add("<home_name>");
                    }
                }
            }
        }

        return suggestions;
    }
}
