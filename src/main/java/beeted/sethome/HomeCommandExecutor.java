package beeted.sethome;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

public class HomeCommandExecutor implements CommandExecutor {
    private final SetHome plugin;
    private final HomeImporter homeImporter;
    private final Menu menu;


    public HomeCommandExecutor(SetHome plugin) {
        this.plugin = plugin;
        this.homeImporter = new HomeImporter(plugin);
        this.menu = new Menu(plugin);

    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration config = plugin.getConfig();
        String userCommand = config.getString("menu.open-command").replace("/", "");

        // Verifica si el comando es exactamente el comando configurado (por ejemplo /homegui)
        if (command.getName().equalsIgnoreCase(userCommand)) {

            // Si no hay argumentos, abrir el menú
            if (args.length == 0) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (!player.hasPermission("sethome.use")) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permissions", "&cYou don't have permission to do that.")));
                        return true;
                    }
                    // Abre el menú principal
                    menu.openMainMenu(player);
                    return true; // Detiene la ejecución aquí para evitar que siga ejecutando otros comandos
                }
            }

            // Comando /homegui admin create <jugador> <nombre_del_hogar> <x> <y> <z>
            if (args.length == 7 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("create")) {
                if (!sender.hasPermission("sethome.admin")) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permissions")));
                    return true;
                }

                // Obtener el jugador objetivo
                OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(args[2]);
                if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.player-not-found")));
                    return true;
                }

                // Obtener el nombre del hogar
                String homeName = args[3];

                // Verificar si las coordenadas están correctamente proporcionadas
                double x, y, z;
                try {
                    x = Double.parseDouble(args[4]);
                    y = Double.parseDouble(args[5]);
                    z = Double.parseDouble(args[6]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.invalid-coordinates")));
                    return true;
                }

                // Ruta de almacenamiento de los datos del jugador
                File dataFolder = new File(plugin.getDataFolder(), "data");
                File playerFile = new File(dataFolder, targetPlayer.getUniqueId() + ".yml");
                YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

                // Verificar si el hogar ya existe
                if (playerConfig.contains(homeName)) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-exists")));
                    return true;
                }

                List<String> homes = playerConfig.getStringList("homes");

                // Crear la ubicación del hogar con las coordenadas proporcionadas
                String worldName = (targetPlayer.getPlayer() != null) ? targetPlayer.getPlayer().getWorld().getName() : Bukkit.getWorlds().get(0).getName();
                Location homeLocation = new Location(Bukkit.getWorld(worldName), x, y, z);

                // Agregar el nuevo hogar al archivo del jugador
                homes.add(homeName);
                playerConfig.set("homes", homes);
                playerConfig.set(homeName + ".world", worldName);
                playerConfig.set(homeName + ".x", x);
                playerConfig.set(homeName + ".y", y);
                playerConfig.set(homeName + ".z", z);
                playerConfig.set(homeName + ".yaw", homeLocation.getYaw());
                playerConfig.set(homeName + ".pitch", homeLocation.getPitch());

                // Guardar el archivo de configuración
                try {
                    playerConfig.save(playerFile);
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-established-to-other")).replace("%player%", targetPlayer.getName()));
                } catch (IOException e) {
                    e.printStackTrace();
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.saving-error")));
                }

                return true;
            }

            // Comando /homegui admin create <jugador> <nombre_del_hogar>
            if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("create")) {
                if (!sender.hasPermission("sethome.admin")) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permissions")));
                    return true;
                }

                // Verificar que el ejecutante sea un jugador
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.player-only")));
                    return true;
                }

                Player executor = (Player) sender;

                // Obtener el jugador objetivo
                OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(args[2]);
                if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.player-not-found")));
                    return true;
                }

                // Obtener el nombre del hogar
                String homeName = args[3];

                // Ruta de almacenamiento de los datos del jugador
                File dataFolder = new File(plugin.getDataFolder(), "data");
                File playerFile = new File(dataFolder, targetPlayer.getUniqueId() + ".yml");
                YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

                // Verificar si el hogar ya existe
                if (playerConfig.contains(homeName)) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-exists")));
                    return true;
                }

                // Obtener la ubicación del jugador que ejecuta el comando
                Location homeLocation = executor.getLocation();

                // Obtener la lista de hogares
                List<String> homes = playerConfig.getStringList("homes");

                // Agregar el nuevo hogar al archivo del jugador
                homes.add(homeName);
                playerConfig.set("homes", homes);
                playerConfig.set(homeName + ".world", homeLocation.getWorld().getName());
                playerConfig.set(homeName + ".x", homeLocation.getX());
                playerConfig.set(homeName + ".y", homeLocation.getY());
                playerConfig.set(homeName + ".z", homeLocation.getZ());
                playerConfig.set(homeName + ".yaw", homeLocation.getYaw());
                playerConfig.set(homeName + ".pitch", homeLocation.getPitch());

                // Guardar el archivo de configuración
                try {
                    playerConfig.save(playerFile);
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-established-to-other")).replace("%player%", targetPlayer.getName()));
                } catch (IOException e) {
                    e.printStackTrace();
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.saving-error")));
                }

                return true;
            }

            // Comando /homegui admin delete <jugador> <nombre_del_hogar>
            if (args.length == 4 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("delete")) {
                if (!sender.hasPermission("sethome.admin")) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permissions")));
                    return true;
                }

                // Obtener el jugador objetivo
                OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(args[2]);
                if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.player-not-found")));
                    return true;
                }

                // Obtener el nombre del hogar
                String homeName = args[3];

                // Ruta de almacenamiento de los datos del jugador
                File dataFolder = new File(plugin.getDataFolder(), "data");
                File playerFile = new File(dataFolder, targetPlayer.getUniqueId() + ".yml");
                YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

                // Verificar si el hogar existe
                if (!playerConfig.contains(homeName)) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-not-found")));
                    return true;
                }

                // Obtener la lista de hogares
                List<String> homes = playerConfig.getStringList("homes");

                // Eliminar el hogar de la lista
                homes.remove(homeName);
                playerConfig.set("homes", homes);
                playerConfig.set(homeName, null); // Eliminar la configuración del hogar

                // Guardar el archivo de configuración
                try {
                    playerConfig.save(playerFile);
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-removed-to-other")).replace("%player%", targetPlayer.getName()).replace("%home%", homeName));
                } catch (IOException e) {
                    e.printStackTrace();
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.saving-error")));
                }

                return true;
            }

            // /homegui admin seeplayer <jugador>
            if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("seeplayer")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.player-only")));
                    return true;
                }

                Player admin = (Player) sender;

                if (!admin.hasPermission("sethome.admin")) {
                    admin.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permissions")));
                    return true;
                }

                OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
                if (target == null || !target.hasPlayedBefore()) {
                    admin.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.player-not-found")));
                    return true;
                }

                // Abre directamente los homes del jugador objetivo
                menu.openPlayerHomesInventory(admin, target, 0);
                return true;
            }


            // Comando /homegui create <nombre_del_hogar>
            if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
                if (!sender.hasPermission("sethome.use")) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permissions")));
                    return true;
                }

                // Verificar que el ejecutante sea un jugador
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "You must be a player to use this command.");
                    return true;
                }
                Player player = (Player) sender;

                // Obtener el nombre del hogar
                String homeName = args[1];

                // Ruta de almacenamiento de los datos del jugador
                File dataFolder = new File(plugin.getDataFolder(), "data");
                if (!dataFolder.exists()) dataFolder.mkdirs();

                File playerFile = new File(dataFolder, player.getUniqueId() + ".yml");
                YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

                // Verificar si el hogar ya existe
                if (playerConfig.contains(homeName)) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-exists")));
                    return true;
                }

                List<String> homes = playerConfig.getStringList("homes");

                // ✅ Verificar límite de homes
                int maxHomes = getMaxHomesForPlayer(player);
                if (homes.size() >= maxHomes) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            config.getString("messages.home-limit-reached").replace("%limit%", String.valueOf(maxHomes))));
                    return true;
                }

                // Crear la ubicación del hogar usando la ubicación actual del jugador
                Location homeLocation = player.getLocation();
                String worldName = homeLocation.getWorld().getName();

                // Agregar el nuevo hogar al archivo del jugador
                homes.add(homeName);
                playerConfig.set("homes", homes);
                playerConfig.set(homeName + ".world", worldName);
                playerConfig.set(homeName + ".x", homeLocation.getX());
                playerConfig.set(homeName + ".y", homeLocation.getY());
                playerConfig.set(homeName + ".z", homeLocation.getZ());
                playerConfig.set(homeName + ".yaw", homeLocation.getYaw());
                playerConfig.set(homeName + ".pitch", homeLocation.getPitch());

                // Guardar el archivo de configuración
                try {
                    playerConfig.save(playerFile);
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            config.getString("messages.home-established")).replace("%home%", homeName));
                } catch (IOException e) {
                    e.printStackTrace();
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.saving-error")));
                }

                return true;
            }

            // Comando /homegui delete <nombre_del_hogar>
            if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
                if (!sender.hasPermission("sethome.use")) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permissions")));
                    return true;
                }

                // Verificar que el ejecutante sea un jugador
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.player-only")));
                    return true;
                }
                Player player = (Player) sender;

                // Obtener el nombre del hogar
                String homeName = args[1];

                // Ruta de almacenamiento de los datos del jugador
                File dataFolder = new File(plugin.getDataFolder(), "data");
                File playerFile = new File(dataFolder, player.getUniqueId() + ".yml");

                // Verificar si el archivo del jugador existe
                if (!playerFile.exists()) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.player-not-found")));
                    return true;
                }

                YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

                // Verificar si el hogar existe en la configuración del jugador
                if (!playerConfig.contains(homeName)) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-not-found")));
                    return true;
                }

                // Obtener la lista de hogares
                List<String> homes = playerConfig.getStringList("homes");

                // Verificar si el hogar está en la lista antes de intentar eliminarlo
                if (!homes.contains(homeName)) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-not-found")));
                    return true;
                }

                // Eliminar el hogar de la lista
                homes.remove(homeName);
                playerConfig.set("homes", homes);
                playerConfig.set(homeName, null); // Eliminar la configuración del hogar

                // Guardar el archivo de configuración
                try {
                    playerConfig.save(playerFile);
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-removed")).replace("%home%", homeName));
                } catch (IOException e) {
                    e.printStackTrace();
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.saving-error")));
                }

                return true;
            }

            // Si hay un subcomando 'reload'
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("sethome.reload")) {

                    // ✅ Recargar la configuración
                    plugin.reloadConfig();
                    plugin.saveDefaultConfig();

                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfig().getString("messages.plugin-reloaded", "&aPlugin reloaded successfully.")));
                } else {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfig().getString("messages.no-permissions", "&cYou don't have permission to do that.")));
                }
                return true;
            }

            // Comando /home import Essentials
            if (args.length > 1 && args[0].equalsIgnoreCase("import") && args[1].equalsIgnoreCase("Essentials")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (!player.hasPermission("sethome.import.essentials")) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.import-no-permission")));
                        return true;
                    }
                    homeImporter.importHomesFromEssentialsForAllPlayers(player);
                } else if (sender instanceof ConsoleCommandSender) {
                    homeImporter.importHomesFromEssentialsForAllPlayers(sender);
                } else {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.console-or-player")));
                }
                return true;
            }

            // Comando /home import HuskHomes
            if (args.length > 1 && args[0].equalsIgnoreCase("import") && args[1].equalsIgnoreCase("HuskHomes")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (!player.hasPermission("sethome.import.huskhomes")) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.import-no-permission")));
                        return true;
                    }
                    homeImporter.importHomesFromHuskHomesForAllPlayers(player, "HuskHomes/HuskHomesData.db");
                } else if (sender instanceof ConsoleCommandSender) {
                    homeImporter.importHomesFromHuskHomesForAllPlayers(sender, "HuskHomes/HuskHomesData.db");
                } else {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.console-or-player")));
                }
                return true;
            }

            // Si no se cumple ninguna de las condiciones anteriores, devuelve falso
            return false;
        }

        // Si el comando no coincide con /homegui, devuelve falso
        return false;
    }


    /*public static void registerDynamicCommand(SetHome plugin, String commandName) {
        try {
            // Obtener el CommandMap
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            // Crear el comando dinámico
            HomeDynamicCommand dynamicCommand = new HomeDynamicCommand(commandName, plugin);

            // Configurar propiedades del comando
            dynamicCommand.setDescription("Open the home menu.");
            dynamicCommand.setUsage("/" + commandName);
            dynamicCommand.setPermission("sethome.use");

            // Registrar el comando dinámico
            commandMap.register(plugin.getDescription().getName(), dynamicCommand);

            plugin.getLogger().info("Successfully registered dynamic command: /" + commandName);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register the dynamic command: " + commandName);
            e.printStackTrace();
        }
    }*/

    private int getMaxHomesForPlayer(Player player) {
        FileConfiguration config = plugin.getConfig();
        int defaultMaxHomes = config.getInt("default-maxhomes", 3); // 👈 asegúrate de que coincide con tu config.yml

        int maxHomes = -1;

        // 🔎 Buscar permisos del estilo sethome.maxhomes.X
        for (PermissionAttachmentInfo permInfo : player.getEffectivePermissions()) {
            String perm = permInfo.getPermission().toLowerCase();
            if (perm.startsWith("sethome.maxhomes.")) {
                try {
                    int value = Integer.parseInt(perm.replace("sethome.maxhomes.", ""));
                    if (value > maxHomes) {
                        maxHomes = value;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // Si no tiene ningún permiso, usar el default del config
        return maxHomes > -1 ? maxHomes : defaultMaxHomes;
    }
}
