package beeted.sethome;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class SetHome extends JavaPlugin {
    ConsoleCommandSender console = Bukkit.getConsoleSender();
    @Override
    public void onEnable() {

        //Metrics
        int pluginId = 	23348;
        Metrics metrics = new Metrics(this, pluginId);

        // Plugin startup logic
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new Menu(this), this);
        getConfig().options().copyDefaults();
        getCommand("home").setExecutor(new HomeCommandExecutor(this));
        getCommand("home").setTabCompleter(new HomeTabCompleter());
        saveDefaultConfig();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        console.sendMessage("[SetHome] Saving your home...");
        console.sendMessage("[SetHome] Saving config.");
    }

    public void registerConfig(){
        File config = new File(this.getDataFolder(), "config.yml");
        String configRute = config.getPath();
        if(!config.exists()){
            this.getConfig().options().copyDefaults(true);
            saveDefaultConfig();
        }
    }
}
