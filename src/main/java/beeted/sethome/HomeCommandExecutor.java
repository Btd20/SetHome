package beeted.sethome;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.List;

public class HomeCommandExecutor implements CommandExecutor {
    private final SetHome plugin;
    private final HomeImporter homeImporter;
    private final Menu menu;

    public HomeCommandExecutor(SetHome plugin) {
        this.plugin = plugin;
        this.homeImporter = new HomeImporter(plugin);
        this.menu = new Menu(plugin);
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration config = plugin.getConfig();
        String userCommand = config.getString("menu.open-command", "/home").replace("/", "");

        // Verifica si el comando coincide con el configurado
        if (!label.equalsIgnoreCase(userCommand)) {
            return false;
        }

        // Comando /home reload
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (sender.hasPermission("sethome.reload")) {
                plugin.reloadConfig();
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.plugin-reloaded", "&aPlugin reloaded successfully.")));
            } else {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permissions", "&cYou don't have permission to do that.")));
            }
            return true;
        }

        // Comando /home import Essentials
        if (args.length > 1 && args[0].equalsIgnoreCase("import") && args[1].equalsIgnoreCase("Essentials")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (!player.hasPermission("sethome.import.essentials")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to import homes.");
                    return true;
                }
                homeImporter.importHomesFromEssentialsForAllPlayers(player);
            } else if (sender instanceof ConsoleCommandSender) {
                homeImporter.importHomesFromEssentialsForAllPlayers(sender);
            } else {
                sender.sendMessage(ChatColor.RED + "This command can only be executed by a player or console.");
            }
            return true;
        }

        //Comando /home import HuskHomes
        if (args.length > 1 && args[0].equalsIgnoreCase("import") && args[1].equalsIgnoreCase("HuskHomes")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (!player.hasPermission("sethome.import.huskhomes")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to import homes.");
                    return true;
                }
                homeImporter.importHomesFromHuskHomesForAllPlayers(player,"HuskHomes/HuskHomesData.db");
            } else if (sender instanceof ConsoleCommandSender) {
                homeImporter.importHomesFromHuskHomesForAllPlayers(sender,"HuskHomes/HuskHomesData.db");
            } else {
                sender.sendMessage(ChatColor.RED + "This command can only be executed by a player or console.");
            }
            return true;
        }

        if (sender instanceof Player) {
            Player player = (Player) sender;
            // Lógica para abrir el menú
            menu.openMainMenu(player);
        }
        return true;
    }


    public static void registerDynamicCommand(SetHome plugin, String commandName) {
        try {
            // Obtener el CommandMap
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            // Crear el comando dinámico
            BukkitCommand dynamicCommand = new BukkitCommand(commandName) {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return plugin.getCommandExecutor().onCommand(sender, this, label, args);
                }

                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                    return new HomeTabCompleter(plugin).onTabComplete(sender, this, alias, args);
                }
            };

            // Configurar propiedades del comando
            dynamicCommand.setDescription("Open the home menu.");
            dynamicCommand.setUsage("/" + commandName);
            dynamicCommand.setPermission("sethome.use");

            // Registrar el comando dinámico
            commandMap.register(plugin.getDescription().getName(), dynamicCommand);

            plugin.getLogger().info("Successfully registered dynamic command: /" + commandName);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register the dynamic command: " + commandName);
            e.printStackTrace();
        }
    }


}
