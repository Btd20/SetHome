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

public class AdminGUIManager {

    private final SetHomeGUI plugin;
    private final Map<UUID, Integer> adminPage = new HashMap<>();
    private final Map<UUID, String> searchFilters = new HashMap<>();

    public final NamespacedKey actionKey;
    public final NamespacedKey targetUuidKey;
    public final NamespacedKey targetHomeKey;

    public AdminGUIManager(SetHomeGUI plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "adm_action");
        this.targetUuidKey = new NamespacedKey(plugin, "adm_target_uuid");
        this.targetHomeKey = new NamespacedKey(plugin, "adm_target_home");
    }

    public void openAdminMenu(Player player) {
        UUID uuid = player.getUniqueId();
        YamlDocument config = plugin.getGuisConfig();
        Section section = config.getSection("gui.admin-gui");
        if (section == null) return;

        String unknownNameConfig = section.getString("unknown-player-name", "&7Unknown");
        int currentPage = adminPage.getOrDefault(uuid, 1);
        String filter = searchFilters.getOrDefault(uuid, "").toLowerCase();

        File dataDir = new File(plugin.getDataFolder(), "data");
        List<OfflinePlayer> registeredPlayers = new ArrayList<>();

        if (dataDir.exists() && dataDir.isDirectory()) {
            File[] files = dataDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    try {
                        UUID pUuid = UUID.fromString(file.getName().replace(".yml", ""));
                        OfflinePlayer op = Bukkit.getOfflinePlayer(pUuid);
                        String name = op.getName() != null ? op.getName() : ChatColor.stripColor(Utils.color(unknownNameConfig));

                        if (!filter.isEmpty() && !name.toLowerCase().contains(filter)) continue;
                        registeredPlayers.add(op);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }

        registeredPlayers.sort(Comparator.comparing(op -> op.getName() != null ? op.getName() : ""));

        List<Integer> slots = section.getIntList("slots");
        int itemsPerPage = slots.size();
        if (itemsPerPage == 0) return;

        int maxPages = (int) Math.ceil((double) registeredPlayers.size() / itemsPerPage);
        if (maxPages == 0) maxPages = 1;

        if (currentPage > maxPages) {
            currentPage = maxPages;
            adminPage.put(uuid, currentPage);
        }

        String titleStr = section.getString("title", "Admin Menu")
                .replace("%page%", String.valueOf(currentPage))
                .replace("%max_page%", String.valueOf(maxPages));

        int size = section.getInt("size", 54);

        // Optimización anti-reset de cursor: Reutilizar inventario abierto si corresponde
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
        if (headTemplate != null && !registeredPlayers.isEmpty()) {
            int startIndex = (currentPage - 1) * itemsPerPage;
            for (int i = 0; i < itemsPerPage; i++) {
                int globalIndex = startIndex + i;
                if (globalIndex >= registeredPlayers.size()) break;

                OfflinePlayer target = registeredPlayers.get(globalIndex);
                int headSlot = slots.get(i);

                ItemStack head = Utils.getPlayerHead(target.getUniqueId());
                ItemMeta meta = head.getItemMeta();
                if (meta != null) {
                    String pName = target.getName() != null ? target.getName() : Utils.color(unknownNameConfig);
                    meta.setDisplayName(Utils.color(headTemplate.getString("display-name", "").replace("%player_name%", pName)));

                    int totalHomes = plugin.getHomeManager().getHomeCount(target.getUniqueId());

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

        File playerFile = new File(plugin.getDataFolder() + "/data", targetUuid.toString() + ".yml");
        if (!playerFile.exists()) {
            // Leemos el mensaje desde la sección admin con un fallback seguro
            String noHomesMsg = plugin.getMainConfig().getString(
                    "messages.admin.no-homes-found",
                    "&cThis player does not have any homes configurations."
            );

            // Enviamos el mensaje aplicando la traducción de colores del plugin
            admin.sendMessage(Utils.color(noHomesMsg));
            return;
        }

        admin.getScheduler().run(plugin, (task) -> {
            try {
                dev.dejvokep.boostedyaml.YamlDocument targetData = dev.dejvokep.boostedyaml.YamlDocument.create(playerFile);
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
                // Leemos el mensaje desde la sección admin con un fallback idéntico a tu cadena original
                String recordErrorMsg = plugin.getMainConfig().getString(
                        "messages.admin.player-record-error",
                        "&cError processing player record."
                );

                // Enviamos el mensaje aplicando la paleta de colores hexadecimales y tradicionales
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
        // Cerramos el menú actual para que el jugador pueda ver el chat claramente
        player.closeInventory();

        // Obtenemos la lista de mensajes configurada desde el config.yml
        List<String> promptLines = plugin.getConfig().getStringList("messages.admin.search-prompt");

        // Recorremos cada línea configurada y la enviamos coloreada
        for (String line : promptLines) {
            player.sendMessage(Utils.color(line));
        }

        // Registramos al jugador en los metadatos de conversación de la sesión
        player.setMetadata("admin_search_mode", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
    }

    public int getPage(UUID uuid) { return adminPage.getOrDefault(uuid, 1); }
    public void setPage(UUID uuid, int page) { adminPage.put(uuid, page); }
    public void setSearchFilter(UUID uuid, String filter) { searchFilters.put(uuid, filter); }
}