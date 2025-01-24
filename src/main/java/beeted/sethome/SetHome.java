package beeted.sethome;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class SetHome extends JavaPlugin {
    ConsoleCommandSender console = Bukkit.getConsoleSender();
    private HomeCommandExecutor commandExecutor;
    private HomeTabCompleter commandTabExecutor;
    @Override
    public void onEnable() {
        // Cargar configuración
        registerConfig();

        // Obtener el comando configurado en la configuración
        String userCommand = getConfig().getString("menu.open-command", "/home").replace("/", "");

        // Registrar el comando dinámico
        commandExecutor = new HomeCommandExecutor(this);
        HomeCommandExecutor.registerDynamicCommand(this, userCommand);

        getLogger().info("SetHome plugin enabled with dynamic command: /" + userCommand);

        // Registrar eventos
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new Menu(this), this);

        // Métricas
        int pluginId = 23348;
        Metrics metrics = new Metrics(this, pluginId);
    }


    public HomeCommandExecutor getCommandExecutor() {
        return commandExecutor;
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
