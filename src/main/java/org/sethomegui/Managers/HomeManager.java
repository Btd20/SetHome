package org.sethomegui.Managers;

import dev.dejvokep.boostedyaml.YamlDocument;
import org.bukkit.Location;
import org.sethomegui.SetHomeGUI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HomeManager {

    private final SetHomeGUI plugin;

    public HomeManager(SetHomeGUI plugin) {
        this.plugin = plugin;
    }

    /**
     * Obtiene el documento de datos de un jugador desde el backend activo
     * (archivos YAML por defecto, o MySQL si está configurado en config.yml).
     *
     * El documento devuelto se comporta como cualquier YamlDocument: al llamar a save()
     * los datos se persisten solos en el backend que corresponda.
     */
    public YamlDocument getPlayerFile(UUID uuid) {
        return plugin.getStorageManager().get(uuid);
    }

    /**
     * Elimina por completo los datos de un jugador del backend activo.
     */
    public void deletePlayerData(UUID uuid) {
        plugin.getStorageManager().delete(uuid);
    }

    /**
     * Guarda un nuevo hogar respetando la estructura exacta solicitada
     */
    public void saveHome(UUID uuid, String homeName, Location loc) {
        YamlDocument config = getPlayerFile(uuid);
        if (config == null) return;

        // 1. Obtener o crear la lista de indexación "homes"
        List<String> homeList = config.getStringList("homes");
        if (homeList == null) {
            homeList = new ArrayList<>();
        }

        // Evitar duplicados en el índice si sobreescribe el nombre
        if (!homeList.contains(homeName)) {
            homeList.add(homeName);
        }
        config.set("homes", homeList);

        // 2. Almacenar los datos posicionales bajo la clave del nombre del hogar
        config.set(homeName + ".world", loc.getWorld().getName());
        config.set(homeName + ".x", loc.getX());
        config.set(homeName + ".y", loc.getY());
        config.set(homeName + ".z", loc.getZ());
        config.set(homeName + ".yaw", (double) loc.getYaw());
        config.set(homeName + ".pitch", (double) loc.getPitch());

        try {
            config.save();
        } catch (IOException e) {
            // Leemos la plantilla desde el config.yml usando tu lector de BoostedYAML
            String saveErrorMsg = plugin.getMainConfig().getString(
                    "messages.system-errors.save-error",
                    "Could not save home data for UUID %uuid%"
            );

            // Reemplazamos el marcador %uuid% por la variable local y lo mandamos al logger
            plugin.getLogger().severe(saveErrorMsg.replace("%uuid%", uuid.toString()));
            e.printStackTrace();
        }
    }

    /**
     * Calcula el límite máximo de hogares de un jugador basado en sus permisos y la config general.
     * Si devuelve -1, significa que el jugador tiene hogares ilimitados.
     */
    public int getPlayerMaxHomes(org.bukkit.entity.Player player) {
        int maxFromConfig = plugin.getMainConfig().getInt("default-max-homes", 3);
        int maxFromPermission = -2; // Valor centinela para saber si encontramos un permiso

        // Escaneamos los permisos efectivos del jugador (funciona perfectamente con LuckPerms)
        for (org.bukkit.permissions.PermissionAttachmentInfo attachment : player.getEffectivePermissions()) {
            String permission = attachment.getPermission().toLowerCase();

            if (permission.startsWith("sethome.maxhomes.")) {
                try {
                    String numberStr = permission.substring("sethome.maxhomes.".length());
                    int val = Integer.parseInt(numberStr);

                    // Si el jugador pertenece a varios grupos con límites distintos,
                    // conservamos el valor más alto otorgado.
                    if (val > maxFromPermission) {
                        maxFromPermission = val;
                    }
                } catch (NumberFormatException ignored) {
                    // Ignorar si el permiso no termina en un número válido
                }
            }
        }

        // Si se encontró un permiso sethome.maxhomes.x, este tiene prioridad absoluta
        if (maxFromPermission != -2) {
            return maxFromPermission;
        }

        // Si no hay permisos y la config base es -1, es ilimitado
        return maxFromConfig;
    }

    /**
     * Obtiene la cantidad actual de hogares de un jugador
     */
    public int getHomeCount(UUID uuid) {
        YamlDocument config = getPlayerFile(uuid);
        if (config == null) return 0;
        List<String> homeList = config.getStringList("homes");
        return homeList != null ? homeList.size() : 0;
    }
}