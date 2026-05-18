package org.sethomegui.Managers;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.sethomegui.SetHomeGUI;
import org.sethomegui.Utils.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportManager {

    private final SetHomeGUI plugin;
    private final Map<UUID, io.papermc.paper.threadedregions.scheduler.ScheduledTask> activeTasks = new HashMap<>();

    public TeleportManager(SetHomeGUI plugin) {
        this.plugin = plugin;
    }

    public void queueTeleport(Player player, Location targetLocation) {
        UUID uuid = player.getUniqueId();

        // Cancelamos si ya hay un teletransporte activo en cola para este jugador
        cancelActiveTeleport(uuid);

        // ==========================================================
        //       ⚡ COMPROBACIÓN DE BYPASS PARA EL COOLDOWN ⚡
        // ==========================================================
        if (player.hasPermission("sethome.cooldown.bypass")) {
            // Teletransporte asíncrono regional inmediato para Folia/Paper
            player.teleportAsync(targetLocation).thenAccept(success -> {
                if (success) {
                    YamlDocument actionsConfig = plugin.getActionsConfig();
                    executePostTeleportActions(player, actionsConfig.getSection("teleported-actions"));
                }
            });
            return; // Cortamos el flujo aquí para no activar el temporizador
        }
        // ==========================================================

        int cooldown = plugin.getMainConfig().getInt("teleport-cooldown", 5);
        boolean cancelOnMove = plugin.getMainConfig().getBoolean("cancel-on-move", true);
        Location startLoc = player.getLocation().clone();

        YamlDocument actionsConfig = plugin.getActionsConfig();
        Section teleportActions = actionsConfig.getSection("teleport-actions");

        // Ejecutar acciones iniciales de inmediato pasando el tiempo completo
        executePreTeleportActions(player, teleportActions, cooldown, true);

        final int[] secondsElapsed = {0};

        ScheduledTask task = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, (scheduledTask) -> {

            // Verificación de desconexión
            if (!player.isOnline()) {
                scheduledTask.cancel();
                activeTasks.remove(uuid);
                return;
            }

            // Comprobación de movimiento
            if (cancelOnMove) {
                Location current = player.getLocation();
                if (current.getBlockX() != startLoc.getBlockX() ||
                        current.getBlockY() != startLoc.getBlockY() ||
                        current.getBlockZ() != startLoc.getBlockZ()) {

                    scheduledTask.cancel();
                    activeTasks.remove(uuid);

                    // Leemos el mensaje desde la configuración usando el fallback nativo por seguridad
                    String moveCancelledMsg = plugin.getMainConfig().getString(
                            "messages.home-action-messages.teleport-cancelled-move",
                            "&#ef6603[SetHomeGUI] &cTeleportation cancelled because you moved!"
                    );

                    // Enviamos el mensaje aplicando la paleta de colores del plugin
                    player.sendMessage(Utils.color(moveCancelledMsg));
                    player.sendTitle("", "", 0, 0, 0);
                    return;
                }
            }

            // Incrementamos el contador manual de segundos
            secondsElapsed[0]++;
            int currentRemaining = cooldown - secondsElapsed[0];

            if (currentRemaining <= 0) {
                scheduledTask.cancel();
                activeTasks.remove(uuid);

                // Teletransporte asíncrono regional seguro
                player.teleportAsync(targetLocation).thenAccept(success -> {
                    if (success) {
                        executePostTeleportActions(player, actionsConfig.getSection("teleported-actions"));
                    }
                });
                return;
            }

            // Actualizamos la interfaz del jugador en cada segundo transcurrido
            executePreTeleportActions(player, teleportActions, currentRemaining, false);

        }, 1L, 1L, java.util.concurrent.TimeUnit.SECONDS);

        activeTasks.put(uuid, task);
    }

    private void executePreTeleportActions(Player player, Section section, int seconds, boolean isInitialRun) {
        if (section == null) return;

        // 1. REGION: TITLE
        Section titleSec = section.getSection("title");
        if (titleSec != null && titleSec.getBoolean("enabled", false)) {
            String type = titleSec.getString("type", "COOLDOWN");
            // Si es COOLDOWN se actualiza siempre. Si es STATIC se envía continuamente para que se mantenga en pantalla.
            if (type.equalsIgnoreCase("COOLDOWN") || type.equalsIgnoreCase("STATIC")) {
                String title = Utils.setPlaceholders(player, titleSec.getString("title", ""), plugin);
                String sub = Utils.setPlaceholders(player, titleSec.getString("subtitle", ""), plugin)
                        .replace("%seconds%", String.valueOf(seconds));

                // 20 Ticks de stay garantizan que permanezca el segundo completo en pantalla sin parpadeos
                player.sendTitle(Utils.color(title), Utils.color(sub), 0, 20, 5);
            }
        }

        // 2. REGION: ACTIONBAR
        Section actionbarSec = section.getSection("actionbar");
        if (actionbarSec != null && actionbarSec.getBoolean("enabled", false)) {
            String type = actionbarSec.getString("type", "COOLDOWN");
            // Si es COOLDOWN se actualiza siempre. Si es STATIC se envía continuamente para que no desaparezca a los 2 segundos.
            if (type.equalsIgnoreCase("COOLDOWN") || type.equalsIgnoreCase("STATIC")) {
                String msg = Utils.setPlaceholders(player, actionbarSec.getString("message", ""), plugin)
                        .replace("%seconds%", String.valueOf(seconds));
                player.sendActionBar(Utils.color(msg));
            }
        }

        // 3. REGION: CHAT MESSAGE
        Section msgSec = section.getSection("message");
        if (msgSec != null && msgSec.getBoolean("enabled", false)) {
            String type = msgSec.getString("type", "COOLDOWN");

            // Si es COOLDOWN se envía en cada segundo que pasa (5, 4, 3, 2, 1). Si es STATIC, solo en la ejecución inicial.
            if (type.equalsIgnoreCase("COOLDOWN") || (type.equalsIgnoreCase("STATIC") && isInitialRun)) {
                String msg = Utils.setPlaceholders(player, msgSec.getString("message", ""), plugin)
                        .replace("%seconds%", String.valueOf(seconds));
                player.sendMessage(Utils.color(msg));
            }
        }
    }

    private void executePostTeleportActions(Player player, Section section) {
        if (section == null) return;

        // 1. TITLE FINAL
        Section titleSec = section.getSection("title");
        if (titleSec != null && titleSec.getBoolean("enabled", false)) {
            String title = Utils.setPlaceholders(player, titleSec.getString("title", ""), plugin);
            String sub = Utils.setPlaceholders(player, titleSec.getString("subtitle", ""), plugin);
            int ticks = titleSec.getInt("duration", 2) * 20;
            player.sendTitle(Utils.color(title), Utils.color(sub), 10, ticks, 10);
        }

        // 2. ACTIONBAR FINAL
        Section actionbarSec = section.getSection("actionbar");
        if (actionbarSec != null && actionbarSec.getBoolean("enabled", false)) {
            String msg = Utils.setPlaceholders(player, actionbarSec.getString("message", ""), plugin);
            player.sendActionBar(Utils.color(msg));
        }

        // 3. MESSAGE FINAL
        Section msgSec = section.getSection("message");
        if (msgSec != null && msgSec.getBoolean("enabled", false)) {
            String msg = Utils.setPlaceholders(player, msgSec.getString("message", ""), plugin);
            player.sendMessage(Utils.color(msg));
        }
    }

    public void cancelActiveTeleport(UUID uuid) {
        if (activeTasks.containsKey(uuid)) {
            activeTasks.get(uuid).cancel();
            activeTasks.remove(uuid);
        }
    }
}