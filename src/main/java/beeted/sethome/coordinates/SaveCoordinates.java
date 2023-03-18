package beeted.sethome.coordinates;

import beeted.sethome.SetHome;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class SaveCoordinates extends JavaPlugin implements Listener {
    private final SetHome plugin;
    public SaveCoordinates (SetHome plugin) {
        this.plugin = plugin;
    }
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Obtiene el jugador que ha ingresado
        Player player = event.getPlayer();

        // Crea un archivo YAML para almacenar las coordenadas del hogar del jugador
        File playerFile = new File(getDataFolder(), player.getUniqueId() + ".yml");
        YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
        try {
            // Crea el archivo si no existe
            if (!playerFile.exists()) {
                playerFile.createNewFile();

                // Establece el nombre del jugador en el archivo YAML
                playerConfig.set("nombre", player.getName());

                // Guarda los cambios en el archivo YAML
                playerConfig.save(playerFile);
            }
        } catch (IOException e) {
            // Maneja cualquier excepción
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        // Obtiene el jugador que ha ejecutado el comando
        Player player = event.getPlayer();

        // Verifica si el jugador ha ejecutado el comando "/sethome"
        if (event.getMessage().equalsIgnoreCase("/sethome")) {
            // Obtiene la ubicación actual del jugador
            String home = "X: " + player.getLocation().getBlockX() + " Y: " + player.getLocation().getBlockY() + " Z: " + player.getLocation().getBlockZ();

            // Actualiza el archivo YAML del jugador con las nuevas coordenadas del hogar
            File playerFile = new File(getDataFolder(), player.getUniqueId() + ".yml");
            YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
            playerConfig.set("hogar", home);
            try {
                playerConfig.save(playerFile);
            } catch (IOException e) {
                // Maneja cualquier excepción
                e.printStackTrace();
            }

            // Cancela la ejecución del comando para evitar que el servidor lo procese
            event.setCancelled(true);

            // Envía un mensaje al jugador confirmando que se ha establecido su hogar
            player.sendMessage("Tu hogar ha sido establecido.");
        }
    }
}
