package org.sethomegui.Commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.sethomegui.SetHomeGUI;
import org.sethomegui.Listeners.ChatPromptListener;
import org.sethomegui.Utils.Utils;

import java.util.List;

public class SetHomeCommand implements CommandExecutor {

    private final SetHomeGUI plugin;

    public SetHomeCommand(SetHomeGUI plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 1. COMPROBACIÓN DE INSTANCIA (CONSOLA)
        if (!(sender instanceof Player)) {
            String onlyPlayersMsg = plugin.getMainConfig().getString(
                    "messages.only-players",
                    "&#ef6603[SetHomeGUI] &cError: Only players can execute this command."
            );
            sender.sendMessage(Utils.color(onlyPlayersMsg));
            return true;
        }

        Player player = (Player) sender;
        String basePath = "messages.home-creation-messages.";
        dev.dejvokep.boostedyaml.YamlDocument config = plugin.getMainConfig();

        // --- 2. COMPROBACIÓN DE MUNDO EN LISTA NEGRA ---
        String currentWorld = player.getWorld().getName();
        List<String> blacklistedWorlds = config.getStringList("blacklisted-worlds");

        if (blacklistedWorlds != null && blacklistedWorlds.contains(currentWorld)) {
            String worldMsg = config.getString(basePath + "world-blacklisted",
                    "&#ef6603[SetHomeGUI] &cYou cannot set a home in the world &f%world%&#ef6603!");

            worldMsg = worldMsg.replace("%world%", currentWorld);
            player.sendMessage(Utils.setPlaceholders(player, worldMsg, plugin));
            return true;
        }

        // --- 3. COMPROBACIÓN DE LÍMITE DE HOGARES ---
        int currentHomes = plugin.getHomeManager().getHomeCount(player.getUniqueId());
        int maxHomes = plugin.getHomeManager().getPlayerMaxHomes(player);

        // REGLA A: FLUJO DEL PROMPT DE CHAT (Si no introduce argumentos, ej: /sethome)
        if (args.length == 0) {
            if (maxHomes != -1 && currentHomes >= maxHomes) {
                String limitMsg = config.getString(basePath + "limit-reached",
                        "&#ef6603[SetHomeGUI] &cYou have reached your maximum limit of &f%max% &chomes!");
                limitMsg = limitMsg.replace("%max%", String.valueOf(maxHomes));
                player.sendMessage(Utils.setPlaceholders(player, limitMsg, plugin));
                return true;
            }

            String cancelWord = config.getString(basePath + "cancel-word", "cancel");
            String promptMsg = config.getString(basePath + "name-prompt",
                    "&#ef6603[SetHomeGUI] &#f9a805Please type the name for your home in the chat... Or type &f%cancel_word% &#f9a805to exit.");
            promptMsg = promptMsg.replace("%cancel_word%", cancelWord);

            player.sendMessage(Utils.setPlaceholders(player, promptMsg, plugin));
            ChatPromptListener.startPrompt(player);
            return true;
        }

        // REGLA B: CREACIÓN DIRECTA POR COMANDO (Si introduce argumentos, ej: /sethome mi casa)
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            builder.append(args[i]);
            if (i < args.length - 1) builder.append(" ");
        }
        String homeName = builder.toString().trim();

        // VALIDACIÓN DE LISTA NEGRA
        List<String> blacklist = plugin.getMainConfig().getStringList("blacklisted-words");
        if (blacklist != null) {
            for (String word : blacklist) {
                if (homeName.toLowerCase().contains(word.toLowerCase())) {
                    String blacklistMsg = plugin.getMainConfig().getString(
                            "messages.home-creation-messages.blacklisted-name",
                            "&#ef6603[SetHomeGUI] &cError: The home name contains a blacklisted word!"
                    );
                    player.sendMessage(Utils.color(blacklistMsg));
                    return true; // Cancelamos la ejecución del comando
                }
            }
        }

        // VALIDACIÓN DE REGEX
        String regex = config.getString("name-regex", "^[A-Za-z0-9\\s]{1,32}$");
        if (!homeName.matches(regex) || homeName.isEmpty()) {
            String errorMsg = config.getString(basePath + "invalid-name",
                    "&#ef6603[SetHomeGUI] &cInvalid name! &#f9a805Please avoid using spaces, dots, or slashes. Try again or type &f%cancel_word% &#f9a805to exit.");
            String cancelWord = config.getString(basePath + "cancel-word", "cancel");
            errorMsg = errorMsg.replace("%cancel_word%", cancelWord);
            player.sendMessage(Utils.setPlaceholders(player, errorMsg, plugin));
            return true;
        }

        // VALIDACIÓN DE LÍMITE DE ÚLTIMO SEGUNDO (Ignorado si se está sobreescribiendo uno existente)
        boolean homeExists = plugin.getHomeManager().getPlayerFile(player.getUniqueId()).getStringList("homes").contains(homeName);
        if (!homeExists && maxHomes != -1 && currentHomes >= maxHomes) {
            String limitMsg = config.getString(basePath + "limit-reached",
                    "&#ef6603[SetHomeGUI] &cYou have reached your maximum limit of &f%max% &chomes!");
            limitMsg = limitMsg.replace("%max%", String.valueOf(maxHomes));
            player.sendMessage(Utils.setPlaceholders(player, limitMsg, plugin));
            return true;
        }

        // GUARDADO FÍSICO SEGURO (Sincronizado con Folia/Paper)
        Bukkit.getRegionScheduler().execute(plugin, player.getLocation(), () -> {
            plugin.getHomeManager().saveHome(player.getUniqueId(), homeName, player.getLocation());

            String successMsg = config.getString(basePath + "home-saved",
                    "&#ef6603[SetHomeGUI] &#f9a805Home &f%name% &#f9a805has been successfully saved!");
            successMsg = successMsg.replace("%name%", homeName);
            player.sendMessage(Utils.setPlaceholders(player, successMsg, plugin));
        });

        return true;
    }
}