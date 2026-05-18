package org.sethomegui.Listeners;

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

public class AdminChatListener implements Listener {

    private final SetHomeGUI plugin;

    public AdminChatListener(SetHomeGUI plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAdminChatSearch(AsyncChatEvent event) {
        Player admin = event.getPlayer();

        if (!admin.hasMetadata("admin_search_mode")) return;

        event.setCancelled(true);

        String query = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
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
        admin.getScheduler().run(plugin, (task) -> {
            manager.openAdminMenu(admin);
        }, null);
    }
}