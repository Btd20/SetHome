package beeted.sethome;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SetHome extends JavaPlugin {
    ConsoleCommandSender console = Bukkit.getConsoleSender();
    private HomeCommandExecutor commandExecutor;
    private HomeTabCompleter commandTabExecutor;
    @Override
    public void onEnable() {
        // Cargar configuración
        registerConfig();

        // Obtener el comando configurado en la configuración
        String userCommand = getConfig().getString("menu.open-command").replace("/", "");

        // Registrar el comando dinámico
        commandExecutor = new HomeCommandExecutor(this);
        //HomeCommandExecutor.registerDynamicCommand(this, userCommand);

        getCommand(userCommand).setExecutor(new HomeCommandExecutor(this));
        getCommand(userCommand).setTabCompleter(new HomeTabCompleter(this));

        getLogger().info("SetHome GUI plugin enabled with dynamic command: /" + userCommand);

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
        console.sendMessage("[SetHome GUI] Saving your home...");
        console.sendMessage("[SetHome GUI] Saving config.");
    }

    public void registerConfig(){
        File config = new File(this.getDataFolder(), "config.yml");
        String configRute = config.getPath();
        if(!config.exists()){
            this.getConfig().options().copyDefaults(true);
            saveDefaultConfig();
        }
    }

    public File getPlayerDataFile(UUID uuid) {
        File dataFolder = new File(getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        return new File(dataFolder, uuid.toString() + ".yml");
    }

    public List<String> getHomesFor(Player player) {
        List<String> homes = new ArrayList<>();
        File file = getPlayerDataFile(player.getUniqueId());
        if (!file.exists()) return homes;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        homes = config.getStringList("homes");

        return homes != null ? homes : new ArrayList<>();
    }
}
