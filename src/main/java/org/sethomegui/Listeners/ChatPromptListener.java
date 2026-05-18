package org.sethomegui.Listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.sethomegui.SetHomeGUI;
import org.sethomegui.Utils.Utils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ChatPromptListener implements Listener {

    private final SetHomeGUI plugin;
    private static final Set<UUID> awaitingHomeName = new HashSet<>();

    public ChatPromptListener(SetHomeGUI plugin) {
        this.plugin = plugin;
    }

    public static void startPrompt(Player player) {
        awaitingHomeName.add(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!awaitingHomeName.contains(uuid)) return;

        event.setCancelled(true);

        String message = event.getMessage().trim();
        String basePath = "messages.home-creation-messages.";
        String cancelWord = plugin.getMainConfig().getString(basePath + "cancel-word", "cancel");

        // 1. CONTROL DE CANCELACIÓN
        if (message.equalsIgnoreCase(cancelWord)) {
            awaitingHomeName.remove(uuid);
            String cancelMsg = plugin.getMainConfig().getString(basePath + "creation-cancelled");
            player.sendMessage(Utils.setPlaceholders(player, cancelMsg, plugin));
            return;
        }

        // 2. VALIDACIÓN MEDIANTE REGEX DINÁMICO (Configurable)
        String regex = plugin.getMainConfig().getString("name-regex", "^[A-Za-z0-9\\s]{1,32}$");
        if (!message.matches(regex) || message.isEmpty()) {
            String errorMsg = plugin.getMainConfig().getString(basePath + "invalid-name");
            errorMsg = errorMsg.replace("%cancel_word%", "&f" + cancelWord);
            player.sendMessage(Utils.setPlaceholders(player, errorMsg, plugin));
            return;
        }

        // VALIDACIÓN DE PALABRAS PROHIBIDAS (Lista Negra de Nombres)
        List<String> blacklistedWords = plugin.getMainConfig().getStringList("blacklisted-words");
        if (blacklistedWords != null) {
            for (String word : blacklistedWords) {
                if (message.toLowerCase().contains(word.toLowerCase())) {
                    String blacklistMsg = plugin.getMainConfig().getString(
                            basePath + "blacklisted-name",
                            "&#ef6603[SetHomeGUI] &cError: The home name contains a blacklisted word!"
                    );
                    player.sendMessage(Utils.setPlaceholders(player, blacklistMsg, plugin));
                    return;
                }
            }
        }

        // --- COMPROBACIÓN DE MUNDO EN LISTA NEGRA (ÚLTIMO SEGUNDO) ---
        String currentWorld = player.getWorld().getName();
        List<String> blacklistedWorlds = plugin.getMainConfig().getStringList("blacklisted-worlds");

        if (blacklistedWorlds != null && blacklistedWorlds.contains(currentWorld)) {
            String worldMsg = plugin.getMainConfig().getString(basePath + "world-blacklisted");
            worldMsg = worldMsg.replace("%world%", "&f" + currentWorld);
            player.sendMessage(Utils.setPlaceholders(player, worldMsg, plugin));
            awaitingHomeName.remove(uuid); // Limpiamos su estado de espera ya que el proceso falló
            return;
        }

        // 3. FILTRO DE LÍMITES
        int currentHomes = plugin.getHomeManager().getHomeCount(uuid);
        int maxHomes = plugin.getHomeManager().getPlayerMaxHomes(player);
        boolean homeExists = plugin.getHomeManager().getPlayerFile(uuid).getStringList("homes").contains(message);

        if (!homeExists && maxHomes != -1 && currentHomes >= maxHomes) {
            String limitMsg = plugin.getMainConfig().getString(basePath + "limit-reached");
            limitMsg = limitMsg.replace("%max%", "&f" + maxHomes);
            player.sendMessage(Utils.setPlaceholders(player, limitMsg, plugin));
            return;
        }

        // 4. GUARDADO EXITOSO
        awaitingHomeName.remove(uuid);
        String homeName = message;

        Bukkit.getRegionScheduler().execute(plugin, player.getLocation(), () -> {
            plugin.getHomeManager().saveHome(uuid, homeName, player.getLocation());

            String successMsg = plugin.getMainConfig().getString(basePath + "home-saved");
            successMsg = successMsg.replace("%name%", "&f" + homeName);
            player.sendMessage(Utils.setPlaceholders(player, successMsg, plugin));
        });
    }
}