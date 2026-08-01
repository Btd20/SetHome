package org.sethomegui.Commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.sethomegui.SetHomeGUI;
import org.sethomegui.Importers.HomeImporter;
import org.sethomegui.Utils.Utils;

import java.util.List;

public class HomeAdminCommand implements CommandExecutor {

    private final SetHomeGUI plugin;
    private final HomeImporter importer;

    public HomeAdminCommand(SetHomeGUI plugin) {
        this.plugin = plugin;
        this.importer = new HomeImporter(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 1. Validación de Permisos Administrativos
        if (!sender.hasPermission("sethome.admin")) {
            String noPermissionMsg = plugin.getMainConfig().getString(
                    "messages.admin.no-permission",
                    "&#ef6603[SetHomeGUI] &cYou do not have permission to execute this command."
            );
            sender.sendMessage(Utils.color(noPermissionMsg));
            return true;
        }

        // 2. Validación de argumentos mínimos / Sintaxis base
        if (args.length == 0) {
            if (sender instanceof Player) {
                Utils.sendUsage((Player) sender, "homeadmin gui", plugin);
            } else {
                String cmdUsage = plugin.getMainConfig().getString(
                        "messages.admin.command-usage",
                        "&cUsage: /homeadmin <gui|view|import|reload|version>"
                );
                sender.sendMessage(Utils.color(cmdUsage));
            }
            return true;
        }

        // 3. SUBCOMANDO: VERSION
        if (args[0].equalsIgnoreCase("version")) {
            List<String> versionLines = plugin.getMainConfig().getStringList("messages.plugin-version");
            String currentVersion = plugin.getPluginMeta().getVersion();

            if (versionLines != null && !versionLines.isEmpty()) {
                for (String line : versionLines) {
                    sender.sendMessage(Utils.color(line.replace("%version%", currentVersion)));
                }
            } else {
                sender.sendMessage(Utils.color("&7Plugin: &#ef6603SetHomeGUI &7| Version: &#ef6603" + currentVersion));
            }
            return true;
        }

        // 4. SUBCOMANDO: RELOAD
        if (args[0].equalsIgnoreCase("reload")) {
            Bukkit.getAsyncScheduler().runNow(plugin, (task) -> {
                try {
                    plugin.getMainConfig().reload();
                    plugin.getGuisConfig().reload();

                    if (plugin.getActionsConfig() != null) {
                        plugin.getActionsConfig().reload();
                    }

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

        // 5. SUBCOMANDO: IMPORT
        if (args[0].equalsIgnoreCase("import")) {
            if (args.length < 2) {
                String importUsage = plugin.getMainConfig().getString(
                        "messages.admin.import-usage",
                        "&#ef6603[SetHomeGUI] &cUsage: /homeadmin import <Essentials|HuskHomes>"
                );
                sender.sendMessage(Utils.color(importUsage));
                return true;
            }

            String source = args[1];
            String startMsg = plugin.getMainConfig().getString(
                    "messages.admin.import-started",
                    "&#ef6603[SetHomeGUI] &#f9a805Starting data import process asynchronously..."
            );
            sender.sendMessage(Utils.color(startMsg));

            Bukkit.getAsyncScheduler().runNow(plugin, (task) -> {
                if (source.equalsIgnoreCase("Essentials")) {
                    importer.importHomesFromEssentialsForAllPlayers(sender);
                } else if (source.equalsIgnoreCase("HuskHomes")) {
                    importer.importHomesFromHuskHomesForAllPlayers(sender, "HuskHomes/HuskHomesData.db");
                } else {
                    sender.sendMessage(Utils.color("&cUnknown source. Please use 'Essentials' or 'HuskHomes'."));
                }
            });
            return true;
        }

        // 6. SUBCOMANDO: GUI
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

        // 7. SUBCOMANDO: VIEW <PLAYER>
        if (args[0].equalsIgnoreCase("view")) {
            if (!(sender instanceof Player)) {
                String onlyPlayersMsg = plugin.getMainConfig().getString(
                        "messages.only-players",
                        "&#ef6603[SetHomeGUI] &cError: Only players can execute this command."
                );
                sender.sendMessage(Utils.color(onlyPlayersMsg));
                return true;
            }

            if (args.length < 2) {
                String viewUsage = plugin.getMainConfig().getString(
                        "messages.admin.view-usage",
                        "&#ef6603[SetHomeGUI] &cUsage: /homeadmin view <player>"
                );
                sender.sendMessage(Utils.color(viewUsage));
                return true;
            }

            Player admin = (Player) sender;
            String targetName = args[1];

            @SuppressWarnings("deprecation")
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);

            if (targetPlayer == null || (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline())) {
                String unknownPlayerMsg = plugin.getMainConfig().getString(
                        "messages.admin.view-unknown-player",
                        "&#ef6603[SetHomeGUI] &cError: Player &f%player% &chas never played on this server."
                );
                admin.sendMessage(Utils.color(unknownPlayerMsg.replace("%player%", targetName)));
                return true;
            }

            admin.closeInventory();
            admin.getScheduler().run(plugin, (task) -> {
                plugin.getAdminGUIManager().openAdminPlayerHomesMenu(admin, targetPlayer.getUniqueId(), 1);
            }, null);
            return true;
        }

        return true;
    }
}