package org.sethomegui.Commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import dev.dejvokep.boostedyaml.YamlDocument;
import org.sethomegui.SetHomeGUI;
import org.sethomegui.Utils.Utils;

import java.util.List;

public class MainCommands implements CommandExecutor {

    private final SetHomeGUI plugin;

    public MainCommands(SetHomeGUI plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        // 1. Check if the sender is a player
        if (!(sender instanceof Player)) {
            String onlyPlayersMsg = plugin.getMainConfig().getString(
                    "messages.only-players",
                    "&#ef6603[SetHomeGUI] &cError: Only players can execute this command."
            );
            sender.sendMessage(Utils.color(onlyPlayersMsg));
            return true;
        }

        Player player = (Player) sender;
        YamlDocument config = plugin.getMainConfig();

        // 2. Dynamic Permission Check based on config.yml
        boolean requiresPermission = config.getBoolean("requires-default-permission", false);

        if (requiresPermission) {
            String permissionNode = config.getString("default-permission", "sethome.use");

            if (!player.hasPermission(permissionNode)) {
                String noPermissionMsg = config.getString(
                        "messages.home-creation-messages.no-permission",
                        "&#ef6603[SetHomeGUI] &cYou do not have permission to use this command."
                );
                player.sendMessage(Utils.color(noPermissionMsg));
                return true;
            }
        }

        // 3. FLUJO A: Si no hay argumentos, abrimos el menú principal GUI como siempre
        if (args.length == 0) {
            plugin.getGuiManager().openMainGUI(player);
            return true;
        }

        // 4. FLUJO B: Si hay argumentos (ej: /home mi casa), unimos todo el texto para el nombre
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            builder.append(args[i]);
            if (i < args.length - 1) builder.append(" ");
        }
        String homeName = builder.toString().trim().replace("\"", "");

        // Obtenemos el archivo YAML de datos específico del jugador
        YamlDocument playerFile = plugin.getHomeManager().getPlayerFile(player.getUniqueId());
        List<String> rawHomesList = playerFile.getStringList("homes");

        // Verificamos si el hogar solicitado existe en sus registros
        if (rawHomesList == null || !rawHomesList.contains(homeName)) {
            String errorMsg = config.getString(
                    "messages.home-action-messages.home-not-found",
                    "&#ef6603[SetHomeGUI] &cError: You do not have a home named &f%name%&c."
            );
            player.sendMessage(Utils.color(errorMsg.replace("%name%", homeName)));
            return true;
        }

        // 5. EXTRACCIÓN DE LA LOCACIÓN (Misma lógica que tu menú GUI)
        String worldName = playerFile.getString(homeName + ".world");
        org.bukkit.World world = Bukkit.getWorld(worldName);

        if (world == null) {
            String worldNotLoadedMsg = config.getString(
                    "messages.home-action-messages.world-not-loaded",
                    "&#ef6603[SetHomeGUI] &cError: Destination world '%world%' is not loaded."
            );
            player.sendMessage(Utils.color(worldNotLoadedMsg.replace("%world%", worldName)));
            return true;
        }

        double x = playerFile.getDouble(homeName + ".x");
        double y = playerFile.getDouble(homeName + ".y");
        double z = playerFile.getDouble(homeName + ".z");
        float yaw = playerFile.getDouble(homeName + ".yaw").floatValue();
        float pitch = playerFile.getDouble(homeName + ".pitch").floatValue();

        Location targetLoc = new Location(world, x, y, z, yaw, pitch);

        // Disparamos tu lógica con barra de acción, títulos y compatibilidad con Folia/Paper
        plugin.getTeleportManager().queueTeleport(player, targetLoc);

        return true;
    }
}