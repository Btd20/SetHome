package org.sethomegui;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.sethomegui.Commands.HomeAdminCommand;
import org.sethomegui.Commands.HomeAdminTabCompleter;
import org.sethomegui.Commands.HomeTabCompleter;
import org.sethomegui.Listeners.*;
import org.sethomegui.Managers.AdminGUIManager;
import org.sethomegui.Managers.GUIManager;
import org.sethomegui.Commands.MainCommands;
import org.sethomegui.Managers.HomeManager;
import org.sethomegui.Managers.TeleportManager;
import org.sethomegui.Placeholders.GlobalPlaceholders;
import org.sethomegui.Storage.StorageManager;
import org.bukkit.NamespacedKey;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;

import java.io.File;
import java.io.IOException;

public final class SetHomeGUI extends JavaPlugin {
    private YamlDocument mainConfig;
    private YamlDocument guisConfig;
    private YamlDocument actionsConfig;
    private YamlDocument guiAdminConfig;
    private GUIManager guiManager;
    private HomeManager homeManager;
    private TeleportManager teleportManager;
    private AdminGUIManager adminGUIManager;
    private StorageManager storageManager;
    private NamespacedKey actionKey;
    private NamespacedKey targetHomeKey;

    @Override
    public void onEnable() {
        setupFiles();

        int pluginId = 23348;
        new Metrics(this, pluginId);

        // Las llaves deben existir antes de que cualquier menú construya ítems
        this.actionKey = new NamespacedKey(this, "sethomegui_action");
        this.targetHomeKey = new NamespacedKey(this, "sethomegui_target_home");

        // El almacenamiento debe estar listo antes que cualquier manager que lea datos
        this.storageManager = new StorageManager(this);
        this.storageManager.initialize();

        this.guiManager = new GUIManager(this);
        this.homeManager = new HomeManager(this);
        this.teleportManager = new TeleportManager(this);
        this.adminGUIManager = new AdminGUIManager(this);

        this.adminGUIManager.loadPlayersCacheAsync();

        getServer().getPluginManager().registerEvents(new MenuClickListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatPromptListener(this), this);
        getServer().getPluginManager().registerEvents(new HomesMenuClickListener(this), this);
        getServer().getPluginManager().registerEvents(new ConfirmationMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new AdminMenuClickListener(this), this);
        getServer().getPluginManager().registerEvents(new AdminChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDataListener(this), this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            getLogger().info("PlaceholderAPI found! Registering placeholders...");
            new GlobalPlaceholders(this).register();
        } else {
            getLogger().warning("PlaceholderAPI not found! Placeholders will not work.");
        }

        if (getCommand("home") != null) {
            getCommand("home").setExecutor(new MainCommands(this));

            // NUEVO: Vinculamos el TabCompleter de los hogares del jugador
            getCommand("home").setTabCompleter(new HomeTabCompleter(this));
        }

        if (this.getCommand("sethome") != null) {
            this.getCommand("sethome").setExecutor(new org.sethomegui.Commands.SetHomeCommand(this));
        }

        if (this.getCommand("delhome") != null) {
            this.getCommand("delhome").setExecutor(new org.sethomegui.Commands.DelHomeCommand(this));
            this.getCommand("delhome").setTabCompleter(new org.sethomegui.Commands.DelHomeTabCompleter(this));
        }

        // Registro del comando ejecutor
        if (getCommand("homeadmin") != null) {
            getCommand("homeadmin").setExecutor(new HomeAdminCommand(this));

            // NUEVO: Vinculamos el TabCompleter al comando
            getCommand("homeadmin").setTabCompleter(new HomeAdminTabCompleter());
        }

        getLogger().info("SetHomeGUI has been successfully enabled on Folia!");
    }

    private void setupFiles() {
        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }

            // Configuramos las opciones globales del actualizador para que NUNCA borre nada del usuario
            UpdaterSettings updaterSettings = UpdaterSettings.builder()
                    .setKeepAll(true) // Conserva absolutamente todas las modificaciones de los administradores
                    .build();

            LoaderSettings loaderSettings = LoaderSettings.builder()
                    .setAutoUpdate(true) // Dejamos que BoostedYAML fusione de forma segura
                    .build();

            // --- MANEJO DE CONFIG.YML ---
            this.mainConfig = YamlDocument.create(
                    new File(getDataFolder(), "config.yml"),
                    getResource("config.yml"), // Recurso base dentro del JAR
                    GeneralSettings.DEFAULT,
                    loaderSettings,
                    updaterSettings
            );

            // --- MANEJO DE GUI.YML ---
            this.guisConfig = YamlDocument.create(
                    new File(getDataFolder(), "gui.yml"),
                    getResource("gui.yml"), // Siempre pasamos el recurso; el updater se encarga de no romper nada
                    GeneralSettings.DEFAULT,
                    loaderSettings,
                    updaterSettings
            );

            this.actionsConfig = YamlDocument.create(
                    new File(getDataFolder(), "actions.yml"),
                    getResource("actions.yml"),
                    GeneralSettings.DEFAULT,
                    loaderSettings,
                    updaterSettings
            );

            getLogger().info("Configuration files successfully loaded without data loss!");

        } catch (IOException e) {
            getLogger().severe("Could not load config.yml and gui.yml correctly!");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    public YamlDocument getMainConfig() {
        return this.mainConfig;
    }

    public YamlDocument getGuisConfig() {
        return this.guisConfig;
    }

    public YamlDocument getActionsConfig() {
        return this.actionsConfig;
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    public HomeManager getHomeManager() {
        return this.homeManager;
    }

    public TeleportManager getTeleportManager() {
        return this.teleportManager;
    }

    public AdminGUIManager getAdminGUIManager() { return this.adminGUIManager; }

    public StorageManager getStorageManager() { return this.storageManager; }

    public NamespacedKey getActionKey() { return this.actionKey; }

    public NamespacedKey getTargetHomeKey() { return this.targetHomeKey; }

    public YamlDocument getGuiAdminConfig() { return this.guiAdminConfig; }

    @Override
    public void onDisable() {
        // Los archivos de configuración de lectura (como menús y config general) NO se deben guardar
        // al apagar el servidor a menos que el plugin modifique valores mediante código (setters).
        // Al quitar el .save() de aquí, evitamos que un guardado corrupto destruya las ediciones hechas a mano.

        // Vacia la cola de escrituras pendientes y cierra las conexiones de MySQL/Redis
        if (storageManager != null) storageManager.shutdown();

        getLogger().info("SetHomeGUI has been safely disabled.");
    }
}