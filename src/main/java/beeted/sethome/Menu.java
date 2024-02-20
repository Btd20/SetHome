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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Menu implements Listener {

    private final SetHome plugin;
    private final Map<Player, String> pendingHomeNames = new HashMap<>();

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

                // Abre el menú para el jugador
                player.openInventory(menu);

                // Cancela la ejecución del comando para evitar que el servidor lo procese
                event.setCancelled(true);

                String path6 = "menu.your-homes-item";

                // Crea un nuevo objeto para el inventario
                ItemStack homeListItem = new ItemStack(Material.OAK_DOOR);
                ItemMeta doorItemMeta = homeListItem.getItemMeta();
                doorItemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString(path6)));
                homeListItem.setItemMeta(doorItemMeta);

                // Agrega el objeto al inventario
                menu.setItem(15, homeListItem); // Puedes ajustar la posición del ítem según tus necesidades
            } else {
                String permissions = config.getString("no-permissions");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', permissions));
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Obtiene el jugador que ha clicado en el inventario
        Player player = (Player) event.getWhoClicked();
        FileConfiguration config = plugin.getConfig();

        // Lectura de la config
        String path1 = "menu.gui-title";

        // Verifica si el inventario es el menú de hogar
        if (event.getView().getTitle().equals(ChatColor.translateAlternateColorCodes('&', config.getString(path1)))) {
            event.setCancelled(true); // Cancela el evento para evitar que el jugador mueva los items

            // Obtiene el item que ha sido clicado
            ItemStack clickedItem = event.getCurrentItem();

            //Lectura de la config
            String path2 = "menu.set-home-item";

            // Verifica si el item es el de establecer hogar
            if (clickedItem.getItemMeta().getDisplayName().equals(ChatColor.translateAlternateColorCodes('&', config.getString(path2)))) {
                // Guarda el jugador que está estableciendo el hogar
                pendingHomeNames.put(player, "");

                // Envía un mensaje pidiendo el nombre de la casa
                String chatName = config.getString("messages.enter-home-name");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', chatName));

                // Cierra el menú para evitar clics adicionales mientras se espera el nombre de la casa
                player.closeInventory();
            }

            String path6 = "menu.your-homes-item";
            String homesTitle = "homes-menu.gui-title";

            // Verifica si el item es el de la puerta
            if (clickedItem.getItemMeta().getDisplayName().equals(ChatColor.translateAlternateColorCodes('&', config.getString(path6)))) {
                // Cierra el inventario actual
                player.closeInventory();

                // Abre el inventario "Your homes" después de un breve retraso para asegurarse de que el inventario actual se haya cerrado completamente
                Bukkit.getScheduler().runTaskLater(plugin, () -> openYourHomesInventory(player, config.getString(homesTitle)), 1);
            }
        }

        String path6 = "homes-menu.gui-title";

        // Verifica si el inventario clicado es el inventario "Your homes"
        if (event.getView().getTitle().equals(ChatColor.translateAlternateColorCodes('&', config.getString(path6)))) {
            event.setCancelled(true); // Cancela el evento para evitar que el jugador tome la cama

            // Obtiene el item que ha sido clicado
            ItemStack clickedItem = event.getCurrentItem();

            String goBackItemPath = "homes-menu.go-back-item";
            if (clickedItem.getItemMeta().getDisplayName().equals(ChatColor.translateAlternateColorCodes('&', config.getString(goBackItemPath)))) {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // Lógica para abrir el menú principal nuevamente
                    openMainMenu(player);
                }, 1);
                return;
            }

            String closeItemPath = "homes-menu.close-item";
            if (clickedItem.getItemMeta().getDisplayName().equals(ChatColor.translateAlternateColorCodes('&', config.getString(closeItemPath)))) {
                player.closeInventory();
                return;
            }

            if (clickedItem != null && clickedItem.getType() == Material.RED_BED) {
                String homeName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName().replace("Home: ", ""));

                // Teletransporta al jugador a su hogar
                File dataFolder = new File(plugin.getDataFolder(), "data");
                File playerFile = new File(dataFolder, player.getUniqueId() + ".yml");
                YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);
                String homeCoords = playerConfig.getString(homeName);

                if (homeCoords != null) {
                    String[] coords = homeCoords.split(" ");
                    int x = Integer.parseInt(coords[1]);
                    int y = Integer.parseInt(coords[3]);
                    int z = Integer.parseInt(coords[5]);

                    Location homeLocation = new Location(player.getWorld(), x, y, z);
                    player.teleport(homeLocation);

                    // Mensaje de confirmación
                    String teleportedToHomePath = config.getString("messages.teleported");
                    String teleportMessage = ChatColor.translateAlternateColorCodes('&', teleportedToHomePath);
                    teleportMessage = teleportMessage.replace("%home%", homeName);
                    player.sendMessage(teleportMessage);

                } else {
                    // Mensaje de error si no se encuentra la coordenada del hogar
                    String homeNotFound = config.getString("messages.home-not-found");
                    String notFoundMessage = ChatColor.translateAlternateColorCodes('&', homeNotFound);
                    player.sendMessage(notFoundMessage);
                }
            }
            if (event.getClick() == ClickType.RIGHT && clickedItem.getType() == Material.RED_BED) {
                String homeName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName().replace("Home: ", ""));

                // Elimina el hogar del jugador
                File dataFolder = new File(plugin.getDataFolder(), "data");
                File playerFile = new File(dataFolder, player.getUniqueId() + ".yml");
                YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

                List<String> homes = playerConfig.getStringList("homes");
                homes.remove(homeName);
                playerConfig.set("homes", homes);
                playerConfig.set(homeName, null); // Elimina las coordenadas del hogar

                try {
                    playerConfig.save(playerFile);

                    // Mensaje de confirmación
                    String homeRemoved = config.getString("messages.home-removed");
                    String removedMessage = ChatColor.translateAlternateColorCodes('&', homeRemoved);
                    removedMessage = removedMessage.replace("%home%", homeName);
                    player.sendMessage(removedMessage);
                } catch (IOException e) {
                    // Maneja cualquier excepción
                    e.printStackTrace();
                }

                // Recarga el inventario después de eliminar el hogar
                Bukkit.getScheduler().runTaskLater(plugin, () -> openYourHomesInventory(player, config.getString("homes-menu.gui-title")), 1);
            }
        }
    }

    private void openMainMenu(Player player) {
        FileConfiguration config = plugin.getConfig();
        String menuTitlePath = "menu.gui-title";

        // Crea un nuevo inventario para el menú principal
        Inventory menu = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', config.getString(menuTitlePath)));

        // Agrega los elementos al menú principal (ajusta según tus necesidades)
        ItemStack setHomeItem = new ItemStack(Material.RED_BED);
        ItemMeta setHomeMeta = setHomeItem.getItemMeta();

        String setHomeItemPath = "menu.set-home-item";

        setHomeMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString(setHomeItemPath)));
        setHomeItem.setItemMeta(setHomeMeta);

        ItemStack miObjeto = new ItemStack(Material.NETHER_STAR);
        ItemMeta miObjetoMeta = miObjeto.getItemMeta();

        String infoTitlePath = "menu.info-title";

        miObjetoMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString(infoTitlePath)));
        miObjeto.setItemMeta(miObjetoMeta);

        ItemStack portalItem = new ItemStack(Material.OAK_DOOR);
        ItemMeta portalMeta = portalItem.getItemMeta();

        String portalItemPath = "menu.your-homes-item";
        portalMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString(portalItemPath)));
        portalItem.setItemMeta(portalMeta);

        menu.setItem(13, miObjeto);
        menu.setItem(11, setHomeItem);
        menu.setItem(15, portalItem); // Ajusta la posición según tus necesidades

        player.openInventory(menu);
    }

    private void openYourHomesInventory(Player player, String path) {
        player.closeInventory();
        // Crea un nuevo inventario para "Your homes"
        FileConfiguration config = plugin.getConfig();
        Inventory homesMenu = Bukkit.createInventory(null, 54, ChatColor.translateAlternateColorCodes('&', path));

        // Carga las ubicaciones de los hogares del archivo YAML del jugador
        File dataFolder = new File(plugin.getDataFolder(), "data");
        File playerFile = new File(dataFolder, player.getUniqueId() + ".yml");
        YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

        // Obtén la lista de hogares del jugador
        List<String> homes = playerConfig.getStringList("homes");

        // Itera sobre cada hogar y agrega una cama al inventario por cada uno
        for (String home : homes) {
            // Puedes ajustar esto según tu lógica para obtener las coordenadas del hogar
            String[] coordsString = playerConfig.getString(home).split(" ");
            int x = Integer.parseInt(coordsString[1]);
            int y = Integer.parseInt(coordsString[3]);
            int z = Integer.parseInt(coordsString[5]);

            // Crea un nuevo ítem de cama
            ItemStack bedItem = new ItemStack(Material.RED_BED);
            ItemMeta bedMeta = bedItem.getItemMeta();
            String homeNamePath = config.getString("homes-menu.home-name");
            String homeNameItem = ChatColor.translateAlternateColorCodes('&', homeNamePath);
            homeNameItem = homeNameItem.replace("%home%", home);
            bedMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', homeNameItem));
            bedItem.setItemMeta(bedMeta);

            // Agrega la cama al inventario
            homesMenu.addItem(bedItem);
        }

        // Agrega una línea de paneles de cristal negro en la cuarta fila
        for (int i = 45; i < 54; i++) {
            homesMenu.setItem(i, new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
        }

        ItemStack goBack = new ItemStack(Material.ARROW);
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta goBackMeta = goBack.getItemMeta();
        ItemMeta closeMeta = close.getItemMeta();

        String goBackPath = config.getString("homes-menu.go-back-item");
        String closePath = config.getString("homes-menu.close-item");

        goBackMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', goBackPath));
        closeMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', closePath));
        goBack.setItemMeta(goBackMeta);
        close.setItemMeta(closeMeta);

        homesMenu.setItem(48, goBack);
        homesMenu.setItem(50, close);

        // Abre el inventario "Your homes" para el jugador
        player.openInventory(homesMenu);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        FileConfiguration config = plugin.getConfig();

        // Verifica si el jugador está esperando para establecer el nombre de la casa
        if (pendingHomeNames.containsKey(player)) {
            // Obtiene el nombre de la casa del mensaje de chat
            String homeName = event.getMessage();

            // Verifica si el hogar ya existe
            File dataFolder = new File(plugin.getDataFolder(), "data");
            File playerFile = new File(dataFolder, player.getUniqueId() + ".yml");
            YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

            if (playerConfig.contains(homeName)) {
                // El hogar ya existe, notifica al jugador y cancela el evento
                String homeExists = config.getString("messages.home-exists");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', homeExists));
                event.setCancelled(true);
                return;
            }

            // Guarda el nombre de la casa en el mapa
            pendingHomeNames.put(player, homeName);

            // Cancela el evento para evitar que el mensaje de chat aparezca en el chat global
            event.setCancelled(true);

            // Obtén la lista de hogares del jugador y agrega el nuevo hogar
            List<String> homes = playerConfig.getStringList("homes");
            homes.add(homeName);
            playerConfig.set("homes", homes);

            // Actualiza el archivo YAML del jugador con las nuevas coordenadas del hogar
            playerConfig.set(homeName, "X: " + player.getLocation().getBlockX() + " Y: " + player.getLocation().getBlockY() + " Z: " + player.getLocation().getBlockZ());

            try {
                playerConfig.save(playerFile);

                // Lectura de la config
                String path4 = "messages.home-established";

                // Envía un mensaje al jugador confirmando que se ha establecido su hogar
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString(path4)));
            } catch (IOException e) {
                // Maneja cualquier excepción
                e.printStackTrace();
            }

            // Limpia el nombre de la casa en el mapa después de un breve retraso
            Bukkit.getScheduler().runTaskLater(plugin, () -> pendingHomeNames.remove(player), 20);
        }
    }
}

