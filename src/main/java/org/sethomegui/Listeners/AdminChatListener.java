package org.sethomegui.Listeners;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.sethomegui.SetHomeGUI;
import org.sethomegui.Managers.AdminGUIManager;
import org.sethomegui.Utils.Utils;

import java.util.regex.Pattern;

public class AdminChatListener implements Listener {

    private final SetHomeGUI plugin;

    // Patrón optimizado para limpiar formatos Hexadecimales comunes (&#ffffff o #ffffff)
    private static final Pattern HEX_CLEAN_PATTERN = Pattern.compile("&#[A-Fa-f0-9]{6}|#[A-Fa-f0-9]{6}");

    public AdminChatListener(SetHomeGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAdminChatSearch(AsyncChatEvent event) {
        Player admin = event.getPlayer();

        if (!admin.hasMetadata("admin_search_mode")) return;

        event.setCancelled(true);

        // 1. Extraemos el texto plano del componente moderno de Paper
        String rawQuery = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        // 2. 🛠️ LIMPIEZA DE TEXTO CRUDO EXTREMA (Evita desvíos por ChatColor2 o formatos heredados)
        rawQuery = HEX_CLEAN_PATTERN.matcher(rawQuery).replaceAll(""); // Remueve Hex
        rawQuery = ChatColor.stripColor(rawQuery); // Remueve secciones legadas '§'
        rawQuery = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', rawQuery)); // Remueve secciones con '&'

        String query = rawQuery;
        AdminGUIManager manager = plugin.getAdminGUIManager();
        FileConfiguration config = plugin.getConfig();

        admin.removeMetadata("admin_search_mode", plugin);

        if (query.equalsIgnoreCase("cancel")) {
            String msg = config.getString("messages.admin.search-cancelled", "&cSearch cancelled. Reopening menu...");
            admin.sendMessage(Utils.color(msg));
        }
        else if (query.isEmpty() || query.equalsIgnoreCase("all")) {
            manager.setSearchFilter(admin.getUniqueId(), "");
            String msg = config.getString("messages.admin.search-reset", "&aSearch reset. Listing all records...");
            admin.sendMessage(Utils.color(msg));
        }
        else {
            manager.setSearchFilter(admin.getUniqueId(), query.toLowerCase());
            String msg = config.getString("messages.admin.search-filter-applied", "&aFilter applied: &e%query%");
            admin.sendMessage(Utils.color(msg.replace("%query%", query)));
        }

        manager.setPage(admin.getUniqueId(), 1);

        // Abrimos el menú de forma segura en el hilo de la entidad (Folia/Paper-compliant)
        admin.getScheduler().run(plugin, (task) -> {
            manager.openAdminMenu(admin);
        }, null);
    }
}