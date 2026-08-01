package org.sethomegui.Placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.sethomegui.SetHomeGUI;

public class GlobalPlaceholders extends PlaceholderExpansion {

    private final SetHomeGUI plugin;

    public GlobalPlaceholders(SetHomeGUI plugin) {
        this.plugin = plugin;
    }

    @Override
    @NotNull
    public String getAuthor() { return "Beeted_"; }

    @Override
    @NotNull
    public String getIdentifier() { return "sethomegui"; }

    @Override
    @NotNull
    // Se lee de plugin.yml para que no vuelva a quedarse desfasada al subir de version
    public String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        // Validación de seguridad por si el placeholder se consulta sin un jugador válido
        if (player == null) {
            return "0";
        }

        // 1. %sethomegui_homes% -> Cantidad de hogares actuales del usuario
        if (params.equalsIgnoreCase("homes")) {
            int currentHomes = plugin.getHomeManager().getHomeCount(player.getUniqueId());
            return String.valueOf(currentHomes);
        }

        // 2. %sethomegui_maxhomes% -> Límite máximo de hogares permitidos
        if (params.equalsIgnoreCase("maxhomes")) {
            // Si el jugador está online, calculamos sus permisos exactos
            if (player.isOnline()) {
                Player onlinePlayer = player.getPlayer();
                if (onlinePlayer != null) {
                    int maxHomes = plugin.getHomeManager().getPlayerMaxHomes(onlinePlayer);
                    // Si el límite es infinito (-1), devolvemos un texto personalizado o un símbolo
                    return maxHomes == -1 ? "∞" : String.valueOf(maxHomes);
                }
            }
            // Fallback por si el jugador está offline
            return "0";
        }

        return null; // Si piden un parámetro desconocido (ej: %sethomegui_test%) devuelve null
    }
}