package org.sethomegui.Commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.sethomegui.SetHomeGUI;
import org.sethomegui.Importers.HomeImporter;
import org.sethomegui.Utils.Utils;

public class HomeAdminCommand implements CommandExecutor {

    private final SetHomeGUI plugin;
    private final HomeImporter importer;

    public HomeAdminCommand(SetHomeGUI plugin) {
        this.plugin = plugin;
        this.importer = new HomeImporter(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 1. Validación de Permisos Administrativos (Consola o Jugador)
        if (!sender.hasPermission("sethome.admin")) {
            String noPermissionMsg = plugin.getMainConfig().getString(
                    "messages.admin.no-permission",
                    "&#ef6603[SetHomeGUI] &cYou do not have permission to execute this command."
            );
            sender.sendMessage(Utils.color(noPermissionMsg));
            return true;
        }

        // 2. Validación de argumentos mínimos
        if (args.length == 0) {
            if (sender instanceof Player) {
                Utils.sendUsage((Player) sender, "homeadmin gui", plugin);
            } else {
                sender.sendMessage(Utils.color("&cUsage: /homeadmin <gui|import|reload>"));
            }
            return true;
        }

        // 3. SUBCOMANDO: RELOAD
        if (args[0].equalsIgnoreCase("reload")) {
            // Se ejecuta de forma asíncrona para leer de disco de manera segura
            Bukkit.getAsyncScheduler().runNow(plugin, (task) -> {
                try {
                    // LLamada a los métodos nativos de BoostedYaml alojados en tu clase principal
                    plugin.getMainConfig().reload();
                    plugin.getGuisConfig().reload();

                    // Si tienes un archivo independiente mapeado para las acciones:
                    if (plugin.getActionsConfig() != null) {
                        plugin.getActionsConfig().reload();
                    }

                    // Obtenemos el mensaje de éxito directamente desde el archivo recién actualizado
                    String reloadMsg = plugin.getMainConfig().getString(
                            "messages.admin.reload-success",
                            "&#ef6603[SetHomeGUI] &aAll configurations (config, actions, guis) successfully reloaded!"
                    );
                    sender.sendMessage(Utils.color(reloadMsg));

                } catch (Exception e) {
                    sender.sendMessage(Utils.color("&c&#ef6603[SetHomeGUI] &cAn error occurred while reloading data files. Check console."));
                    plugin.getLogger().severe("Could not reload configuration files: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            return true;
        }

        // 4. SUBCOMANDO: IMPORT
        if (args[0].equalsIgnoreCase("import")) {
            if (args.length < 2) {
                String importUsage = plugin.getMainConfig().getString("messages.admin.import-usage", "&#ef6603[SetHomeGUI] &cUsage: /homeadmin import <Essentials|HuskHomes>");
                sender.sendMessage(Utils.color(importUsage));
                return true;
            }

            String source = args[1];
            String startMsg = plugin.getMainConfig().getString("messages.admin.import-started", "&#ef6603[SetHomeGUI] &#f9a805Starting data import process asynchronously...");
            sender.sendMessage(Utils.color(startMsg));

            // Ejecución Asíncrona obligatoria para no congelar el servidor con lecturas de disco/SQL
            Bukkit.getAsyncScheduler().runNow(plugin, (task) -> {
                if (source.equalsIgnoreCase("Essentials")) {
                    importer.importHomesFromEssentialsForAllPlayers(sender);
                } else if (source.equalsIgnoreCase("HuskHomes")) {
                    // Ruta por defecto adaptada a la base de datos interna SQLite de HuskHomes
                    importer.importHomesFromHuskHomesForAllPlayers(sender, "HuskHomes/HuskHomesData.db");
                } else {
                    sender.sendMessage(Utils.color("&cUnknown source. Please use 'Essentials' or 'HuskHomes'."));
                }
            });
            return true;
        }

        // 5. SUBCOMANDO: GUI
        if (args[0].equalsIgnoreCase("gui")) {
            if (!(sender instanceof Player)) {
                String onlyPlayersMsg = plugin.getMainConfig().getString(
                        "messages.only-players",
                        "&#ef6603[SetHomeGUI] &cError: Only players can execute this command."
                );
                sender.sendMessage(Utils.color(onlyPlayersMsg));
                return true;
            }

            Player player = (Player) sender;
            player.closeInventory();
            plugin.getAdminGUIManager().setPage(player.getUniqueId(), 1);

            player.getScheduler().run(plugin, (t) -> {
                plugin.getAdminGUIManager().openAdminMenu(player);
            }, null);
            return true;
        }

        return true;
    }
}