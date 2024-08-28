package beeted.sethome;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class HomeTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        // Asegúrate de que el comando sea /home
        if (command.getName().equalsIgnoreCase("home")) {
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
