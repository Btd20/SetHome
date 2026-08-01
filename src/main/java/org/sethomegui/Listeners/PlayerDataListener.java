package org.sethomegui.Listeners;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.sethomegui.SetHomeGUI;

import java.util.UUID;

/**
 * Mantiene la cache de datos alineada con quien esta conectado.
 *
 * Al entrar se precargan los datos en segundo plano (imprescindible con MySQL, para que
 * abrir un menu no tenga que esperar a la base de datos) y al salir se liberan de memoria,
 * de forma que si el jugador cambia de servidor en la red vuelva a leer datos frescos.
 */
public class PlayerDataListener implements Listener {

    private final SetHomeGUI plugin;

    public PlayerDataListener(SetHomeGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        if (!plugin.getStorageManager().isExternalBackend()) {
            // Con archivos locales la lectura es inmediata y no hace falta precargar nada.
            return;
        }

        Bukkit.getAsyncScheduler().runNow(plugin, (task) -> plugin.getStorageManager().preload(uuid));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getStorageManager().invalidate(event.getPlayer().getUniqueId());
    }
}
