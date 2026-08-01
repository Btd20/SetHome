package org.sethomegui.Listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.sethomegui.SetHomeGUI;
import org.sethomegui.Utils.Utils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class ChatPromptListener implements Listener {

    private static SetHomeGUI plugin;
    // Usamos ConcurrentHashMap para total seguridad de hilos en Folia/Paper
    private static final Map<UUID, Long> awaitingHomeName = new ConcurrentHashMap<>();

    // Patrón optimizado para limpiar formatos Hexadecimales comunes (&#ffffff o #ffffff)
    private static final Pattern HEX_CLEAN_PATTERN = Pattern.compile("&#[A-Fa-f0-9]{6}|#[A-Fa-f0-9]{6}");

    public ChatPromptListener(SetHomeGUI plugin) {
        ChatPromptListener.plugin = plugin;
    }

    public static void startPrompt(Player player) {
        UUID uuid = player.getUniqueId();
        long startTime = System.currentTimeMillis();
        awaitingHomeName.put(uuid, startTime);

        // Obtener el tiempo de expiración desde la configuración (por defecto 60 segundos)
        long timeoutSeconds = plugin.getMainConfig().getLong("chat-prompt-timeout", 60L);

        // PROGRAMAR EL TIMEOUT AUTOMÁTICO EN EL ASYNC SCHEDULER (Compatible con Folia)
        Bukkit.getAsyncScheduler().runDelayed(plugin, (task) -> {
            // Verificamos si el jugador sigue esperando y si es el prompt correcto (mismo timestamp)
            if (awaitingHomeName.containsKey(uuid) && awaitingHomeName.get(uuid) == startTime) {
                awaitingHomeName.remove(uuid);

                // Evitamos errores si el jugador se desconectó durante la espera
                if (!player.isOnline()) return;

                String timeoutMsg = plugin.getMainConfig().getString(
                        "messages.home-creation-messages.creation-timeout",
                        "&#ef6603[SetHomeGUI] &cTime expired! Home creation process cancelled."
                );
                player.sendMessage(Utils.setPlaceholders(player, timeoutMsg, plugin));
            }
        }, timeoutSeconds, TimeUnit.SECONDS);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Si no está en el mapa, ignoramos el evento por completo
        if (!awaitingHomeName.containsKey(uuid)) return;

        event.setCancelled(true);

        // 🛠️ LIMPIEZA DE TEXTO CRUDO EXTREMA (Anti ChatColor2, códigos legados § y &)
        String rawMessage = event.getMessage().trim();
        rawMessage = HEX_CLEAN_PATTERN.matcher(rawMessage).replaceAll(""); // Remueve formatos Hex
        rawMessage = ChatColor.stripColor(rawMessage); // Remueve secciones legadas '§'
        rawMessage = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', rawMessage)); // Remueve secciones con '&'

        String message = rawMessage;
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

        // Se mantiene la ejecución correcta en la región de Folia del jugador
        Bukkit.getRegionScheduler().execute(plugin, player.getLocation(), () -> {
            plugin.getHomeManager().saveHome(uuid, homeName, player.getLocation());

            // Lógica de caché premium removida aquí para la versión normal
            String successMsg = plugin.getMainConfig().getString(basePath + "home-saved");
            successMsg = successMsg.replace("%name%", "&f" + homeName);
            player.sendMessage(Utils.setPlaceholders(player, successMsg, plugin));
        });
    }
}