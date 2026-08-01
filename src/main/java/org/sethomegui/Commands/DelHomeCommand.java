package org.sethomegui.Commands;

import dev.dejvokep.boostedyaml.YamlDocument;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.sethomegui.SetHomeGUI;
import org.sethomegui.Utils.Utils;

import java.util.List;

public class DelHomeCommand implements CommandExecutor {

    private final SetHomeGUI plugin;

    public DelHomeCommand(SetHomeGUI plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            String onlyPlayersMsg = plugin.getConfig().getString(
                    "messages.only-players",
                    "&#ef6603[SetHomeGUI] &cError: Only players can execute this command."
            );
            sender.sendMessage(Utils.color(onlyPlayersMsg));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            Utils.sendUsage(player, "/delhome <home name>", plugin);
            return true;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            builder.append(args[i]);
            if (i < args.length - 1) builder.append(" ");
        }
        String homeName = builder.toString().trim();

        if (homeName.startsWith("\"") && homeName.endsWith("\"") && homeName.length() > 1) {
            homeName = homeName.substring(1, homeName.length() - 1);
        }

        YamlDocument playerFile = plugin.getHomeManager().getPlayerFile(player.getUniqueId());
        List<String> rawHomesList = playerFile != null ? playerFile.getStringList("homes") : null;

        if (rawHomesList == null || !rawHomesList.contains(homeName)) {
            String errorMsg = plugin.getConfig().getString(
                    "messages.home-action-messages.home-not-found",
                    "&#ef6603[SetHomeGUI] &cError: You do not have a home named &f%name%&c."
            );
            player.sendMessage(Utils.color(errorMsg.replace("%name%", homeName)));
            return true;
        }

        // Abre la confirmación. El borrado físico y de caché se hace en el clic de este menú
        plugin.getGuiManager().openConfirmationGUI(player, homeName);
        plugin.getGuiManager().playConfiguredClickSound(player, "homes-gui");
        return true;
    }
}