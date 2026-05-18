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
            // Leemos el mensaje desde el config.yml con un fallback seguro en texto plano por si acaso
            String onlyPlayersMsg = plugin.getConfig().getString(
                    "messages.only-players",
                    "&#ef6603[SetHomeGUI] &cError: Only players can execute this command."
            );

            sender.sendMessage(Utils.color(onlyPlayersMsg));
            return true;
        }

        Player player = (Player) sender;

        // Si no introduce ningún argumento, le recordamos cómo usarlo
        if (args.length == 0) {
            Utils.sendUsage(player, "/delhome <home name>", plugin);
            return true;
        }

        // Unimos todos los argumentos por si el nombre contiene espacios
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            builder.append(args[i]);
            if (i < args.length - 1) builder.append(" ");
        }
        String homeName = builder.toString().trim();

        // ==========================================
        //         ⚠️ AJUSTE DE SEGURIDAD ⚠️
        // ==========================================
        // Limpia las comillas del principio y del final si el jugador envió el argumento envuelto en ellas
        if (homeName.startsWith("\"") && homeName.endsWith("\"") && homeName.length() > 1) {
            homeName = homeName.substring(1, homeName.length() - 1);
        }
        // ==========================================

        // Verificamos si el hogar realmente existe en su archivo de datos
        YamlDocument playerFile = plugin.getHomeManager().getPlayerFile(player.getUniqueId());
        List<String> rawHomesList = playerFile != null ? playerFile.getStringList("homes") : null;

        if (rawHomesList == null || !rawHomesList.contains(homeName)) {
            // Leemos el mensaje desde el config.yml con un fallback seguro por si no existe en el archivo
            String errorMsg = plugin.getConfig().getString(
                    "messages.home-action-messages.home-not-found",
                    "&#ef6603[SetHomeGUI] &cError: You do not have a home named &f%name%&c."
            );

            // Reemplazamos el marcador %name% por la variable homeName y aplicamos colores
            player.sendMessage(Utils.color(errorMsg.replace("%name%", homeName)));
            return true;
        }

        // Si la casa existe, le abrimos de forma elegante el menú visual de confirmación
        plugin.getGuiManager().openConfirmationGUI(player, homeName);
        plugin.getGuiManager().playConfiguredClickSound(player, "homes-gui");
        return true;
    }
}