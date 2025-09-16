package beeted.sethome;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.Settings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class SetHome extends JavaPlugin {
    ConsoleCommandSender console = Bukkit.getConsoleSender();
    private HomeCommandExecutor commandExecutor;
    //public YamlDocument configYaml;
    private HomeTabCompleter commandTabExecutor;
    private YamlDocument configYaml;

    // Lista de admins que están eliminando un hogar
    private final Set<UUID> adminsDeletingHome = new HashSet<>();

    // Métodos auxiliares
    public void startDeletingHome(UUID adminUUID) {
        adminsDeletingHome.add(adminUUID);
    }

    public void stopDeletingHome(UUID adminUUID) {
        adminsDeletingHome.remove(adminUUID);
    }

    public boolean isDeletingHome(UUID adminUUID) {
        return adminsDeletingHome.contains(adminUUID);
    }

    @Override
    public void onEnable() {
        // Crear config por defecto si no existe
        saveDefaultConfig();

        // Recargar para que Bukkit lea los cambios
        reloadConfig();

        try {
            configYaml = YamlDocument.create(
                    new File(getDataFolder(), "config.yml"),
                    getResource("config.yml"),
                    GeneralSettings.DEFAULT, // mantiene la estructura del archivo más limpia
                    LoaderSettings.builder()
                            .setAutoUpdate(true)
                            .build()
            );

            // Guardar los valores faltantes de vuelta al config.yml
            configYaml.save();
        } catch (IOException e) {
            e.printStackTrace();
            getLogger().severe("Could not load config.yml.");
        }

        String userCommand = getConfig().getString("menu.open-command").replace("/", "");

        commandExecutor = new HomeCommandExecutor(this);

        getCommand(userCommand).setExecutor(commandExecutor);
        getCommand(userCommand).setTabCompleter(new HomeTabCompleter(this));

        getLogger().info("SetHome GUI plugin enabled with dynamic command: /" + userCommand);

        getServer().getPluginManager().registerEvents(new Menu(this), this);

        int pluginId = 23348;
        new Metrics(this, pluginId);
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

    public void registerConfig() {
        saveDefaultConfig(); // crea config si no existe
        getConfig().options().copyDefaults(true);
        saveConfig(); // guarda las claves por defecto nuevas si faltan
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
