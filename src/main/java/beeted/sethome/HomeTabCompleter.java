package beeted.sethome;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HomeTabCompleter implements TabCompleter {

    private final SetHome plugin;

    public HomeTabCompleter(SetHome plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();

        Player player = (Player) sender;
        List<String> suggestions = new ArrayList<>();
        List<String> completions = new ArrayList<>();

        // ==========================================
        // LÓGICA PARA /HOME
        // ==========================================
        if (command.getName().equalsIgnoreCase("home")) {
            if (args.length == 1) {
                // Sugerir los nombres de los hogares del jugador
                suggestions.addAll(plugin.getHomesFor(player));
            }
        }

        // ==========================================
        // LÓGICA PARA /HOMEGUI
        // ==========================================
        else if (command.getName().equalsIgnoreCase("homegui")) {

            // ARGUMENTO 1: /homegui <subcomando>
            if (args.length == 1) {
                if (player.hasPermission("sethome.use")) {
                    suggestions.add("create");
                    suggestions.add("delete");
                }
                if (player.hasPermission("sethome.reload")) {
                    suggestions.add("reload");
                }
                if (player.hasPermission("sethome.import")) { // Permiso base para import
                    suggestions.add("import");
                }
                if (player.hasPermission("sethome.admin")) {
                    suggestions.add("admin");
                }
            }

            // ARGUMENTO 2: Depende del subcomando
            else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("import") && player.hasPermission("sethome.import")) {
                    if (player.hasPermission("sethome.import.essentials")) suggestions.add("Essentials");
                    if (player.hasPermission("sethome.import.huskhomes")) suggestions.add("HuskHomes");
                }

                if (args[0].equalsIgnoreCase("delete") && player.hasPermission("sethome.use")) {
                    suggestions.addAll(plugin.getHomesFor(player));
                }

                if (args[0].equalsIgnoreCase("create") && player.hasPermission("sethome.use")) {
                    suggestions.add("<nombre_del_home>");
                }

                if (args[0].equalsIgnoreCase("admin") && player.hasPermission("sethome.admin")) {
                    suggestions.add("create");
                    suggestions.add("delete");
                    suggestions.add("seeplayer");
                }
            }

            // ARGUMENTO 3: Lógica de Admin
            else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && player.hasPermission("sethome.admin")) {
                // Sugerir jugadores para create, delete y seeplayer
                for (Player online : Bukkit.getOnlinePlayers()) {
                    suggestions.add(online.getName());
                }
            }

            // ARGUMENTO 4: Lógica de Admin (Hogares del objetivo o nombre)
            else if (args.length == 4 && args[0].equalsIgnoreCase("admin") && player.hasPermission("sethome.admin")) {
                if (args[1].equalsIgnoreCase("delete")) {
                    // Intentamos obtener los hogares del jugador escrito en args[2]
                    Player target = Bukkit.getPlayer(args[2]);
                    if (target != null) {
                        suggestions.addAll(plugin.getHomesFor(target));
                    }
                } else if (args[1].equalsIgnoreCase("create")) {
                    suggestions.add("<nombre_del_home>");
                }
            }

            // COORDENADAS (Admin create)
            else if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("create") && player.hasPermission("sethome.admin")) {
                if (args.length == 5) suggestions.add("<x>");
                if (args.length == 6) suggestions.add("<y>");
                if (args.length == 7) suggestions.add("<z>");
            }
        }

        // Filtrar sugerencias según lo que el usuario ya empezó a escribir
        StringUtil.copyPartialMatches(args[args.length - 1], suggestions, completions);
        Collections.sort(completions);

        return completions;
    }
}