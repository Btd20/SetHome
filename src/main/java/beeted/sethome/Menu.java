package beeted.sethome;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class Menu implements Listener {

    private final SetHome plugin;

    public Menu (SetHome plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        // Obtiene el jugador que ha ejecutado el comando
        Player player = event.getPlayer();

        // Lectura de la config
        FileConfiguration config = plugin.getConfig();
        String path1 = "menu.open-command";

        // Verifica si el jugador ha ejecutado el comando "/menu"
        if (event.getMessage().equalsIgnoreCase(config.getString(path1))) {
            if (player.hasPermission("sethome.use")) {

                // Lectura de la config
                String path2 = "menu.gui-title";

                // Crea un nuevo inventario para el menú
                Inventory menu = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', config.getString(path2)));

                // Crea los dos items en el medio del menú
                ItemStack setHomeItem = new ItemStack(Material.RED_BED);
                ItemMeta setHomeMeta = setHomeItem.getItemMeta();

                //Lectura de la config
                String path3 = "menu.set-home-item";

                setHomeMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString(path3)));
                setHomeItem.setItemMeta(setHomeMeta);

                ItemStack goHomeItem = new ItemStack(Material.COMPASS);
                ItemMeta goHomeMeta = goHomeItem.getItemMeta();

                //Lectura de la config
                String path4 = "menu.travel-home-item";

                goHomeMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString(path4)));
                goHomeItem.setItemMeta(goHomeMeta);

                /////
                // Crea un nuevo objeto para el inventario
                ItemStack miObjeto = new ItemStack(Material.NETHER_STAR);
                ItemMeta miObjetoMeta = miObjeto.getItemMeta();

                //Lectura de la config
                String path5 = "menu.info-title";

                miObjetoMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString(path5)));
                miObjeto.setItemMeta(miObjetoMeta);

                // Agrega el objeto al inventario
                menu.setItem(13, miObjeto);
                /////

                // Agrega los items al inventario
                menu.setItem(11, setHomeItem);
                menu.setItem(15, goHomeItem);

                // Abre el menú para el jugador
                player.openInventory(menu);

                // Cancela la ejecución del comando para evitar que el servidor lo procese
                event.setCancelled(true);
            } else {
                player.sendMessage(ChatColor.RED + "You don't have permission.");
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Obtiene el jugador que ha clicado en el inventario
        Player player = (Player) event.getWhoClicked();
        FileConfiguration config = plugin.getConfig();

        // Lectura de la config
        String path0 = "menu.open-command";
        String path1 = "menu.gui-title";

        // Verifica si el inventario es el menú de hogar
        if (event.getView().getTitle().equals(ChatColor.translateAlternateColorCodes('&', config.getString(path1)))) {
            event.setCancelled(true); // Cancela el evento para evitar que el jugador mueva los items

            // Obtiene el item que ha sido clicado
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem == null) {
                return; // Sale del método si el item clicado es nulo
            }

            //Lectura de la config
            String path2 = "menu.set-home-item";

            // Verifica si el item es el de establecer hogar
            if (clickedItem.getItemMeta().getDisplayName().equals(ChatColor.translateAlternateColorCodes('&', config.getString(path2)))) {
                // Ejecuta el comando /sethome
                player.performCommand("sethome");

                String home = "X: " + player.getLocation().getBlockX() + " Y: " + player.getLocation().getBlockY() + " Z: " + player.getLocation().getBlockZ();

                // Actualiza el archivo YAML del jugador con las nuevas coordenadas del hogar
                File dataFolder = new File(plugin.getDataFolder(), "data");
                File playerFile = new File(dataFolder, player.getUniqueId() + ".yml");
                YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
                playerConfig.set("home", home);

                try {
                    playerConfig.save(playerFile);
                } catch (IOException e) {
                    // Maneja cualquier excepción
                    e.printStackTrace();
                }

                // Cancela la ejecución del comando para evitar que el servidor lo procese
                event.setCancelled(true);

                //Lectura de la config
                String path4 = "messages.home-established";

                // Envía un mensaje al jugador confirmando que se ha establecido su hogar
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString(path4)));

                // Cierra el inventario
                player.closeInventory();
            }

            //Lectura de la config
            String path3 = "menu.travel-home-item";

            // Verifica si el item es el de ir al hogar
            if (clickedItem.getItemMeta().getDisplayName().equals(ChatColor.translateAlternateColorCodes('&', config.getString(path3)))) {
                // Ejecuta el comando /home
                player.performCommand("home");

                // Carga las coordenadas del hogar del jugador desde el archivo YAML
                File dataFolder = new File(plugin.getDataFolder(), "data");
                File playerFile = new File(dataFolder, player.getUniqueId() + ".yml");
                YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
                String home = playerConfig.getString("home");

                // Verifica si el jugador tiene un hogar establecido
                if (home != null) {
                    // Separa las coordenadas de X, Y y Z
                    String[] coords = home.split(" ");
                    int x = Integer.parseInt(coords[1]);
                    int y = Integer.parseInt(coords[3]);
                    int z = Integer.parseInt(coords[5]);

                    // Teletransporta al jugador a su hogar
                    Location homeLocation = new Location(player.getWorld(), x, y, z);
                    player.teleport(homeLocation);

                    // Lectura de la config
                    String path6 = "messages.teleported";

                    // Manda el mensaje conforme se ha teletransportado
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString(path6)));

                    // Cancela la ejecución del comando para evitar que el servidor lo procese
                    event.setCancelled(true);
                } else {
                    // Envía un mensaje al jugador informándole que no tiene un hogar establecido

                    //Lectura de la config
                    String path5 = "messages.not-established-home";

                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString(path5)));
                }
                // Cierra el inventario
                player.closeInventory();
            }
        }
    }
}

