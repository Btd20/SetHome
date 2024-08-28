package beeted.sethome;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class HomeCommandExecutor implements CommandExecutor {
    private final SetHome plugin;

    public HomeCommandExecutor(SetHome plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration config = plugin.getConfig();

        // Verifica si el comando es /home
        if (!label.equalsIgnoreCase("home")) {
            return false;
        }

        // Verifica si el comando es ejecutado por un jugador para otros comandos "/home"
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            // Verifica si el remitente tiene permisos para recargar el plugin
            if (sender.hasPermission("sethome.reload")) {
                plugin.reloadConfig();
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.plugin-reloaded", "&aPlugin reloaded successfully!")));
                return true;
            } else {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permissions", "&cYou don't have permission to reload the plugin!")));
                return true;
            }
        }

        Player player = (Player) sender;

        // Verifica si el jugador tiene permisos para usar cualquier comando de sethome
        if (!player.hasPermission("sethome.use")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permissions", "&cYou don't have permission to use this command!")));
            return true;
        }

        // Lógica adicional para otros subcomandos de "/home" aquí
        // Por ejemplo, mostrar un menú o listar hogares

        // Si no se ha manejado ningún caso específico, devuelve false para mostrar el uso del comando
        return false;
    }
}
