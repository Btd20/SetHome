package beeted.sethome;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.awt.SystemColor.menu;

public class Menu implements Listener {

    private final SetHome plugin;
    private final Map<Player, String> pendingHomeNames = new HashMap<>();
    private final Map<Player, BukkitRunnable> teleportCooldowns = new HashMap<>();
    private long cooldownTime; // Tiempo de cooldown en milisegundos
    private final Map<Player, BukkitRunnable> teleportTasks = new HashMap<>();
    private final Set<Player> teleportingPlayers = new HashSet<>();



    public Menu(SetHome plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    private void reloadConfig() {
        FileConfiguration config = plugin.getConfig();
        cooldownTime = config.getLong("teleport-cooldown") * 1000L; // Convertir segundos a milisegundos
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        // Obtiene el jugador que ha ejecutado el comando
        Player player = event.getPlayer();

        // Lectura de la config
        FileConfiguration config = plugin.getConfig();
        String path1 = "menu.open-command";

        // Verifica si el jugador ha ejecutado el comando configurado
        if (event.getMessage().equalsIgnoreCase(config.getString(path1))) {

            // Lectura de la config
            String path2 = "menu.gui-title";

            // Crea un nuevo inventario para el menú
            Inventory menu = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', config.getString(path2)));

            // Configuración del borde
            String borderGlassColorName = config.getString("menu.glass-pane-color", "GRAY").toUpperCase();
            Material borderGlassMaterial = Material.matchMaterial(borderGlassColorName + "_STAINED_GLASS_PANE");
            if (borderGlassMaterial == null) {
                borderGlassMaterial = Material.GRAY_STAINED_GLASS_PANE; // Valor predeterminado
            }
            ItemStack borderGlassPane = new ItemStack(borderGlassMaterial);
            ItemMeta borderGlassMeta = borderGlassPane.getItemMeta();
            borderGlassMeta.setDisplayName(" ");
            borderGlassPane.setItemMeta(borderGlassMeta);

            // Rellenar bordes con cristal
            int[] borderSlots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
            for (int slot : borderSlots) {
                menu.setItem(slot, borderGlassPane);
            }

            // Configuración del cristal central
            String centerGlassColorName = config.getString("menu.center-glass-color", "LIGHT_BLUE").toUpperCase();
            Material centerGlassMaterial = Material.matchMaterial(centerGlassColorName + "_STAINED_GLASS_PANE");
            if (centerGlassMaterial == null) {
                centerGlassMaterial = Material.LIGHT_BLUE_STAINED_GLASS_PANE; // Valor predeterminado
            }
            ItemStack centerGlassPane = new ItemStack(centerGlassMaterial);
            ItemMeta centerGlassMeta = centerGlassPane.getItemMeta();
            centerGlassMeta.setDisplayName(" ");
            centerGlassPane.setItemMeta(centerGlassMeta);

            menu.setItem(10, centerGlassPane);
            menu.setItem(12, centerGlassPane);
            menu.setItem(14, centerGlassPane);
            menu.setItem(16, centerGlassPane);

            // Configuración del ítem "Set Home"
            String setHomeMaterialName = config.getString("menu.set-home-item.material").toUpperCase();
            Material setHomeMaterial = Material.matchMaterial(setHomeMaterialName);
            if (setHomeMaterial == null) {
                setHomeMaterial = Material.RED_BED; // Valor predeterminado
            }

            ItemStack setHomeItem = new ItemStack(setHomeMaterial);
            ItemMeta setHomeMeta = setHomeItem.getItemMeta();

            String setHomeDisplayName = config.getString("menu.set-home-item.display-name");
            List<String> setHomeLore = config.getStringList("menu.set-home-item.lore");

            // Configura el nombre del ítem con colores
            setHomeMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', setHomeDisplayName));

            // Configura el lore del ítem con colores usando translateAlternateColorCodes
            for (int i = 0; i < setHomeLore.size(); i++) {
                setHomeLore.set(i, ChatColor.translateAlternateColorCodes('&', setHomeLore.get(i)));
            }
            setHomeMeta.setLore(setHomeLore);

            setHomeItem.setItemMeta(setHomeMeta);

            menu.setItem(11, setHomeItem);

            // Ítem de información (Nether Star sin lore)
            ItemStack miObjeto = new ItemStack(Material.NETHER_STAR);
            ItemMeta miObjetoMeta = miObjeto.getItemMeta();
            miObjetoMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("menu.info-title")));
            miObjeto.setItemMeta(miObjetoMeta);

            menu.setItem(13, miObjeto);

            // Ítem de lista de hogares
            String homeListMaterialName = config.getString("menu.your-homes-item.material", "OAK_DOOR").toUpperCase();
            Material homeListMaterial = Material.matchMaterial(homeListMaterialName);
            if (homeListMaterial == null) {
                homeListMaterial = Material.RED_WOOL; // Valor predeterminado
            }

            ItemStack homeListItem = new ItemStack(homeListMaterial);
            ItemMeta doorItemMeta = homeListItem.getItemMeta();
            String path6 = "menu.your-homes-item";
            String homeListDisplayName = config.getString(path6 + ".display-name");
            List<String> homeListLore = config.getStringList(path6 + ".lore");

            doorItemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', homeListDisplayName));

            for (int i = 0; i < homeListLore.size(); i++) {
                homeListLore.set(i, ChatColor.translateAlternateColorCodes('&', homeListLore.get(i)));
            }
            doorItemMeta.setLore(homeListLore);

            homeListItem.setItemMeta(doorItemMeta);

            menu.setItem(15, homeListItem);

            // Abre el menú para el jugador
            player.openInventory(menu);

            // Cancela la ejecución del comando para evitar que el servidor lo procese
            event.setCancelled(true);
        }
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Verifica que el clic es de un jugador
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        FileConfiguration config = plugin.getConfig();

        // Obtiene el inventario
        Inventory inventory = event.getInventory();
        String inventoryTitle = ChatColor.translateAlternateColorCodes('&', config.getString("menu.gui-title"));

        // Verifica si el inventario es el menú de hogar
        if (event.getView().getTitle().equals(inventoryTitle)) {
            event.setCancelled(true); // Cancela el evento para evitar que el jugador mueva los ítems

            // Obtiene el ítem que ha sido clicado
            ItemStack clickedItem = event.getCurrentItem();

            // Verifica si el ítem no es null y tiene un ItemMeta
            if (clickedItem != null && clickedItem.getItemMeta() != null) {
                // Verifica si el ítem es el de establecer hogar
                String setHomeItemName = ChatColor.translateAlternateColorCodes('&', config.getString("menu.set-home-item.display-name"));
                if (clickedItem.getItemMeta().getDisplayName().equals(setHomeItemName)) {
                    // Guarda el jugador que está estableciendo el hogar
                    pendingHomeNames.put(player, "");

                    // Envía un mensaje pidiendo el nombre de la casa
                    String chatName = config.getString("messages.enter-home-name");
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', chatName));

                    // Cierra el menú para evitar clics adicionales mientras se espera el nombre de la casa
                    player.closeInventory();
                }

                // Verifica si el ítem es el de la puerta
                String yourHomesItemName = ChatColor.translateAlternateColorCodes('&', config.getString("menu.your-homes-item.display-name"));
                if (clickedItem.getItemMeta().getDisplayName().equals(yourHomesItemName)) {
                    // Cierra el inventario actual
                    player.closeInventory();

                    // Abre el inventario "Your homes" después de un breve retraso para asegurarse de que el inventario actual se haya cerrado completamente
                    Bukkit.getScheduler().runTaskLater(plugin, () -> openYourHomesInventory(player, config.getString("homes-menu.gui-title")), 1); // Ajusta el retraso si es necesario
                }
            }
        }

        // Verifica si el inventario clicado es el inventario "Your homes"
        if (event.getView().getTitle().equals(ChatColor.translateAlternateColorCodes('&', config.getString("homes-menu.gui-title")))) {
            event.setCancelled(true); // Cancela el evento para evitar que el jugador tome los ítems

            // Obtiene el ítem que ha sido clicado
            ItemStack clickedItem = event.getCurrentItem();

            // Verifica si el ítem no es null y tiene un ItemMeta
            if (clickedItem != null && clickedItem.getItemMeta() != null) {
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

                // Verifica si el clic es del tipo izquierdo y el ítem es una cama roja
                if (event.getClick() == ClickType.LEFT && clickedItem.getType() == Material.RED_BED) {
                    String homeName = clickedItem.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "homePosition"), PersistentDataType.STRING);
                    if (homeName != null) {
                        // Verifica si el jugador tiene el permiso para omitir el cooldown
                        if (player.hasPermission("sethome.cooldown.bypass")) {
                            // Si tiene el permiso, teletranspórtalo inmediatamente
                            teleportPlayerToHome(player, homeName);
                            return;
                        }

                        // Verifica si el jugador ya tiene un cooldown de teletransporte
                        if (teleportCooldowns.containsKey(player)) {
                            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.teleport-in-progress")));
                            return;
                        }

                        // Inicia el cooldown de teletransporte
                        String teleportCooldownMessage = ChatColor.translateAlternateColorCodes('&', config.getString("messages.teleport-cooldown"));
                        String formattedMessage = teleportCooldownMessage.replace("%seconds%", String.valueOf(cooldownTime / 1000));
                        player.sendMessage(formattedMessage);

                        // Marca al jugador como teletransportándose
                        teleportingPlayers.add(player);

                        int cooldownSeconds = (int) (cooldownTime / 1000);

                        if (cooldownSeconds > 0) {
                            // Verifica si cooldown-title está habilitado
                            if (config.getBoolean("titles.cooldown-title.enable")) {
                                String teleportTitle = ChatColor.translateAlternateColorCodes('&', config.getString("titles.cooldown-title.teleport-title"));
                                String teleportSubtitle = ChatColor.translateAlternateColorCodes('&', config.getString("titles.cooldown-title.teleport-subtitle"));

                                // Envía título y subtítulo con el contador
                                int totalSeconds = (int) (cooldownTime / 1000);
                                for (int i = totalSeconds; i > 0; i--) {
                                    final int secondsLeft = i;
                                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                        player.sendTitle(teleportTitle.replace("%seconds%", String.valueOf(secondsLeft)), teleportSubtitle.replace("%seconds%", String.valueOf(secondsLeft)), 10, 20, 10);
                                    }, (totalSeconds - i) * 20L); // 20 ticks por segundo
                                }
                            } else if (config.getBoolean("titles.static-title.enable")) {
                                String teleportTitle = ChatColor.translateAlternateColorCodes('&', config.getString("titles.static-title.teleport-title"));
                                String teleportSubtitle = ChatColor.translateAlternateColorCodes('&', config.getString("titles.static-title.teleport-subtitle"));

                                // Envía título y subtítulo estáticos
                                int totalSeconds = (int) (cooldownTime / 1000);
                                player.sendTitle(teleportTitle.replace("%seconds%", String.valueOf(totalSeconds)), teleportSubtitle.replace("%seconds%", String.valueOf(totalSeconds)), 10, totalSeconds * 20, 10);
                            }
                        }

                        // Tarea para teletransportar al jugador después del cooldown
                        BukkitRunnable teleportTask = new BukkitRunnable() {
                            @Override
                            public void run() {
                                // Verifica si el jugador aún está en el proceso de teletransporte
                                if (teleportingPlayers.contains(player)) {
                                    // Realiza el teletransporte
                                    teleportPlayerToHome(player, homeName);
                                }
                            }
                        };

                        // Programa la tarea con el cooldown
                        teleportTask.runTaskLater(plugin, cooldownTime / 50); // Dividido por 50 porque runTaskLater usa ticks (1 tick = 50ms)

                        // Almacena la tarea programada para poder cancelarla si es necesario
                        teleportTasks.put(player, teleportTask);

                        // Cierra el inventario para que el jugador no haga clic en otros ítems durante el cooldown
                        player.closeInventory();
                    }
                }

                // Verifica si el clic es del tipo derecho y el ítem es una cama roja
                if (event.getClick() == ClickType.RIGHT && clickedItem.getType() == Material.RED_BED) {
                    String homeName = clickedItem.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "homePosition"), PersistentDataType.STRING);
                    if (homeName != null) {
                        // Abre el menú de confirmación
                        openConfirmationMenu(player, homeName);
                    }
                }
            }
        }

        // Verifica si el inventario es el menú de confirmación
        if (event.getView().getTitle().equals(ChatColor.translateAlternateColorCodes('&', config.getString("confirmation-menu.gui-title")))) {
            event.setCancelled(true); // Cancela el evento para evitar que el jugador mueva los ítems

            // Obtiene el ítem que ha sido clicado
            ItemStack clickedItem = event.getCurrentItem();

            // Verifica si el ítem no es null y tiene un ItemMeta
            if (clickedItem != null && clickedItem.getItemMeta() != null) {
                String itemName = clickedItem.getItemMeta().getDisplayName();
                String confirmItemName = ChatColor.translateAlternateColorCodes('&', config.getString("confirmation-menu.confirm-item.display-name"));
                String cancelItemName = ChatColor.translateAlternateColorCodes('&', config.getString("confirmation-menu.cancel-item.display-name"));

                // Confirmar la eliminación del hogar
                if (itemName.equals(confirmItemName)) {
                    // Elimina el hogar
                    File dataFolder = new File(plugin.getDataFolder(), "data");
                    File playerFile = new File(dataFolder, player.getUniqueId() + ".yml");
                    YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

                    List<String> homes = playerConfig.getStringList("homes");
                    homes.remove(pendingHomeNames.get(player)); // Usa el nombre del hogar pendiente
                    playerConfig.set("homes", homes);
                    playerConfig.set(pendingHomeNames.get(player), null); // Elimina las coordenadas del hogar

                    try {
                        playerConfig.save(playerFile);
                        // Mensaje de confirmación
                        String homeRemoved = config.getString("messages.home-removed");
                        String removedMessage = ChatColor.translateAlternateColorCodes('&', homeRemoved);
                        removedMessage = removedMessage.replace("%home%", pendingHomeNames.get(player));
                        player.sendMessage(removedMessage);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    player.closeInventory();
                }

                // Cancelar la eliminación del hogar
                if (itemName.equals(cancelItemName)) {
                    player.closeInventory();
                }
            }
        }
    }

    // Método para teletransportar al jugador al hogar
    private void teleportPlayerToHome(Player player, String homeName) {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        File playerFile = new File(dataFolder, player.getUniqueId() + ".yml");
        YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

        // Obtener el mundo y las coordenadas del hogar
        String worldName = playerConfig.getString(homeName + ".world");
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            double x = playerConfig.getDouble(homeName + ".x");
            double y = playerConfig.getDouble(homeName + ".y");
            double z = playerConfig.getDouble(homeName + ".z");

            Location homeLocation = new Location(world, x, y, z);
            player.teleport(homeLocation);

            // Mensaje de confirmación
            String teleportedToHomePath = plugin.getConfig().getString("messages.teleported");
            String teleportMessage = ChatColor.translateAlternateColorCodes('&', teleportedToHomePath);
            teleportMessage = teleportMessage.replace("%home%", homeName);
            player.sendMessage(teleportMessage);
        } else {
            // Mensaje de error si el mundo no se encuentra
            String worldNotFound = plugin.getConfig().getString("messages.world-not-found");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', worldNotFound));
        }

        // Limpia el cooldown
        teleportCooldowns.remove(player);
        teleportingPlayers.remove(player);
    }

    private void openConfirmationMenu(Player player, String homeName) {
        FileConfiguration config = plugin.getConfig();
        Inventory confirmationMenu = Bukkit.createInventory(null, 9, ChatColor.translateAlternateColorCodes('&', config.getString("confirmation-menu.gui-title")));

        // Crear ítem de confirmación
        ItemStack confirmItem = new ItemStack(Material.GREEN_WOOL); // Usar el material que desees
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("confirmation-menu.confirm-item.display-name")));
        confirmItem.setItemMeta(confirmMeta);

        // Crear ítem de cancelación
        ItemStack cancelItem = new ItemStack(Material.RED_WOOL); // Usar el material que desees
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("confirmation-menu.cancel-item.display-name")));
        cancelItem.setItemMeta(cancelMeta);

        // Agregar ítems al inventario
        confirmationMenu.setItem(3, confirmItem);
        confirmationMenu.setItem(5, cancelItem);

        // Mostrar el inventario al jugador
        player.openInventory(confirmationMenu);

        // Guarda el nombre del hogar pendiente para usarlo en la confirmación
        pendingHomeNames.put(player, homeName);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Verifica si el jugador está en proceso de teletransporte
        if (teleportingPlayers.contains(player)) {
            FileConfiguration config = plugin.getConfig();

            // Verifica si la cancelación por movimiento está habilitada
            boolean cancelOnMove = config.getBoolean("cancel-on-move");
            if (!cancelOnMove) {
                return; // Si está deshabilitado, no hacemos nada
            }

            // Obtiene las ubicaciones de inicio y destino del movimiento
            Location from = event.getFrom();
            Location to = event.getTo();

            // Solo cancela el teletransporte si el jugador se mueve a otro bloque
            if (from.getBlockX() != to.getBlockX() ||
                    from.getBlockY() != to.getBlockY() ||
                    from.getBlockZ() != to.getBlockZ()) {

                // Cancela la tarea de teletransporte
                BukkitRunnable teleportTask = teleportTasks.get(player);
                if (teleportTask != null) {
                    teleportTask.cancel();
                    teleportTasks.remove(player);
                }

                // Obtiene el mensaje de cancelación de la configuración
                String teleportCancelledMessage = ChatColor.translateAlternateColorCodes('&', config.getString("messages.teleport-cancelled"));

                // Reinicia el titulo del jugador
                player.resetTitle();

                // Envía el mensaje al jugador
                player.sendMessage(teleportCancelledMessage);

                // Limpia el estado del jugador
                teleportingPlayers.remove(player);
            }
        }
    }

    private void openMainMenu(Player player) {
        FileConfiguration config = plugin.getConfig();
        String menuTitlePath = "menu.gui-title";

        // Crea un nuevo inventario para el menú principal
        Inventory menu = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', config.getString(menuTitlePath)));

        // Configuración del borde
        String borderGlassColorName = config.getString("menu.glass-pane-color", "GRAY").toUpperCase();
        Material borderGlassMaterial = Material.matchMaterial(borderGlassColorName + "_STAINED_GLASS_PANE");
        if (borderGlassMaterial == null) {
            borderGlassMaterial = Material.GRAY_STAINED_GLASS_PANE; // Valor predeterminado
        }
        ItemStack borderGlassPane = new ItemStack(borderGlassMaterial);
        ItemMeta borderGlassMeta = borderGlassPane.getItemMeta();
        borderGlassMeta.setDisplayName(" ");
        borderGlassPane.setItemMeta(borderGlassMeta);

        // Rellenar bordes con cristal
        int[] borderSlots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
        for (int slot : borderSlots) {
            menu.setItem(slot, borderGlassPane);
        }

        // Configuración del cristal central
        String centerGlassColorName = config.getString("menu.center-glass-color", "LIGHT_BLUE").toUpperCase();
        Material centerGlassMaterial = Material.matchMaterial(centerGlassColorName + "_STAINED_GLASS_PANE");
        if (centerGlassMaterial == null) {
            centerGlassMaterial = Material.LIGHT_BLUE_STAINED_GLASS_PANE; // Valor predeterminado
        }
        ItemStack centerGlassPane = new ItemStack(centerGlassMaterial);
        ItemMeta centerGlassMeta = centerGlassPane.getItemMeta();
        centerGlassMeta.setDisplayName(" ");
        centerGlassPane.setItemMeta(centerGlassMeta);

        menu.setItem(10, centerGlassPane);
        menu.setItem(12, centerGlassPane);
        menu.setItem(14, centerGlassPane);
        menu.setItem(16, centerGlassPane);

        // Configuración del ítem "Set Home"
        String setHomeMaterialName = config.getString("menu.set-home-item.material").toUpperCase();
        Material setHomeMaterial = Material.matchMaterial(setHomeMaterialName);
        if (setHomeMaterial == null) {
            setHomeMaterial = Material.RED_BED; // Valor predeterminado
        }

        ItemStack setHomeItem = new ItemStack(setHomeMaterial);
        ItemMeta setHomeMeta = setHomeItem.getItemMeta();

        String setHomeDisplayName = config.getString("menu.set-home-item.display-name");
        List<String> setHomeLore = config.getStringList("menu.set-home-item.lore");

        // Configura el nombre del ítem con colores
        setHomeMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', setHomeDisplayName));

        // Configura el lore del ítem con colores usando translateAlternateColorCodes
        for (int i = 0; i < setHomeLore.size(); i++) {
            setHomeLore.set(i, ChatColor.translateAlternateColorCodes('&', setHomeLore.get(i)));
        }
        setHomeMeta.setLore(setHomeLore);

        setHomeItem.setItemMeta(setHomeMeta);

        menu.setItem(11, setHomeItem);

        // Ítem de información (Nether Star sin lore)
        ItemStack miObjeto = new ItemStack(Material.NETHER_STAR);
        ItemMeta miObjetoMeta = miObjeto.getItemMeta();
        miObjetoMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("menu.info-title")));
        miObjeto.setItemMeta(miObjetoMeta);

        menu.setItem(13, miObjeto);

        // Ítem de lista de hogares
        String homeListMaterialName = config.getString("menu.your-homes-item.material", "OAK_DOOR").toUpperCase();
        Material homeListMaterial = Material.matchMaterial(homeListMaterialName);
        if (homeListMaterial == null) {
            homeListMaterial = Material.RED_WOOL; // Valor predeterminado
        }

        ItemStack homeListItem = new ItemStack(homeListMaterial);
        ItemMeta doorItemMeta = homeListItem.getItemMeta();
        String path6 = "menu.your-homes-item";
        String homeListDisplayName = config.getString(path6 + ".display-name");
        List<String> homeListLore = config.getStringList(path6 + ".lore");

        doorItemMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', homeListDisplayName));

        for (int i = 0; i < homeListLore.size(); i++) {
            homeListLore.set(i, ChatColor.translateAlternateColorCodes('&', homeListLore.get(i)));
        }
        doorItemMeta.setLore(homeListLore);

        homeListItem.setItemMeta(doorItemMeta);

        menu.setItem(15, homeListItem);

        // Abre el menú para el jugador
        player.openInventory(menu);
    }

    private void openYourHomesInventory(Player player, String path) {
        player.closeInventory(); // Cierra cualquier inventario abierto antes de abrir uno nuevo

        // Crea un nuevo inventario para "Your homes"
        FileConfiguration config = plugin.getConfig();
        Inventory homesMenu = Bukkit.createInventory(null, 54, ChatColor.translateAlternateColorCodes('&', path));

        // Carga las ubicaciones de los hogares del archivo YAML del jugador
        File dataFolder = new File(plugin.getDataFolder(), "data");
        File playerFile = new File(dataFolder, player.getUniqueId() + ".yml");
        YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

        // Obtén la lista de hogares del jugador
        List<String> homes = playerConfig.getStringList("homes");

        // Si hay hogares, agrega ítems de hogar
        if (!homes.isEmpty()) {
            int position = 0;

            // Itera sobre cada hogar y agrega una cama al inventario por cada uno
            for (String home : homes) {
                // Verifica si el hogar tiene coordenadas almacenadas
                if (playerConfig.contains(home)) {
                    String world = playerConfig.getString(home + ".world");
                    double x = playerConfig.getDouble(home + ".x");
                    double y = playerConfig.getDouble(home + ".y");
                    double z = playerConfig.getDouble(home + ".z");

                    // Verifica que el mundo no sea null antes de procesarlo
                    if (world != null) {
                        // Crea un nuevo ítem de cama
                        ItemStack bedItem = new ItemStack(Material.RED_BED);
                        ItemMeta bedMeta = bedItem.getItemMeta();

                        // Configura el nombre del hogar con colores
                        String homeNamePath = config.getString("homes-menu.home-item.display-name");
                        if (homeNamePath != null) {
                            String homeNameItem = ChatColor.translateAlternateColorCodes('&', homeNamePath);
                            homeNameItem = homeNameItem.replace("%home%", home);
                            bedMeta.setDisplayName(homeNameItem);
                        }

                        // Asigna un identificador único al ítem (el nombre del hogar)
                        bedMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "homePosition"), PersistentDataType.STRING, home);

                        // Configura el lore del hogar con las coordenadas y colores
                        List<String> lore = config.getStringList("homes-menu.home-item.lore");
                        if (lore != null) {
                            for (int i = 0; i < lore.size(); i++) {
                                lore.set(i, ChatColor.translateAlternateColorCodes('&', lore.get(i)
                                        .replace("%home%", home)
                                        .replace("%world%", world)
                                        .replace("%x%", String.valueOf(x))
                                        .replace("%y%", String.valueOf(y))
                                        .replace("%z%", String.valueOf(z))));
                            }
                            bedMeta.setLore(lore);
                        }

                        bedItem.setItemMeta(bedMeta);

                        // Agrega la cama al inventario
                        homesMenu.addItem(bedItem);
                        position++;
                    }
                }
            }
        } else {
            // Si no hay hogares, agregar un ítem informativo
            ItemStack noHomesItem = new ItemStack(Material.PAPER);
            ItemMeta noHomesMeta = noHomesItem.getItemMeta();

            if (noHomesMeta != null) {
                String noHomesDisplayName = config.getString("homes-menu.no-homes-item.display-name");
                List<String> noHomesLore = config.getStringList("homes-menu.no-homes-item.lore");

                if (noHomesDisplayName != null) {
                    noHomesMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', noHomesDisplayName));
                }

                if (noHomesLore != null) {
                    noHomesLore.replaceAll(line -> ChatColor.translateAlternateColorCodes('&', line));
                    noHomesMeta.setLore(noHomesLore);
                }

                noHomesItem.setItemMeta(noHomesMeta);
            }

            homesMenu.setItem(22, noHomesItem); // Centra el ítem en el medio del menú
        }

        // Agrega una línea de paneles de cristal negro en la cuarta fila
        for (int i = 45; i < 54; i++) {
            ItemStack glassPane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta meta = glassPane.getItemMeta();

            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&f"));
                glassPane.setItemMeta(meta);
            }

            homesMenu.setItem(i, glassPane);
        }

        // Botones de navegación
        ItemStack goBack = new ItemStack(Material.ARROW);
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta goBackMeta = goBack.getItemMeta();
        ItemMeta closeMeta = close.getItemMeta();

        if (goBackMeta != null) {
            String goBackPath = config.getString("homes-menu.go-back-item");
            if (goBackPath != null) {
                goBackMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', goBackPath));
                goBack.setItemMeta(goBackMeta);
            }
        }

        if (closeMeta != null) {
            String closePath = config.getString("homes-menu.close-item");
            if (closePath != null) {
                closeMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', closePath));
                close.setItemMeta(closeMeta);
            }
        }

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
            // Obtiene el mensaje de chat del jugador
            String message = event.getMessage();

            // Verifica si el jugador ha escrito "cancel"
            if (message.equalsIgnoreCase("cancel")) {
                // Cancela la entrada del nombre del hogar y notifica al jugador
                pendingHomeNames.remove(player);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-cancelled")));
                event.setCancelled(true);
                return;
            }

            // Obtiene el nombre de la casa del mensaje de chat
            String homeName = message;

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

            // Obtén el límite de hogares desde los permisos del jugador
            int maxHomes = getMaxHomesForPlayer(player);

            // Verifica cuántos hogares tiene el jugador
            List<String> homes = playerConfig.getStringList("homes");
            if (homes.size() >= maxHomes) {
                // El jugador ha alcanzado el límite de hogares, notifica al jugador
                String homeLimitReached = config.getString("messages.home-limit-reached");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', homeLimitReached.replace("%limit%", String.valueOf(maxHomes))));
                event.setCancelled(true);
                return;
            }

            // Guarda el nombre de la casa en el mapa
            pendingHomeNames.put(player, homeName);

            // Cancela el evento para evitar que el mensaje de chat aparezca en el chat global
            event.setCancelled(true);

            // Agrega el nuevo hogar a la lista de hogares del jugador
            homes.add(homeName);
            playerConfig.set("homes", homes);

            // Obtiene la ubicación del jugador y el nombre del mundo
            Location loc = player.getLocation();
            String worldName = loc.getWorld().getName();

            // Actualiza el archivo YAML del jugador con las nuevas coordenadas del hogar y el mundo
            playerConfig.set(homeName + ".world", worldName);
            playerConfig.set(homeName + ".x", loc.getBlockX());
            playerConfig.set(homeName + ".y", loc.getBlockY());
            playerConfig.set(homeName + ".z", loc.getBlockZ());

            try {
                playerConfig.save(playerFile);

                // Envía un mensaje al jugador confirmando que se ha establecido su hogar
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-established")));
            } catch (IOException e) {
                // Maneja cualquier excepción
                e.printStackTrace();
            }

            // Limpia el nombre de la casa en el mapa después de un breve retraso
            Bukkit.getScheduler().runTaskLater(plugin, () -> pendingHomeNames.remove(player), 20);
        }
    }

    private int getMaxHomesForPlayer(Player player) {
        int maxHomes = Integer.MAX_VALUE; // No hay límite por defecto

        for (PermissionAttachmentInfo permInfo : player.getEffectivePermissions()) {
            String permission = permInfo.getPermission();
            if (permission.startsWith("sethome.maxhomes.")) {
                try {
                    int homes = Integer.parseInt(permission.split("\\.")[2]);
                    if (homes > 0) {
                        maxHomes = homes;
                    }
                } catch (NumberFormatException e) {
                    // Maneja el caso en que el permiso no tenga un número válido
                    e.printStackTrace();
                }
            }
        }

        return maxHomes;
    }


}

