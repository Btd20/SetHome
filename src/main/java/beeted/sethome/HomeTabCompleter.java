package beeted.sethome;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class HomeTabCompleter implements TabCompleter {

    private final SetHome plugin;

    public HomeTabCompleter(SetHome plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        FileConfiguration config = plugin.getConfig();

        // Obtén el comando configurado en "menu.open-command"
        String configuredCommand = config.getString("menu.open-command", "/home").replace("/", "");

        // Verifica si el comando registrado o el alias coincide con el comando configurado
        if (command.getName().equalsIgnoreCase("home") || alias.equalsIgnoreCase(configuredCommand)) {
            // Verifica si el remitente es un jugador
            if (sender instanceof Player) {
                Player player = (Player) sender;

                // Si hay al menos un argumento, proporciona sugerencias para el primer argumento
                if (args.length == 1) {
                    // Solo agrega la opción 'reload' si el jugador tiene el permiso adecuado
                    if (player.hasPermission("sethome.reload")) {
                        suggestions.add("reload");
                    }
                    // Aquí puedes añadir más sugerencias si es necesario
                }
            }
        }
        return suggestions;
    }
}
