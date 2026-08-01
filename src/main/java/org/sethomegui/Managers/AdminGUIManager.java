package org.sethomegui.Managers;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.sethomegui.SetHomeGUI;
import org.sethomegui.Utils.Utils;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class AdminGUIManager {

    private final SetHomeGUI plugin;
    private final Map<UUID, Integer> adminPage = new HashMap<>();
    private final Map<UUID, String> searchFilters = new HashMap<>();

    // Estructuras thread-safe para que Folia maneje la caché sin excepciones de concurrencia
    private final List<OfflinePlayer> cachedPlayers = new CopyOnWriteArrayList<>();
    private final Map<UUID, String> cachedNames = new ConcurrentHashMap<>();
    // Numero de hogares por jugador, precalculado en segundo plano: sin esto, pintar una
    // pagina del panel lanzaria una consulta a la base de datos por cada cabeza mostrada.
    private final Map<UUID, Integer> cachedHomeCounts = new ConcurrentHashMap<>();

    public final NamespacedKey actionKey;
    public final NamespacedKey targetUuidKey;
    public final NamespacedKey targetHomeKey;

    public AdminGUIManager(SetHomeGUI plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "adm_action");
        this.targetUuidKey = new NamespacedKey(plugin, "adm_target_uuid");
        this.targetHomeKey = new NamespacedKey(plugin, "adm_target_home");
    }

    /**
     * Carga de forma asíncrona todos los archivos del directorio de datos a la memoria RAM.
     * Invócalo en el onEnable() de tu clase principal (SetHomeGUI.java).
     */
    public void loadPlayersCacheAsync() {
        Bukkit.getAsyncScheduler().runNow(plugin, (task) -> {
            long startTime = System.currentTimeMillis();

            // Preguntamos al backend activo (archivos YAML o MySQL) en lugar de escanear el disco
            List<OfflinePlayer> tempPlayers = new ArrayList<>();
            for (UUID pUuid : plugin.getStorageManager().listPlayers()) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(pUuid);
                tempPlayers.add(op);

                String name = op.getName();
                if (name != null) {
                    cachedNames.put(pUuid, name.toLowerCase());
                }
            }

            // Ordenación alfabética inicial en segundo plano
            tempPlayers.sort(Comparator.comparing(op -> op.getName() != null ? op.getName() : ""));

            cachedPlayers.clear();
            cachedPlayers.addAll(tempPlayers);

            cachedHomeCounts.clear();
            cachedHomeCounts.putAll(plugin.getStorageManager().countHomesByPlayer());

            plugin.getLogger().info("Loaded " + cachedPlayers.size() + " admin records into memory cache (" + (System.currentTimeMillis() - startTime) + "ms).");
        });
    }

    /**
     * Registra dinámicamente un jugador en la caché cuando guarda un home por primera vez.
     */
    public void registerPlayerInCache(OfflinePlayer player) {
        // El recuento cambia cada vez que se crea un hogar, asi que lo refrescamos siempre
        cachedHomeCounts.put(player.getUniqueId(), plugin.getHomeManager().getHomeCount(player.getUniqueId()));

        if (!cachedPlayers.contains(player)) {
            cachedPlayers.add(player);
            if (player.getName() != null) {
                cachedNames.put(player.getUniqueId(), player.getName().toLowerCase());
            }
            Bukkit.getAsyncScheduler().runNow(plugin, (task) -> {
                cachedPlayers.sort(Comparator.comparing(op -> op.getName() != null ? op.getName() : ""));
            });
        }
    }

    /**
     * Remueve un jugador de la caché en memoria si ya no tiene hogares guardados.
     */
    public void removePlayerFromCacheIfEmpty(OfflinePlayer player) {
        int remaining = plugin.getHomeManager().getHomeCount(player.getUniqueId());
        if (remaining <= 0) {
            cachedPlayers.remove(player);
            cachedNames.remove(player.getUniqueId());
            cachedHomeCounts.remove(player.getUniqueId());
        } else {
            cachedHomeCounts.put(player.getUniqueId(), remaining);
        }
    }

    public void openAdminMenu(Player player) {
        UUID uuid = player.getUniqueId();
        YamlDocument config = plugin.getGuisConfig();
        Section section = config.getSection("gui.admin-gui");
        if (section == null) return;

        String unknownNameConfig = section.getString("unknown-player-name", "&7Unknown");
        int currentPage = adminPage.getOrDefault(uuid, 1);
        String filter = searchFilters.getOrDefault(uuid, "").toLowerCase();

        // Lectura limpia e instantánea desde la RAM
        List<OfflinePlayer> filteredPlayers = new ArrayList<>();
        for (OfflinePlayer op : cachedPlayers) {
            if (!filter.isEmpty()) {
                String name = cachedNames.get(op.getUniqueId());
                if (name == null || !name.contains(filter)) continue;
            }
            filteredPlayers.add(op);
        }

        List<Integer> slots = section.getIntList("slots");
        int itemsPerPage = slots.size();
        if (itemsPerPage == 0) return;

        int maxPages = (int) Math.ceil((double) filteredPlayers.size() / itemsPerPage);
        if (maxPages == 0) maxPages = 1;

        if (currentPage > maxPages) {
            currentPage = maxPages;
            adminPage.put(uuid, currentPage);
        }

        String titleStr = section.getString("title", "Admin Menu")
                .replace("%page%", String.valueOf(currentPage))
                .replace("%max_page%", String.valueOf(maxPages));

        int size = section.getInt("size", 54);

        Inventory gui;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof AdminMainHolder &&
                player.getOpenInventory().getTopInventory().getSize() == size) {
            gui = player.getOpenInventory().getTopInventory();
            gui.clear();
        } else {
            gui = Bukkit.createInventory(new AdminMainHolder(), size, Utils.color(titleStr));
        }

        Section itemsSec = section.getSection("items");
        if (itemsSec != null) {
            for (Object keyObj : itemsSec.getKeys()) {
                String key = String.valueOf(keyObj);
                if (key.equalsIgnoreCase("player-head")) continue;
                Section itemData = itemsSec.getSection(key);
                if (itemData == null) continue;

                ItemStack item;
                if (itemData.contains("value")) {
                    item = Utils.getHeadFromBase64(itemData.getString("value"));
                } else {
                    Material mat = Material.matchMaterial(itemData.getString("type", "STONE"));
                    item = new ItemStack(mat != null ? mat : Material.STONE);
                }

                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(Utils.color(itemData.getString("display-name", "")));
                    if (itemData.contains("lore")) {
                        List<String> lore = new ArrayList<>();
                        for (String line : itemData.getStringList("lore")) lore.add(Utils.color(line));
                        meta.setLore(lore);
                    }

                    if (key.equalsIgnoreCase("search-button")) {
                        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "search");
                    } else if (key.equalsIgnoreCase("previous-page")) {
                        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "prev_page");
                    } else if (key.equalsIgnoreCase("next-page")) {
                        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "next_page");
                    }
                    item.setItemMeta(meta);
                }

                if (itemData.contains("slots")) {
                    for (int s : itemData.getIntList("slots")) gui.setItem(s, item);
                } else if (itemData.contains("slot")) {
                    gui.setItem(itemData.getInt("slot"), item);
                }
            }
        }

        Section headTemplate = section.getSection("items.player-head");
        if (headTemplate != null && !filteredPlayers.isEmpty()) {
            int startIndex = (currentPage - 1) * itemsPerPage;
            for (int i = 0; i < itemsPerPage; i++) {
                int globalIndex = startIndex + i;
                if (globalIndex >= filteredPlayers.size()) break;

                OfflinePlayer target = filteredPlayers.get(globalIndex);
                int headSlot = slots.get(i);

                ItemStack head = Utils.getPlayerHead(target.getUniqueId());
                ItemMeta meta = head.getItemMeta();
                if (meta != null) {
                    String pName = target.getName() != null ? target.getName() : Utils.color(unknownNameConfig);
                    meta.setDisplayName(Utils.color(headTemplate.getString("display-name", "").replace("%player_name%", pName)));

                    // Usamos el recuento precalculado; solo consultamos al backend si falta
                    Integer totalHomes = cachedHomeCounts.get(target.getUniqueId());
                    if (totalHomes == null) {
                        totalHomes = plugin.getHomeManager().getHomeCount(target.getUniqueId());
                        cachedHomeCounts.put(target.getUniqueId(), totalHomes);
                    }

                    List<String> lore = new ArrayList<>();
                    for (String line : headTemplate.getStringList("lore")) {
                        lore.add(Utils.color(line
                                .replace("%player_uuid%", target.getUniqueId().toString())
                                .replace("%current_homes%", String.valueOf(totalHomes))));
                    }
                    meta.setLore(lore);

                    meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "open_player");
                    meta.getPersistentDataContainer().set(targetUuidKey, PersistentDataType.STRING, target.getUniqueId().toString());

                    head.setItemMeta(meta);
                }
                gui.setItem(headSlot, head);
            }
        }

        if (player.getOpenInventory().getTopInventory() != gui) {
            player.openInventory(gui);
        }
    }

    public void openAdminPlayerHomesMenu(Player admin, UUID targetUuid, int page) {
        Section section = plugin.getGuisConfig().getSection("gui.admin-gui.admin-player-homes-gui");
        if (section == null) return;

        // La lectura del backend se hace fuera del hilo del servidor: con MySQL y un jugador
        // desconectado que aun no este en cache, hacerlo en linea congelaria la partida.
        Bukkit.getAsyncScheduler().runNow(plugin, (loadTask) -> {
            YamlDocument loaded = plugin.getHomeManager().getPlayerFile(targetUuid);

            if (loaded == null || loaded.getStringList("homes").isEmpty()) {
                String noHomesMsg = plugin.getMainConfig().getString(
                        "messages.admin.no-homes-found",
                        "&cThis player does not have any homes configurations."
                );
                admin.sendMessage(Utils.color(noHomesMsg));
                return;
            }

            renderAdminPlayerHomesMenu(admin, targetUuid, page, section, loaded);
        });
    }

    /**
     * Pinta el menu una vez los datos ya estan en memoria.
     */
    private void renderAdminPlayerHomesMenu(Player admin, UUID targetUuid, int page,
                                            Section section, YamlDocument targetData) {
        admin.getScheduler().run(plugin, (task) -> {
            try {
                List<String> homeNames = targetData.getStringList("homes");
                if (homeNames == null) homeNames = new ArrayList<>();

                List<Integer> slots = section.getIntList("home-slots");
                int itemsPerPage = slots.size();
                if (itemsPerPage == 0) return;

                int maxPage = (int) Math.ceil((double) homeNames.size() / itemsPerPage);
                if (maxPage == 0) maxPage = 1;
                final int currentPage = Math.min(page, maxPage);
                setPage(admin.getUniqueId(), currentPage);

                String targetName = Bukkit.getOfflinePlayer(targetUuid).getName();
                if (targetName == null) targetName = "Unknown";

                String titleStr = section.getString("title", "&8Homes of %player%")
                        .replace("%player%", targetName)
                        .replace("%page%", String.valueOf(currentPage))
                        .replace("%max_page%", String.valueOf(maxPage));

                int size = section.getInt("size", 54);

                Inventory gui;
                if (admin.getOpenInventory().getTopInventory().getHolder() instanceof AdminHomesHolder &&
                        admin.getOpenInventory().getTopInventory().getSize() == size) {
                    gui = admin.getOpenInventory().getTopInventory();
                    gui.clear();
                } else {
                    gui = Bukkit.createInventory(new AdminHomesHolder(targetUuid), size, Utils.color(titleStr));
                }

                Section itemsSec = section.getSection("items");
                if (itemsSec != null) {
                    for (Object itemKey : itemsSec.getKeys()) {
                        String id = String.valueOf(itemKey);
                        if (id.equals("home-item")) continue;

                        Section itemSec = section.getSection("items." + id);
                        if (itemSec == null) continue;

                        ItemStack item;
                        if (itemSec.contains("value")) {
                            item = Utils.getHeadFromBase64(itemSec.getString("value"));
                        } else {
                            Material mat = Material.matchMaterial(itemSec.getString("type", "BARRIER"));
                            item = new ItemStack(mat != null ? mat : Material.BARRIER);
                        }

                        ItemMeta meta = item.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName(Utils.color(itemSec.getString("display-name", "")));
                            List<String> lore = new ArrayList<>();
                            for (String line : itemSec.getStringList("lore")) lore.add(Utils.color(line));
                            meta.setLore(lore);

                            if (id.equalsIgnoreCase("back")) {
                                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "go_back");
                            } else if (id.equalsIgnoreCase("previous-page")) {
                                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "homes_prev");
                            } else if (id.equalsIgnoreCase("next-page")) {
                                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "homes_next");
                            }
                            item.setItemMeta(meta);
                        }

                        if (itemSec.contains("slot")) {
                            gui.setItem(itemSec.getInt("slot"), item);
                        } else if (itemSec.contains("slots")) {
                            for (int s : itemSec.getIntList("slots")) gui.setItem(s, item);
                        }
                    }
                }

                Section homeTemplate = section.getSection("items.home-item");
                if (homeTemplate != null && !homeNames.isEmpty()) {
                    int startIndex = (currentPage - 1) * itemsPerPage;
                    for (int i = 0; i < itemsPerPage; i++) {
                        int globalIndex = startIndex + i;
                        if (globalIndex >= homeNames.size()) break;

                        String homeName = homeNames.get(globalIndex);
                        Section homeData = targetData.getSection(homeName);
                        if (homeData == null) continue;

                        Material mat = Material.matchMaterial(homeTemplate.getString("type", "CYAN_BED"));
                        ItemStack homeItem = new ItemStack(mat != null ? mat : Material.CYAN_BED);
                        ItemMeta meta = homeItem.getItemMeta();

                        if (meta != null) {
                            meta.setDisplayName(Utils.color(homeTemplate.getString("display-name", "").replace("%home_name%", homeName)));
                            List<String> lore = new ArrayList<>();
                            for (String line : homeTemplate.getStringList("lore")) {
                                String formatted = line
                                        .replace("%home_world%", homeData.getString("world", "world"))
                                        .replace("%home_x%", String.format(java.util.Locale.US, "%.1f", homeData.getDouble("x", 0.0)))
                                        .replace("%home_y%", String.format(java.util.Locale.US, "%.1f", homeData.getDouble("y", 0.0)))
                                        .replace("%home_z%", String.format(java.util.Locale.US, "%.1f", homeData.getDouble("z", 0.0)));
                                lore.add(Utils.color(formatted));
                            }
                            meta.setLore(lore);

                            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "interact_home");
                            meta.getPersistentDataContainer().set(targetUuidKey, PersistentDataType.STRING, targetUuid.toString());
                            meta.getPersistentDataContainer().set(targetHomeKey, PersistentDataType.STRING, homeName);

                            homeItem.setItemMeta(meta);
                        }
                        gui.setItem(slots.get(i), homeItem);
                    }
                }

                if (admin.getOpenInventory().getTopInventory() != gui) {
                    admin.openInventory(gui);
                }
            } catch (Exception e) {
                String recordErrorMsg = plugin.getMainConfig().getString(
                        "messages.admin.player-record-error",
                        "&cError processing player record."
                );
                admin.sendMessage(Utils.color(recordErrorMsg));
                e.printStackTrace();
            }
        }, null);
    }

    public void openAdminConfirmationMenu(Player admin, UUID targetUuid, String homeName) {
        Section section = plugin.getGuisConfig().getSection("gui.admin-gui.admin-confirmation-gui");
        if (section == null) return;

        admin.getScheduler().run(plugin, (task) -> {
            String titleStr = section.getString("title", "Confirm").replace("%home_name%", homeName);
            int size = section.getInt("size", 27);

            Inventory gui;
            if (admin.getOpenInventory().getTopInventory().getHolder() instanceof AdminConfirmHolder &&
                    admin.getOpenInventory().getTopInventory().getSize() == size) {
                gui = admin.getOpenInventory().getTopInventory();
                gui.clear();
            } else {
                gui = Bukkit.createInventory(new AdminConfirmHolder(targetUuid, homeName), size, Utils.color(titleStr));
            }

            Section itemsSec = section.getSection("items");
            if (itemsSec != null) {
                for (Object itemKey : itemsSec.getKeys()) {
                    String id = String.valueOf(itemKey);
                    Section itemSec = section.getSection("items." + id);
                    if (itemSec == null) continue;

                    Material mat = Material.matchMaterial(itemSec.getString("type", "BARRIER"));
                    ItemStack item = new ItemStack(mat != null ? mat : Material.BARRIER);
                    ItemMeta meta = item.getItemMeta();

                    if (meta != null) {
                        meta.setDisplayName(Utils.color(itemSec.getString("display-name", "").replace("%home_name%", homeName)));
                        List<String> lore = new ArrayList<>();
                        for (String line : itemSec.getStringList("lore")) {
                            lore.add(Utils.color(line.replace("%home_name%", homeName)));
                        }
                        meta.setLore(lore);

                        if (id.equalsIgnoreCase("confirm-button")) {
                            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "confirm_wipe");
                        } else if (id.equalsIgnoreCase("cancel-button")) {
                            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "cancel_wipe");
                        }
                        item.setItemMeta(meta);
                    }

                    if (itemSec.contains("slot")) {
                        gui.setItem(itemSec.getInt("slot"), item);
                    } else if (itemSec.contains("slots")) {
                        for (int s : itemSec.getIntList("slots")) gui.setItem(s, item);
                    }
                }
            }

            if (admin.getOpenInventory().getTopInventory() != gui) {
                admin.openInventory(gui);
            }
        }, null);
    }

    public void openSearchChat(Player player) {
        player.closeInventory();
        List<String> promptLines = plugin.getConfig().getStringList("messages.admin.search-prompt");
        for (String line : promptLines) {
            player.sendMessage(Utils.color(line));
        }
        player.setMetadata("admin_search_mode", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
    }

    public int getPage(UUID uuid) { return adminPage.getOrDefault(uuid, 1); }
    public void setPage(UUID uuid, int page) { adminPage.put(uuid, page); }
    public void setSearchFilter(UUID uuid, String filter) { searchFilters.put(uuid, filter); }
}