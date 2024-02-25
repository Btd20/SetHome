package beeted.sethome;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public class HomeCommandExecutor implements CommandExecutor {
    private final SetHome plugin;

    public HomeCommandExecutor(SetHome plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration config = plugin.getConfig();
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            // Verifica si el jugador tiene permisos para recargar el plugin
            if (sender.hasPermission("sethome.reload")) {
                // Recarga el plugin
                plugin.reloadConfig();
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.plugin-reloaded")));
                return true;
            } else {
                sender.sendMessage("You don't have permission to reload the plugin!");
                return true;
            }
        }
        // Puedes manejar otros casos del comando "/home" aquí si es necesario
        return false;
    }
}

