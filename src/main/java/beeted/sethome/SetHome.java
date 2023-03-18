package beeted.sethome;

import beeted.sethome.coordinates.SaveCoordinates;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class SetHome extends JavaPlugin {
    ConsoleCommandSender console = Bukkit.getConsoleSender();
    @Override
    public void onEnable() {
        // Plugin startup logic
        registerEvents();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public void registerEvents() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new SaveCoordinates(this), this);
    }
}
