package org.sethomegui.Managers;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.sethomegui.SetHomeGUI;
import org.sethomegui.Utils.Utils;

import java.util.*;

public class GUIManager {

    private final SetHomeGUI plugin;
    private final Map<UUID, Integer> playerPage = new HashMap<>();
    private final Map<UUID, String> pendingDeletionHome = new HashMap<>();

    public GUIManager(SetHomeGUI plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the main inventory menu
     */
    public void openMainGUI(Player player) {
        YamlDocument config = plugin.getGuisConfig();
        Section mainSection = config.getSection("gui.main-gui");

        if (mainSection == null) {
            return;
        }

        String title = Utils.setPlaceholders(player, mainSection.getString("title", "Menu"), plugin);
        int size = mainSection.getInt("size", 27);
        Inventory gui = Bukkit.createInventory(null, size, title);

        Section itemsSection = mainSection.getSection("items");
        if (itemsSection != null) {
            for (Object keyObj : itemsSection.getKeys()) {
                String key = String.valueOf(keyObj);

                // Special visibility check for the admin item
                if (key.equals("admin-menu") && !player.hasPermission("sethome.admin")) {
                    continue;
                }

                Section itemData = itemsSection.getSection(key);
                if (itemData == null) continue;

                ItemStack item = createBaseItem(player, itemData);

                // Grid positioning logic (Works perfectly for both formats: vertical and [1,2,3])
                if (itemData.contains("slots")) {
                    List<Integer> slots = itemData.getIntList("slots");
                    for (int slot : slots) {
                        if (slot >= 0 && slot < size) gui.setItem(slot, item);
                    }
                } else {
                    int slot = itemData.getInt("slot", 0);
                    if (slot >= 0 && slot < size) gui.setItem(slot, item);
                }
            }
        }

        player.openInventory(gui);
    }

    /**
     * Opens the paginated homes inventory menu reading live data
     */
    public void openHomesGUI(Player player) {
        YamlDocument config = plugin.getGuisConfig();
        Section section = config.getSection("gui.homes-gui");
        if (section == null) return;

        UUID uuid = player.getUniqueId();
        YamlDocument playerFile = plugin.getHomeManager().getPlayerFile(uuid);

        // Obtenemos la lista real de nombres indexados de hogares
        List<String> rawHomesList = playerFile != null ? playerFile.getStringList("homes") : null;
        if (rawHomesList == null) {
            rawHomesList = new ArrayList<>();
        }

        int currentPage = playerPage.getOrDefault(uuid, 1);
        int totalHomes = rawHomesList.size();

        List<Integer> homeSlots = section.getIntList("home-slots");
        int homesPerPage = homeSlots.size();

        // Cálculo matemático exacto de páginas máximas
        int maxPages = (int) Math.ceil((double) totalHomes / homesPerPage);
        if (maxPages == 0) maxPages = 1;

        // Si por alguna razón la página actual quedó huérfana (ej. borró una casa), reajustamos
        if (currentPage > maxPages) {
            currentPage = maxPages;
            playerPage.put(uuid, currentPage);
        }

        String title = section.getString("title", "Homes")
                .replace("%page%", String.valueOf(currentPage))
                .replace("%max_page%", String.valueOf(maxPages));
        title = Utils.setPlaceholders(player, title, plugin);

        int size = section.getInt("size", 54);
        Inventory gui = Bukkit.createInventory(null, size, title);

        // 1. Renderizar paneles fijos y flechas de paginación
        renderStaticItems(player, gui, section, currentPage, maxPages);

        // 2. RENDER DINÁMICO DE HOGARES REALES
        Section itemTemplate = section.getSection("items.home-item");
        if (itemTemplate != null && totalHomes > 0) {
            int startIndex = (currentPage - 1) * homesPerPage;

            for (int i = 0; i < homesPerPage; i++) {
                int homeIndex = startIndex + i;
                if (homeIndex >= totalHomes) break; // Ya no hay más casas que mostrar

                String homeName = rawHomesList.get(homeIndex);
                int slot = homeSlots.get(i);

                // Construimos el ítem base leyendo del template
                ItemStack homeItem = createBaseItem(player, itemTemplate);
                ItemMeta meta = homeItem.getItemMeta();

                if (meta != null) {
                    // Reemplazamos los parámetros de la casa en el Display Name
                    String name = meta.getDisplayName().replace("%home_name%", homeName);
                    meta.setDisplayName(name);

                    // Reemplazamos las coordenadas reales en el Lore
                    if (meta.hasLore()) {
                        List<String> lore = new ArrayList<>();

                        String world = playerFile.getString(homeName + ".world", "world");
                        // Formateamos las coordenadas a 2 decimales para que no se vea un texto gigante de números
                        String x = String.format(Locale.US, "%.2f", playerFile.getDouble(homeName + ".x"));
                        String y = String.format(Locale.US, "%.2f", playerFile.getDouble(homeName + ".y"));
                        String z = String.format(Locale.US, "%.2f", playerFile.getDouble(homeName + ".z"));

                        for (String line : meta.getLore()) {
                            lore.add(line.replace("%home_world%", world)
                                    .replace("%home_x%", x)
                                    .replace("%home_y%", y)
                                    .replace("%home_z%", z));
                        }
                        meta.setLore(lore);
                    }
                    homeItem.setItemMeta(meta);
                }

                gui.setItem(slot, homeItem);
            }
        }

        player.openInventory(gui);
    }

    /**
     * Opens the dynamic confirmation GUI for deleting a home
     */
    public void openConfirmationGUI(Player player, String homeName) {
        UUID uuid = player.getUniqueId();
        pendingDeletionHome.put(uuid, homeName); // Guardamos la casa en memoria

        YamlDocument config = plugin.getGuisConfig();
        Section section = config.getSection("gui.confirmation-gui");
        if (section == null) return;

        // Reemplazamos el nombre de la casa en el título
        String title = section.getString("title", "Confirm Deletion")
                .replace("%home_name%", homeName);
        title = Utils.setPlaceholders(player, title, plugin);

        int size = section.getInt("size", 27);
        Inventory gui = Bukkit.createInventory(null, size, title);

        Section itemsSection = section.getSection("items");
        if (itemsSection != null) {
            for (Object keyObj : itemsSection.getKeys()) {
                String key = String.valueOf(keyObj);
                Section itemData = itemsSection.getSection(key);
                if (itemData == null) continue;

                // Construimos el ítem base
                ItemStack item = createBaseItem(player, itemData);
                ItemMeta meta = item.getItemMeta();

                // Inyectamos el nombre de la casa de forma dinámica en las placeholders del ítem de Info
                if (meta != null) {
                    if (meta.hasDisplayName()) {
                        meta.setDisplayName(meta.getDisplayName().replace("%home_name%", homeName));
                    }
                    if (meta.hasLore()) {
                        List<String> lore = new ArrayList<>();
                        for (String line : meta.getLore()) {
                            lore.add(line.replace("%home_name%", homeName));
                        }
                        meta.setLore(lore);
                    }
                    item.setItemMeta(meta);
                }

                // Colocamos el ítem en su slot único o en sus múltiples slots decorativos
                if (itemData.contains("slots")) {
                    for (int slot : itemData.getIntList("slots")) {
                        gui.setItem(slot, item);
                    }
                } else if (itemData.contains("slot")) {
                    gui.setItem(itemData.getInt("slot"), item);
                }
            }
        }

        player.openInventory(gui);
    }

    /**
     * Renders background panes, page navigation, and static items
     */
    /**
     * Renders all static and structural items into the GUI, forcing pagination buttons to be always visible.
     */
    private void renderStaticItems(Player player, Inventory gui, Section menuSection, int currentPage, int maxPages) {
        Section itemsSection = menuSection.getSection("items");
        if (itemsSection == null) return;

        for (Object keyObj : itemsSection.getKeys()) {
            String key = String.valueOf(keyObj);

            // Saltamos la plantilla dinámica de hogares ya que esa se dibuja aparte
            if (key.equalsIgnoreCase("home-item")) continue;

            Section itemData = itemsSection.getSection(key);
            if (itemData == null) continue;

            // Creamos el ítem base leyendo las propiedades del YML (Material, texturas Skull, etc.)
            ItemStack item = createBaseItem(player, itemData);
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                // Reemplazamos variables globales de paginación si el administrador las usó en los botones
                if (meta.hasDisplayName()) {
                    meta.setDisplayName(meta.getDisplayName()
                            .replace("%page%", String.valueOf(currentPage))
                            .replace("%max_page%", String.valueOf(maxPages)));
                }
                if (meta.hasLore()) {
                    List<String> lore = new ArrayList<>();
                    for (String line : meta.getLore()) {
                        lore.add(line.replace("%page%", String.valueOf(currentPage))
                                .replace("%max_page%", String.valueOf(maxPages)));
                    }
                    meta.setLore(lore);
                }
                item.setItemMeta(meta);
            }

            // Colocamos el ítem de forma estricta en su posición o lista de posiciones
            if (itemData.contains("slots")) {
                for (int slot : itemData.getIntList("slots")) {
                    gui.setItem(slot, item);
                }
            } else if (itemData.contains("slot")) {
                gui.setItem(itemData.getInt("slot"), item);
            }
        }
    }

    /**
     * Factory utility to generate an ItemStack base out of BoostedYAML fields
     */
    private ItemStack createBaseItem(Player player, Section section) {
        String type = section.getString("type", "STONE").toUpperCase();
        ItemStack item;

        if (type.equals("HEAD")) {
            item = Utils.getHeadFromBase64(section.getString("value", ""));
        } else {
            Material mat = Material.matchMaterial(type);
            item = new ItemStack(mat != null ? mat : Material.BARRIER);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String rawName = section.getString("display-name", section.getString("name", null));
            if (rawName != null) {
                if (rawName.isEmpty()) {
                    meta.setDisplayName(Utils.color("&f "));
                } else {
                    meta.setDisplayName(Utils.setPlaceholders(player, rawName, plugin));
                }
            }

            List<String> lore = section.getStringList("lore");
            if (lore != null && !lore.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(Utils.setPlaceholders(player, line, plugin));
                }
                meta.setLore(coloredLore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // --- HELPER METHODS FOR THE LISTENER ---

    public String getMainMenuTitle() {
        return Utils.color(plugin.getGuisConfig().getString("gui.main-gui.title", "Menu"));
    }

    public String getHomesMenuTitle() {
        return Utils.color(plugin.getGuisConfig().getString("gui.homes-gui.title", "Homes"));
    }

    public String getPendingDeletionHome(UUID uuid) {
        return pendingDeletionHome.get(uuid);
    }

    public void clearPendingDeletion(UUID uuid) {
        pendingDeletionHome.remove(uuid);
    }

    public void playConfiguredClickSound(Player player, String guiPath) {
        String rawSound = plugin.getGuisConfig().getString("gui." + guiPath + ".click-sound");
        if (rawSound != null && !rawSound.isEmpty()) {
            String formattedSound = Utils.formatSoundName(rawSound);
            player.playSound(player.getLocation(), formattedSound, 1.0f, 1.0f);
        }
    }

    public int getPlayerPage(UUID uuid) { return playerPage.getOrDefault(uuid, 1); }
    public void setPlayerPage(UUID uuid, int page) { playerPage.put(uuid, page); }
}