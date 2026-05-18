package org.sethomegui.Managers;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.sethomegui.SetHomeGUI;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HomeManager {

    private final SetHomeGUI plugin;
    private final File dataFolder;

    public HomeManager(SetHomeGUI plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    /**
     * Obtiene el archivo YamlDocument específico de un jugador (.yml)
     */
    public YamlDocument getPlayerFile(UUID uuid) {
        // Cambiado de ".yaml" a ".yml" para mantener la consistencia del plugin
        File playerFile = new File(dataFolder, uuid.toString() + ".yml");
        try {
            return YamlDocument.create(playerFile, GeneralSettings.DEFAULT);
        } catch (IOException e) {
            // Leemos la plantilla desde el config.yml usando tu lector de BoostedYAML
            String loadErrorMsg = plugin.getMainConfig().getString(
                    "messages.system-errors.load-error",
                    "Could not load or create data file for user: %uuid%"
            );

            // Reemplazamos el marcador %uuid% por la variable local y lo mandamos de forma segura al logger
            plugin.getLogger().severe(loadErrorMsg.replace("%uuid%", uuid.toString()));
            e.printStackTrace();
            return null;
        }
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