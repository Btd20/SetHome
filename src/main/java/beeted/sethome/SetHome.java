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

        registerConfig();

        String userCommand = getConfig().getString("menu.open-command", "/home").replace("/", "");

        PluginManager pm = getServer().getPluginManager();

        Command command = this.getCommand(userCommand);

        // Crear el ejecutor de comandos
        commandExecutor = new HomeCommandExecutor(this);
        // Registrar el comando dinámico
        HomeCommandExecutor.registerDynamicCommand(this, userCommand);
        getLogger().info("SetHome plugin enabled with command: /" + userCommand);

        //Metrics
        int pluginId = 	23348;
        Metrics metrics = new Metrics(this, pluginId);

        // Plugin startup logic
        pm.registerEvents(new Menu(this), this);
        if (command instanceof PluginCommand) {
            PluginCommand pluginCommand = (PluginCommand) command;

            // Set the executor for the command
            pluginCommand.setExecutor(new HomeCommandExecutor(this));

            // Set the tab completer for the command
            pluginCommand.setTabCompleter(new HomeTabCompleter(this));
        }

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
