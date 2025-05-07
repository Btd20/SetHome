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
import java.util.stream.Collectors;

import static java.awt.SystemColor.menu;

public class Menu implements Listener {

    private final SetHome plugin;
    private final Map<Player, String> pendingHomeNames = new HashMap<>();
    private final Map<Player, BukkitRunnable> teleportCooldowns = new HashMap<>();
    private long cooldownTime; // Tiempo de cooldown en milisegundos
    private final Map<Player, BukkitRunnable> teleportTasks = new HashMap<>();
    private final Set<Player> teleportingPlayers = new HashSet<>();
    private final Map<UUID, OfflinePlayer> playerTargetMap = new HashMap<>();
    public static boolean isAdmin = false;


    private final Map<Player, BukkitRunnable> teleportCountdownTasks = new HashMap<>();



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

        /*Menu menu = new Menu(plugin);
        // Obtiene el jugador que ha ejecutado el comando
        Player player = event.getPlayer();

        // Lectura de la config
        FileConfiguration config = plugin.getConfig();
        String path1 = "menu.open-command";

        if (!player.hasPermission("sethome.use")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.no-permissions", "&cYou don't have permission to do that.")));
            return;
        }

        // Verifica si el jugador ha ejecutado el comando configurado
        if (event.getMessage().equalsIgnoreCase(config.getString(path1))) {
            menu.openMainMenu(player);
        }*/
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!(event.getInventory().getHolder() instanceof PlayerMenuHolder)) return;

        Player player = (Player) event.getWhoClicked();
        FileConfiguration config = plugin.getConfig();
        Inventory inventory = event.getInventory();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();
        String itemName = meta.getDisplayName();

        String mainMenuTitle = ChatColor.translateAlternateColorCodes('&', config.getString("menu.gui-title"));
        String confirmationMenuTitle = ChatColor.translateAlternateColorCodes('&', config.getString("confirmation-menu.gui-title"));
        String adminConfirmationTitle = ChatColor.translateAlternateColorCodes('&', config.getString("confirmation-menu-admin.gui-title"));

        // Clic en cabeza de jugador (menú admin)
        if (clickedItem.getType() == Material.PLAYER_HEAD) {
            SkullMeta skullMeta = (SkullMeta) clickedItem.getItemMeta();
            if (skullMeta != null && skullMeta.getOwningPlayer() != null) {
                OfflinePlayer target = skullMeta.getOwningPlayer();

                // Asegúrate de que el mapa se actualice correctamente al hacer clic en la cabeza
                playerTargetMap.put(target.getUniqueId(), target);

                isAdmin = true;

                // Abre el inventario del jugador objetivo con la lista de sus hogares
                Bukkit.getScheduler().runTaskLater(plugin, () -> openPlayerHomesInventory(player, target, 0), 1);
                player.closeInventory();
            }
            return;
        }

        // MENÚ PRINCIPAL
        if (event.getView().getTitle().equals(mainMenuTitle)) {
            event.setCancelled(true);

            String setHomeItemName = ChatColor.translateAlternateColorCodes('&', config.getString("menu.set-home-item.display-name"));
            String yourHomesItemName = ChatColor.translateAlternateColorCodes('&', config.getString("menu.your-homes-item.display-name"));
            String adminItemName = ChatColor.translateAlternateColorCodes('&', config.getString("admin-menu.display-name"));

            if (itemName.equals(adminItemName) && player.hasPermission("sethome.admin")) {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> openAdminMenu(player, 0), 1);
                return;
            }

            if (itemName.equals(setHomeItemName)) {
                String currentWorld = player.getWorld().getName();
                List<String> blacklistedWorlds = config.getStringList("blacklisted-worlds");

                if (blacklistedWorlds.contains(currentWorld)) {
                    String bypassPermission = "sethome.world.bypass." + currentWorld;
                    if (!player.hasPermission(bypassPermission)) {
                        String errorMessage = config.getString("messages.error-blacklisted-world");
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', errorMessage));
                        return;
                    }
                }

                pendingHomeNames.put(player, "");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.enter-home-name")));
                player.closeInventory();
                return;
            }

            if (itemName.equals(yourHomesItemName)) {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> openYourHomesInventory(player, 0), 1);
                return;
            }
        }

        // MENÚ DE HOGARES DEL JUGADOR
        if (inventory.getHolder() instanceof PlayerMenuHolder) {
            event.setCancelled(true);

            NamespacedKey pageKey = new NamespacedKey(plugin, "menuPage");

            // CAMBIO DE PÁGINA
            if (meta.getPersistentDataContainer().has(pageKey, PersistentDataType.INTEGER)) {
                int newPage = meta.getPersistentDataContainer().get(pageKey, PersistentDataType.INTEGER);
                openYourHomesInventory(player, newPage);
                return;
            }

            // BOTONES VOLVER / CERRAR
            if (itemName.equals(ChatColor.translateAlternateColorCodes('&', config.getString("homes-menu.go-back-item")))) {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> openMainMenu(player), 1);
                return;
            }

            if (itemName.equals(ChatColor.translateAlternateColorCodes('&', config.getString("homes-menu.close-item")))) {
                player.closeInventory();
                return;
            }

            // TELETRANSPORTE (clic izquierdo)
            if (event.getClick() == ClickType.LEFT && clickedItem.getType() == Material.RED_BED) {
                String homeName = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "homePosition"), PersistentDataType.STRING);
                if (homeName == null || homeName.isEmpty()) return;

                if (player.hasPermission("sethome.cooldown.bypass")) {
                    teleportPlayerToHome(player, homeName);
                    return;
                }

                if (teleportCooldowns.containsKey(player)) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.teleport-in-progress")));
                    return;
                }

                int cooldownSeconds = (int) (cooldownTime / 1000);
                String cooldownMessage = ChatColor.translateAlternateColorCodes('&', config.getString("messages.teleport-cooldown"))
                        .replace("%seconds%", String.valueOf(cooldownSeconds));
                player.sendMessage(cooldownMessage);
                teleportingPlayers.add(player);

                if (cooldownSeconds > 0) {
                    if (config.getBoolean("titles.cooldown-title.enable")) {
                        BukkitRunnable countdownTitle = new BukkitRunnable() {
                            int secondsLeft = cooldownSeconds;

                            @Override
                            public void run() {
                                if (!teleportingPlayers.contains(player)) {
                                    player.resetTitle();
                                    this.cancel();
                                    return;
                                }

                                String title = ChatColor.translateAlternateColorCodes('&', config.getString("titles.cooldown-title.teleport-title"));
                                String subtitle = ChatColor.translateAlternateColorCodes('&', config.getString("titles.cooldown-title.teleport-subtitle"));

                                player.sendTitle(title.replace("%seconds%", String.valueOf(secondsLeft)),
                                        subtitle.replace("%seconds%", String.valueOf(secondsLeft)), 10, 20, 10);

                                if (--secondsLeft <= 0) this.cancel();
                            }
                        };

                        countdownTitle.runTaskTimer(plugin, 0L, 20L);
                        teleportCountdownTasks.put(player, countdownTitle);
                    } else if (config.getBoolean("titles.static-title.enable")) {
                        player.sendTitle(
                                ChatColor.translateAlternateColorCodes('&', config.getString("titles.static-title.teleport-title")),
                                ChatColor.translateAlternateColorCodes('&', config.getString("titles.static-title.teleport-subtitle")),
                                10, cooldownSeconds * 20, 10
                        );
                    }
                }

                BukkitRunnable teleportTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (teleportingPlayers.contains(player)) {
                            teleportPlayerToHome(player, homeName);
                        }
                    }
                };

                teleportTask.runTaskLater(plugin, cooldownTime / 50);
                teleportTasks.put(player, teleportTask);
                Bukkit.getScheduler().runTask(plugin, player::closeInventory);
            }

            // CONFIRMACIÓN DE ELIMINACIÓN (clic derecho)
            if (event.getClick() == ClickType.RIGHT && clickedItem.getType() == Material.RED_BED) {
                String homeName = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "homePosition"), PersistentDataType.STRING);
                if (homeName != null && !homeName.isEmpty()) {
                    pendingHomeNames.put(player, homeName);
                    openConfirmationMenu(player, homeName);
                }
            }
        }

        // MENÚ DE CONFIRMACIÓN ADMIN
        if (event.getView().getTitle().equals(adminConfirmationTitle) && isAdmin) {
            event.setCancelled(true);  // Cancelar el evento para evitar otras interacciones

            // Verifica si el targetMap del admin está vacío antes de proceder
            if (playerTargetMap.isEmpty()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.player-not-selected")));
                player.closeInventory();  // Cierra el inventario en caso de error
                return;
            }

            String confirmName = ChatColor.translateAlternateColorCodes('&', config.getString("confirmation-menu-admin.confirm-item.display-name"));
            String cancelName = ChatColor.translateAlternateColorCodes('&', config.getString("confirmation-menu-admin.cancel-item.display-name"));

            if (itemName.equals(confirmName)) {
                OfflinePlayer target = playerTargetMap.get(player.getUniqueId());

                if (target == null) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.player-not-found")));
                    player.closeInventory();
                    return;
                }

                String homeName = pendingHomeNames.get(player);

                if (homeName == null || homeName.isEmpty()) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-not-found")));
                    player.closeInventory();
                    return;
                }

                File targetFile = new File(plugin.getDataFolder(), "data/" + target.getUniqueId() + ".yml");
                YamlConfiguration targetConfig = YamlConfiguration.loadConfiguration(targetFile);

                List<String> homes = targetConfig.getStringList("homes");

                if (homes.contains(homeName)) {
                    homes.remove(homeName);
                    targetConfig.set("homes", homes);
                    targetConfig.set("homeData." + homeName, null);

                    try {
                        targetConfig.save(targetFile);
                        String message = ChatColor.translateAlternateColorCodes('&', config.getString("messages.admin-home-removed"))
                                .replace("%home%", homeName)
                                .replace("%player%", target.getName());
                        player.sendMessage(message);
                        Bukkit.getScheduler().runTask(plugin, player::closeInventory);
                    } catch (IOException e) {
                        e.printStackTrace();
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.saving-error")));
                    }
                } else {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-not-found")));
                    player.closeInventory();
                }

                playerTargetMap.remove(player.getUniqueId());
                pendingHomeNames.remove(player);
            }
            if (itemName.equals(cancelName)) {
                playerTargetMap.remove(player.getUniqueId());  // Limpiar el mapa
                pendingHomeNames.remove(player);  // Limpiar los hogares pendientes
                player.closeInventory();  // Cierra el inventario si el usuario cancela
            }
        }

        // MENÚ DE CONFIRMACIÓN (Jugador)
        else if (event.getView().getTitle().equals(confirmationMenuTitle)) {
            event.setCancelled(true);

            // Verifica si el targetMap del jugador está vacío antes de proceder
            if (pendingHomeNames.isEmpty()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-not-found")));
                player.closeInventory();
                return;
            }

            String confirmName = ChatColor.translateAlternateColorCodes('&', config.getString("confirmation-menu.confirm-item.display-name"));
            String cancelName = ChatColor.translateAlternateColorCodes('&', config.getString("confirmation-menu.cancel-item.display-name"));

            if (itemName.equals(confirmName)) {
                String homeName = pendingHomeNames.get(player);
                homeName = homeName.trim();  // Elimina espacios extra al principio y al final
                if (homeName == null || homeName.isEmpty()) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-not-found")));
                    player.closeInventory();
                    return;
                }

                File playerFile = new File(plugin.getDataFolder(), "data/" + player.getUniqueId() + ".yml");
                YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

                // Obtener la lista de hogares del jugador
                List<String> homes = playerConfig.getStringList("homes");

                // Verifica si el hogar existe en la lista
                if (!homes.contains(homeName.toLowerCase())) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-not-found")));
                    player.closeInventory();
                    return;
                }

                // Eliminar el hogar de la lista de nombres
                homes.remove(homeName);
                playerConfig.set("homes", homes);

                // Eliminar los detalles del hogar
                playerConfig.set("homeData." + homeName, null);

                try {
                    playerConfig.save(playerFile);
                    String message = ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-removed"))
                            .replace("%home%", homeName);
                    player.sendMessage(message);
                } catch (IOException e) {
                    e.printStackTrace();
                    player.sendMessage(ChatColor.RED + "Error al guardar los cambios.");
                }

                pendingHomeNames.remove(player);
                player.closeInventory();
            }

            if (itemName.equals(cancelName)) {
                pendingHomeNames.remove(player);
                player.closeInventory();
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
            float yaw = (float) playerConfig.getDouble(homeName + ".yaw");
            float pitch = (float) playerConfig.getDouble(homeName + ".pitch");

            Location homeLocation = new Location(world, x, y, z, yaw, pitch);
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
        Inventory confirmationMenu = Bukkit.createInventory(new PlayerMenuHolder(), 9, ChatColor.translateAlternateColorCodes('&', config.getString("confirmation-menu.gui-title")));

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
                    player.sendTitle("", "", 0, 0, 0);
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

    public void openMainMenu(Player player) {
        FileConfiguration config = plugin.getConfig();
        String menuTitlePath = "menu.gui-title";

        Inventory menu = Bukkit.createInventory(new PlayerMenuHolder(), 27, ChatColor.translateAlternateColorCodes('&', config.getString(menuTitlePath)));

        // Bordes
        String borderGlassColorName = config.getString("menu.glass-pane-color", "GRAY").toUpperCase();
        Material borderGlassMaterial = Material.matchMaterial(borderGlassColorName + "_STAINED_GLASS_PANE");
        if (borderGlassMaterial == null) borderGlassMaterial = Material.GRAY_STAINED_GLASS_PANE;
        ItemStack borderGlassPane = new ItemStack(borderGlassMaterial);
        ItemMeta borderGlassMeta = borderGlassPane.getItemMeta();
        borderGlassMeta.setDisplayName(" ");
        borderGlassPane.setItemMeta(borderGlassMeta);

        int[] borderSlots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
        for (int slot : borderSlots) {
            menu.setItem(slot, borderGlassPane);
        }

        // Vidrio central
        String centerGlassColorName = config.getString("menu.center-glass-color", "LIGHT_BLUE").toUpperCase();
        Material centerGlassMaterial = Material.matchMaterial(centerGlassColorName + "_STAINED_GLASS_PANE");
        if (centerGlassMaterial == null) centerGlassMaterial = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
        ItemStack centerGlassPane = new ItemStack(centerGlassMaterial);
        ItemMeta centerGlassMeta = centerGlassPane.getItemMeta();
        centerGlassMeta.setDisplayName(" ");
        centerGlassPane.setItemMeta(centerGlassMeta);

        menu.setItem(10, centerGlassPane);
        menu.setItem(12, centerGlassPane);
        menu.setItem(14, centerGlassPane);
        menu.setItem(16, centerGlassPane);

        // Set Home
        String setHomeMaterialName = config.getString("menu.set-home-item.material").toUpperCase();
        Material setHomeMaterial = Material.matchMaterial(setHomeMaterialName);
        if (setHomeMaterial == null) setHomeMaterial = Material.RED_BED;
        ItemStack setHomeItem = new ItemStack(setHomeMaterial);
        ItemMeta setHomeMeta = setHomeItem.getItemMeta();
        setHomeMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("menu.set-home-item.display-name")));
        List<String> setHomeLore = config.getStringList("menu.set-home-item.lore");
        for (int i = 0; i < setHomeLore.size(); i++) {
            setHomeLore.set(i, ChatColor.translateAlternateColorCodes('&', setHomeLore.get(i)));
        }
        setHomeMeta.setLore(setHomeLore);
        setHomeItem.setItemMeta(setHomeMeta);
        menu.setItem(11, setHomeItem);

        // Your Homes
        String homeListMaterialName = config.getString("menu.your-homes-item.material").toUpperCase();
        Material homeListMaterial = Material.matchMaterial(homeListMaterialName);
        if (homeListMaterial == null) homeListMaterial = Material.OAK_DOOR;
        ItemStack homeListItem = new ItemStack(homeListMaterial);
        ItemMeta homeListMeta = homeListItem.getItemMeta();
        homeListMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("menu.your-homes-item.display-name")));
        List<String> homeListLore = config.getStringList("menu.your-homes-item.lore");
        for (int i = 0; i < homeListLore.size(); i++) {
            homeListLore.set(i, ChatColor.translateAlternateColorCodes('&', homeListLore.get(i)));
        }
        homeListMeta.setLore(homeListLore);
        homeListItem.setItemMeta(homeListMeta);
        menu.setItem(15, homeListItem);

        // Admin Menu o Info
        if (player.hasPermission("sethome.admin")) {
            String adminMaterialName = config.getString("admin-menu.material").toUpperCase();
            Material adminMaterial = Material.matchMaterial(adminMaterialName);
            if (adminMaterial == null) adminMaterial = Material.BOOK;
            ItemStack adminItem = new ItemStack(adminMaterial);
            ItemMeta adminMeta = adminItem.getItemMeta();
            adminMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("admin-menu.display-name")));
            List<String> adminMenuLore = config.getStringList("admin-menu.lore");
            for (int i = 0; i < adminMenuLore.size(); i++) {
                adminMenuLore.set(i, ChatColor.translateAlternateColorCodes('&', adminMenuLore.get(i)));
            }
            adminMeta.setLore(adminMenuLore);
            adminItem.setItemMeta(adminMeta);
            menu.setItem(13, adminItem);
        } else {
            ItemStack infoItem = new ItemStack(Material.NETHER_STAR);
            ItemMeta infoMeta = infoItem.getItemMeta();
            infoMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("menu.info-title")));
            infoItem.setItemMeta(infoMeta);
            menu.setItem(13, infoItem);
        }

        player.openInventory(menu);
    }


    private void openYourHomesInventory(Player player, int page) {
        player.closeInventory();

        FileConfiguration config = plugin.getConfig();
        int size = config.getInt("homes-menu.size");

        File dataFolder = new File(plugin.getDataFolder(), "data");
        File playerFile = new File(dataFolder, player.getUniqueId() + ".yml");
        YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

        List<String> homes = playerConfig.getStringList("homes");

        int itemsPerPage = size - 9; // Excluyendo la última fila
        int totalPages = (int) Math.ceil((double) homes.size() / itemsPerPage);
        if (totalPages == 0) totalPages = 1; // Asegurar al menos una página

        // Reemplazar %page% y %maxpages% en el título
        String rawTitle = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("homes-menu.gui-title"))
                .replace("%page%", String.valueOf(page + 1)) // página empieza en 0
                .replace("%maxpages%", String.valueOf(totalPages));

        Inventory homesMenu = Bukkit.createInventory(new PlayerMenuHolder(), config.getInt("homes-menu.size"), rawTitle);


        if (homes.isEmpty()) {
            // Ítem informativo si no hay hogares
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

            homesMenu.setItem(22, noHomesItem);
        } else {
            int start = page * itemsPerPage;
            int end = Math.min(start + itemsPerPage, homes.size());

            for (int i = start; i < end; i++) {
                String home = homes.get(i);
                if (!playerConfig.contains(home)) continue;

                String world = playerConfig.getString(home + ".world");
                double x = playerConfig.getDouble(home + ".x");
                double y = playerConfig.getDouble(home + ".y");
                double z = playerConfig.getDouble(home + ".z");

                if (world == null) continue;

                ItemStack bedItem = new ItemStack(Material.RED_BED);
                ItemMeta bedMeta = bedItem.getItemMeta();

                String homeNamePath = config.getString("homes-menu.home-item.display-name");
                if (homeNamePath != null) {
                    String homeNameItem = ChatColor.translateAlternateColorCodes('&', homeNamePath).replace("%home%", home);
                    bedMeta.setDisplayName(homeNameItem);
                }

                bedMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "homePosition"), PersistentDataType.STRING, home);

                List<String> lore = config.getStringList("homes-menu.home-item.lore");
                if (lore != null) {
                    for (int j = 0; j < lore.size(); j++) {
                        lore.set(j, ChatColor.translateAlternateColorCodes('&', lore.get(j)
                                .replace("%home%", home)
                                .replace("%world%", world)
                                .replace("%x%", String.valueOf(x))
                                .replace("%y%", String.valueOf(y))
                                .replace("%z%", String.valueOf(z))));
                    }
                    bedMeta.setLore(lore);
                }

                bedItem.setItemMeta(bedMeta);
                homesMenu.addItem(bedItem);
            }
        }

        // Línea inferior (última fila)
        for (int i = size - 9; i < size; i++) {
            ItemStack glassPane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta meta = glassPane.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&f"));
                glassPane.setItemMeta(meta);
            }
            homesMenu.setItem(i, glassPane);
        }

        // Botón volver
        ItemStack goBack = new ItemStack(Material.ARROW);
        ItemMeta goBackMeta = goBack.getItemMeta();
        if (goBackMeta != null) {
            String goBackPath = config.getString("homes-menu.go-back-item");
            goBackMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', goBackPath));
            goBack.setItemMeta(goBackMeta);
        }
        homesMenu.setItem(size - 9 + 3, goBack);

        // Botón cerrar
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            String closePath = config.getString("homes-menu.close-item");
            closeMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', closePath));
            close.setItemMeta(closeMeta);
        }
        homesMenu.setItem(size - 9 + 4, close);

        // Botón página anterior
        if (page > 0) {
            ItemStack previousPage = new ItemStack(Material.PAPER);
            ItemMeta meta = previousPage.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("homes-menu.previous-page-item")));
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "menuPage"), PersistentDataType.INTEGER, page - 1);
                previousPage.setItemMeta(meta);
            }
            homesMenu.setItem(size - 9 + 1, previousPage);
        }

        // Botón página siguiente
        if (page < totalPages - 1) {
            ItemStack nextPage = new ItemStack(Material.PAPER);
            ItemMeta meta = nextPage.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("homes-menu.next-page-item")));
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "menuPage"), PersistentDataType.INTEGER, page + 1);
                nextPage.setItemMeta(meta);
            }
            homesMenu.setItem(size - 9 + 7, nextPage);
        }

        player.openInventory(homesMenu);
    }


    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        FileConfiguration config = plugin.getConfig();

        if (!pendingHomeNames.containsKey(player)) return;

        String message = event.getMessage();

        if (message.equalsIgnoreCase("cancel")) {
            pendingHomeNames.remove(player);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-cancelled")));
            event.setCancelled(true);
            return;
        }

        String homeName = message;

        File dataFolder = new File(plugin.getDataFolder(), "data");
        File playerFile = new File(dataFolder, player.getUniqueId() + ".yml");
        YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

        if (playerConfig.contains(homeName)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-exists")));
            event.setCancelled(true);
            return;
        }

        int maxHomes = getMaxHomesForPlayer(player);
        List<String> homes = playerConfig.getStringList("homes");

        if (homes.size() >= maxHomes) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    config.getString("messages.home-limit-reached").replace("%limit%", String.valueOf(maxHomes))));

            pendingHomeNames.remove(player);

            event.setCancelled(true);
            return;
        }

        Location loc = player.getLocation();
        String worldName = loc.getWorld().getName();

        homes.add(homeName);
        playerConfig.set("homes", homes);
        playerConfig.set(homeName + ".world", worldName);
        playerConfig.set(homeName + ".x", loc.getX());
        playerConfig.set(homeName + ".y", loc.getY());
        playerConfig.set(homeName + ".z", loc.getZ());
        playerConfig.set(homeName + ".yaw", loc.getYaw());
        playerConfig.set(homeName + ".pitch", loc.getPitch());

        try {
            playerConfig.save(playerFile);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', config.getString("messages.home-established")));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Eliminar del mapa inmediatamente
        pendingHomeNames.remove(player);

        // ✅ Cancelamos el evento para que el mensaje no se muestre en el chat global
        event.setCancelled(true);
    }

    private int getMaxHomesForPlayer(Player player) {
        FileConfiguration config = plugin.getConfig();

        // Valor predeterminado de la configuración
        int defaultMaxHomes = config.getInt("default-max-homes", 3); // Por defecto, 3 hogares
        int maxHomes = 0; // Inicializamos con 0 para priorizar permisos

        // Recorrer los permisos del jugador
        for (PermissionAttachmentInfo permInfo : player.getEffectivePermissions()) {
            String permission = permInfo.getPermission();
            if (permission.startsWith("sethome.maxhomes.")) {
                try {
                    int homes = Integer.parseInt(permission.split("\\.")[2]);
                    // Actualizar solo si es mayor al valor actual
                    if (homes > maxHomes) {
                        maxHomes = homes;
                    }
                } catch (NumberFormatException e) {
                    // Ignoramos los permisos mal formados
                    e.printStackTrace();
                }
            }
        }

        // Si no se encontró ningún permiso relevante, usar el valor por defecto
        if (maxHomes == 0) {
            maxHomes = defaultMaxHomes;
        }

        return maxHomes;
    }

    public void openAdminMenu(Player player, int page) {
        player.closeInventory();

        FileConfiguration config = plugin.getConfig();
        int size = config.getInt("admin-menu.size", 54); // Asegúrate que sea múltiplo de 9 y al menos 18

        // Obtener lista de jugadores
        List<OfflinePlayer> players = Arrays.asList(Bukkit.getOfflinePlayers());
        players.sort(Comparator.comparing(OfflinePlayer::getName, String.CASE_INSENSITIVE_ORDER));

        int itemsPerPage = size - 9;
        int totalPages = (int) Math.ceil((double) players.size() / itemsPerPage);
        if (totalPages == 0) totalPages = 1;

        String title = ChatColor.translateAlternateColorCodes('&',
                config.getString("admin-menu.gui-title", "&8Admin Menu - Page %page%/%maxpages%")
        ).replace("%page%", String.valueOf(page + 1)).replace("%maxpages%", String.valueOf(totalPages));

        Inventory menu = Bukkit.createInventory(new PlayerMenuHolder(), size, title);

        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, players.size());

        for (int i = start; i < end; i++) {
            OfflinePlayer target = players.get(i);
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();

            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName(ChatColor.YELLOW + target.getName());

                List<String> lore = config.getStringList("admin-menu.player-head-lore");
                lore.replaceAll(line -> ChatColor.translateAlternateColorCodes('&',
                        line.replace("%player%", target.getName())
                ));
                meta.setLore(lore);

                meta.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, "adminTarget"),
                        PersistentDataType.STRING,
                        target.getUniqueId().toString()
                );
                skull.setItemMeta(meta);
            }

            menu.addItem(skull);
        }

        // Última fila - fondo
        for (int i = size - 9; i < size; i++) {
            ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta meta = glass.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(" ");
                glass.setItemMeta(meta);
            }
            menu.setItem(i, glass);
        }

        // Botón volver
        ItemStack goBack = new ItemStack(Material.ARROW);
        ItemMeta goBackMeta = goBack.getItemMeta();
        if (goBackMeta != null) {
            String goBackPath = config.getString("admin-menu.go-back-item");
            goBackMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', goBackPath));
            goBack.setItemMeta(goBackMeta);
        }
        menu.setItem(size - 9 + 3, goBack);

        // Botón cerrar
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            String closePath = config.getString("homes-menu.close-item");
            closeMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', closePath));
            close.setItemMeta(closeMeta);
        }
        menu.setItem(size - 9 + 4, close);

        // Página anterior
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.PAPER);
            ItemMeta prevMeta = prev.getItemMeta();
            prevMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("admin-menu.previous-page-item", "&7Página anterior")));
            prevMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "adminPage"), PersistentDataType.INTEGER, page - 1);
            prev.setItemMeta(prevMeta);
            menu.setItem(size - 9 + 1, prev);
        }

        // Página siguiente
        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Material.PAPER);
            ItemMeta nextMeta = next.getItemMeta();
            nextMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("admin-menu.next-page-item", "&7Página siguiente")));
            nextMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "adminPage"), PersistentDataType.INTEGER, page + 1);
            next.setItemMeta(nextMeta);
            menu.setItem(size - 9 + 7, next);
        }

        player.openInventory(menu);
    }

    public void openPlayerHomesInventory(Player admin, OfflinePlayer target, int page) {
        admin.closeInventory();

        FileConfiguration config = plugin.getConfig();
        int size = config.getInt("homes-menu.size");

        File dataFolder = new File(plugin.getDataFolder(), "data");
        File playerFile = new File(dataFolder, target.getUniqueId() + ".yml");
        YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

        List<String> homes = playerConfig.getStringList("homes");

        int itemsPerPage = size - 9; // Excluyendo la última fila
        int totalPages = (int) Math.ceil((double) homes.size() / itemsPerPage);
        if (totalPages == 0) totalPages = 1; // Asegurar al menos una página

        // Título con paginación
        String rawTitle = ChatColor.translateAlternateColorCodes('&',
                        config.getString("admin-menu.admin-gui-title"))
                .replace("%page%", String.valueOf(page + 1))
                .replace("%maxpages%", String.valueOf(totalPages))
                .replace("%player%", String.valueOf(target.getName()));

        Inventory homesMenu = Bukkit.createInventory(new PlayerMenuHolder(), size, rawTitle);

        if (homes.isEmpty()) {
            // Ítem informativo si no hay hogares
            ItemStack noHomesItem = new ItemStack(Material.PAPER);
            ItemMeta noHomesMeta = noHomesItem.getItemMeta();
            if (noHomesMeta != null) {
                String displayName = config.getString("homes-menu.no-homes-item.display-name");
                List<String> lore = config.getStringList("homes-menu.no-homes-item.lore");
                noHomesMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
                lore.replaceAll(line -> ChatColor.translateAlternateColorCodes('&', line));
                noHomesMeta.setLore(lore);
                noHomesItem.setItemMeta(noHomesMeta);
            }
            homesMenu.setItem(22, noHomesItem);
        } else {
            int start = page * itemsPerPage;
            int end = Math.min(start + itemsPerPage, homes.size());

            for (int i = start; i < end; i++) {
                String home = homes.get(i);
                if (!playerConfig.contains(home)) continue;

                String world = playerConfig.getString(home + ".world");
                double x = playerConfig.getDouble(home + ".x");
                double y = playerConfig.getDouble(home + ".y");
                double z = playerConfig.getDouble(home + ".z");
                if (world == null) continue;

                ItemStack bedItem = new ItemStack(Material.RED_BED);
                ItemMeta bedMeta = bedItem.getItemMeta();

                String homeNamePath = config.getString("homes-menu.home-item.display-name");
                bedMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', homeNamePath).replace("%home%", home));

                bedMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "adminHomeName"), PersistentDataType.STRING, home);
                bedMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "adminHomeTarget"), PersistentDataType.STRING, target.getUniqueId().toString());

                List<String> lore = config.getStringList("homes-menu.home-item.lore");
                for (int j = 0; j < lore.size(); j++) {
                    lore.set(j, ChatColor.translateAlternateColorCodes('&', lore.get(j)
                            .replace("%home%", home)
                            .replace("%world%", world)
                            .replace("%x%", String.valueOf(x))
                            .replace("%y%", String.valueOf(y))
                            .replace("%z%", String.valueOf(z))));
                }
                bedMeta.setLore(lore);

                bedItem.setItemMeta(bedMeta);
                homesMenu.addItem(bedItem);
            }
        }

        // Decoración de la última fila (pantalla inferior)
        for (int i = size - 9; i < size; i++) {
            ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta meta = pane.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(" ");
                pane.setItemMeta(meta);
            }
            homesMenu.setItem(i, pane);
        }

        // Botón volver
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            String backPath = config.getString("homes-menu.go-back-item");
            backMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', backPath));
            back.setItemMeta(backMeta);
        }
        homesMenu.setItem(size - 9 + 3, back);

        // Botón cerrar
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            String closePath = config.getString("homes-menu.close-item");
            closeMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', closePath));
            close.setItemMeta(closeMeta);
        }
        homesMenu.setItem(size - 9 + 4, close);

        // Botón página anterior
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.PAPER);
            ItemMeta meta = prev.getItemMeta();
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("homes-menu.previous-page-item")));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "adminHomesPage"), PersistentDataType.INTEGER, page - 1);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "adminHomeTarget"), PersistentDataType.STRING, target.getUniqueId().toString());
            prev.setItemMeta(meta);
            homesMenu.setItem(size - 9 + 1, prev);
        }

        // Botón página siguiente
        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Material.PAPER);
            ItemMeta meta = next.getItemMeta();
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString("homes-menu.next-page-item")));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "adminHomesPage"), PersistentDataType.INTEGER, page + 1);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "adminHomeTarget"), PersistentDataType.STRING, target.getUniqueId().toString());
            next.setItemMeta(meta);
            homesMenu.setItem(size - 9 + 7, next);
        }

        admin.openInventory(homesMenu);
    }

    @EventHandler
    public void onAdminInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof PlayerMenuHolder) {
            event.setCancelled(true);
            Player admin = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem == null || !clickedItem.hasItemMeta()) return;

            ItemMeta meta = clickedItem.getItemMeta();
            NamespacedKey pageKey = new NamespacedKey(plugin, "adminHomesPage");

            // Cambio de página
            if (meta.getPersistentDataContainer().has(pageKey, PersistentDataType.INTEGER)) {
                int newPage = meta.getPersistentDataContainer().get(pageKey, PersistentDataType.INTEGER);
                String targetUUID = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "adminHomeTarget"), PersistentDataType.STRING);
                OfflinePlayer target = Bukkit.getOfflinePlayer(UUID.fromString(targetUUID));
                openPlayerHomesInventory(admin, target, newPage);
                return;
            }

            // Botones volver/cerrar
            if (meta.getDisplayName().equals(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("homes-menu.go-back-item")))) {
                admin.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> openMainMenu(admin), 1);
                return;
            }

            if (meta.getDisplayName().equals(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("homes-menu.close-item")))) {
                admin.closeInventory();
                return;
            }

            // Teletransportación (clic izquierdo)
            if (event.getClick() == ClickType.LEFT && clickedItem.getType() == Material.RED_BED && isAdmin) {
                String homeName = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "adminHomeName"), PersistentDataType.STRING);
                if (homeName == null) return;

                String targetUUID = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "adminHomeTarget"), PersistentDataType.STRING);
                OfflinePlayer target = Bukkit.getOfflinePlayer(UUID.fromString(targetUUID));

                teleportPlayerToHome(admin, target, homeName);
                return;
            }

            // Eliminar hogar (clic derecho)
            if (event.getClick() == ClickType.RIGHT && clickedItem.getType() == Material.RED_BED) {
                String homeName = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "adminHomeName"), PersistentDataType.STRING);
                String targetUUID = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "adminHomeTarget"), PersistentDataType.STRING);
                OfflinePlayer target = Bukkit.getOfflinePlayer(UUID.fromString(targetUUID));

                if (homeName != null) {
                    openAdminConfirmationMenu(admin, target, homeName);
                }
            }
        }
    }

    public void teleportPlayerToHome(Player admin, OfflinePlayer target, String homeName) {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        File playerFile = new File(dataFolder, target.getUniqueId() + ".yml");
        YamlConfiguration playerConfig = YamlConfiguration.loadConfiguration(playerFile);

        if (!playerConfig.contains(homeName)) {
            String msg = plugin.getConfig().getString("messages.player-home-not-exist");
            if (msg != null) {
                msg = ChatColor.translateAlternateColorCodes('&', msg)
                        .replace("%home%", homeName)
                        .replace("%player%", target.getName());
                admin.sendMessage(msg);
            }
            return;
        }


        String world = playerConfig.getString(homeName + ".world");
        double x = playerConfig.getDouble(homeName + ".x");
        double y = playerConfig.getDouble(homeName + ".y");
        double z = playerConfig.getDouble(homeName + ".z");

        if (world == null) {
            admin.sendMessage(ChatColor.translateAlternateColorCodes('&', playerConfig.getString("messages.invalid-location")));
            return;
        }

        World targetWorld = Bukkit.getServer().getWorld(world);
        if (targetWorld == null) {
            admin.sendMessage(ChatColor.translateAlternateColorCodes('&', playerConfig.getString("messages.world-not-exist")));
            return;
        }

        Location homeLocation = new Location(targetWorld, x, y, z);
        admin.teleport(homeLocation);

        // Mensaje desde config
        String message = plugin.getConfig().getString("messages.teleported-other-home");

        message = ChatColor.translateAlternateColorCodes('&', message)
                .replace("%home%", homeName)
                .replace("%player_home%", target.getName());

        admin.sendMessage(message);
    }

    public void openAdminConfirmationMenu(Player admin, OfflinePlayer target, String homeName) {
        FileConfiguration config = plugin.getConfig();

        Inventory confirmMenu = Bukkit.createInventory(new PlayerMenuHolder(), 9,
                ChatColor.translateAlternateColorCodes('&', config.getString("confirmation-menu-admin.gui-title")));

        // Crear ítem de confirmación
        ItemStack confirmItem = new ItemStack(Material.GREEN_WOOL);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                config.getString("confirmation-menu-admin.confirm-item.display-name")));
        confirmMeta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "homeName"),
                PersistentDataType.STRING,
                homeName
        );
        confirmItem.setItemMeta(confirmMeta);

        // Crear ítem de cancelación
        ItemStack cancelItem = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                config.getString("confirmation-menu-admin.cancel-item.display-name")));
        cancelItem.setItemMeta(cancelMeta);

        // Agregar ítems al inventario
        confirmMenu.setItem(3, confirmItem);
        confirmMenu.setItem(5, cancelItem);

        // Abrir menú
        admin.openInventory(confirmMenu);

        // Guardar jugador objetivo
        playerTargetMap.put(admin.getUniqueId(), target);
        pendingHomeNames.put(admin, homeName); // <-- Añadir esta línea
    }
}

