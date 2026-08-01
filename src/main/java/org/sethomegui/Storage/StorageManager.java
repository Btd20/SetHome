package org.sethomegui.Storage;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import org.sethomegui.SetHomeGUI;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Punto unico de entrada al almacenamiento. Decide que backend se usa segun config.yml,
 * mantiene la cache en memoria y reparte las escrituras.
 *
 * El resto del plugin sigue trabajando con YamlDocument y no necesita saber si detras
 * hay archivos, MySQL o una red de servidores sincronizada por Redis.
 */
public class StorageManager {

    private final SetHomeGUI plugin;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    private StorageProvider provider;
    private YamlStorageProvider yamlFallback;
    private RedisBridge redis;
    private ExecutorService writeExecutor;

    public StorageManager(SetHomeGUI plugin) {
        this.plugin = plugin;
    }

    // =====================================================================
    //  ARRANQUE
    // =====================================================================

    public void initialize() {
        Section storageConfig = plugin.getMainConfig().getSection("storage");
        String type = storageConfig != null ? storageConfig.getString("type", "YAML") : "YAML";

        this.yamlFallback = new YamlStorageProvider(plugin);
        this.yamlFallback.initialize();

        String normalizedType = type == null ? "YAML" : type.trim().toUpperCase();

        if (storageConfig != null && normalizedType.equals("MYSQL")) {
            if (setupMySql(storageConfig)) {
                setupRedis(storageConfig);
            }
        } else {
            if (!normalizedType.equals("YAML")) {
                plugin.getLogger().warning("storage.type '" + type + "' no existe. Valores validos: YAML, MYSQL. Se usaran archivos YAML.");
            }
            this.provider = yamlFallback;
        }

        plugin.getLogger().info("Almacenamiento activo: " + provider.getName()
                + (redis != null ? " + Redis (cache y sincronizacion)" : ""));

        if (provider != yamlFallback) {
            this.writeExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "SetHomeGUI-Storage-Writer");
                thread.setDaemon(true);
                return thread;
            });
            migrateFromYamlIfRequested(storageConfig);
        }
    }

    /**
     * @return true si MySQL quedo operativo; false si hubo que caer de vuelta a YAML.
     */
    private boolean setupMySql(Section storageConfig) {
        Section mysqlConfig = storageConfig.getSection("mysql");
        if (mysqlConfig == null) {
            plugin.getLogger().severe("storage.type es MYSQL pero falta la seccion storage.mysql en config.yml. Se usaran archivos YAML.");
            this.provider = yamlFallback;
            return false;
        }

        MySqlStorageProvider mysql = new MySqlStorageProvider(plugin, mysqlConfig);
        try {
            mysql.initialize();
            this.provider = mysql;
            return true;
        } catch (Throwable e) {
            // Nunca dejamos el plugin inservible por un fallo de base de datos:
            // avisamos con claridad y seguimos funcionando con archivos locales.
            plugin.getLogger().severe("No se pudo conectar con MySQL: " + e.getMessage());
            plugin.getLogger().severe("SetHomeGUI+ seguira funcionando con archivos YAML locales. Revisa storage.mysql en config.yml.");
            mysql.shutdown();
            this.provider = yamlFallback;
            return false;
        }
    }

    private void setupRedis(Section storageConfig) {
        Section redisConfig = storageConfig.getSection("redis");
        if (redisConfig == null || !redisConfig.getBoolean("enabled", false)) return;

        RedisBridge bridge = new RedisBridge(plugin, redisConfig);
        try {
            bridge.initialize(this::invalidate);
            this.redis = bridge;
        } catch (Throwable e) {
            plugin.getLogger().severe("No se pudo conectar con Redis: " + e.getMessage());
            plugin.getLogger().severe("La sincronizacion entre servidores queda desactivada; MySQL sigue funcionando con normalidad.");
            bridge.shutdown();
        }
    }

    /**
     * Copia los archivos data/*.yml al backend externo la primera vez que se activa.
     */
    private void migrateFromYamlIfRequested(Section storageConfig) {
        if (storageConfig == null || !storageConfig.getBoolean("migrate-from-yaml", false)) return;

        plugin.getLogger().info("Migracion activada: copiando los datos locales al backend externo...");

        int migrated = 0;
        int failed = 0;
        for (UUID uuid : yamlFallback.listPlayers()) {
            try {
                PlayerRecord record = yamlFallback.load(uuid);
                if (record == null) continue;
                provider.save(uuid, record);
                migrated++;
            } catch (Exception e) {
                failed++;
                plugin.getLogger().warning("No se pudo migrar los datos de " + uuid + ": " + e.getMessage());
            }
        }

        plugin.getLogger().info("Migracion terminada: " + migrated + " jugadores copiados"
                + (failed > 0 ? ", " + failed + " con errores" : "") + ".");
        plugin.getLogger().info("Pon storage.migrate-from-yaml en false para no repetir la migracion en cada arranque.");
    }

    // =====================================================================
    //  LECTURA
    // =====================================================================

    /**
     * Devuelve el documento del jugador, cargandolo del backend la primera vez.
     * Devuelve null solo si el backend falla de forma irrecuperable.
     */
    public PlayerData get(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) return cached;

        try {
            PlayerData loaded = PlayerData.of(uuid, this, readRaw(uuid));
            // putIfAbsent evita que dos hilos que lean a la vez se pisen el documento
            PlayerData existing = cache.putIfAbsent(uuid, loaded);
            return existing != null ? existing : loaded;
        } catch (Exception e) {
            String loadErrorMsg = plugin.getMainConfig().getString(
                    "messages.system-errors.load-error",
                    "Could not load or create data file for user: %uuid%"
            );
            plugin.getLogger().severe(loadErrorMsg.replace("%uuid%", uuid.toString()));
            plugin.getLogger().severe("Detalle: " + e.getMessage());
            return null;
        }
    }

    /**
     * Obtiene el YAML crudo del jugador consultando primero la cache de Redis.
     */
    private String readRaw(UUID uuid) throws Exception {
        if (redis != null) {
            String cachedYaml = redis.get(uuid);
            if (cachedYaml != null) return cachedYaml;
        }

        PlayerRecord record = provider.load(uuid);
        String yaml = record == null ? "{}" : PlayerDataCodec.toYaml(record);

        if (redis != null) {
            redis.put(uuid, yaml);
        }
        return yaml;
    }

    /**
     * Carga los datos del jugador en memoria por adelantado (usado al conectarse).
     */
    public void preload(UUID uuid) {
        cache.remove(uuid);
        get(uuid);
    }

    /** Descarta la copia en memoria; la proxima lectura volvera a pedirla al backend. */
    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    // =====================================================================
    //  ESCRITURA
    // =====================================================================

    /**
     * Persiste el documento en el backend. Lo invoca {@link PlayerData#save()}.
     */
    public void persist(UUID uuid, YamlDocument document) throws IOException {
        if (document instanceof PlayerData) {
            cache.put(uuid, (PlayerData) document);
        }

        String yaml = document.dump();

        // Con archivos locales mantenemos la escritura sincrona de siempre: es rapida
        // y garantiza que el .yml queda en disco antes de continuar.
        if (provider == yamlFallback) {
            yamlFallback.saveRaw(uuid, yaml);
            return;
        }

        // Tomamos la instantanea de los datos AQUI, en el hilo que llama, para que el hilo
        // de escritura nunca lea un documento que alguien pueda estar modificando a la vez.
        PlayerRecord record = PlayerDataCodec.toRecord(uuid, document);
        submitWrite(uuid, record, yaml);
    }

    private void submitWrite(UUID uuid, PlayerRecord record, String yaml) {
        if (writeExecutor == null || writeExecutor.isShutdown()) {
            writeDirect(uuid, record, yaml);
            return;
        }
        writeExecutor.execute(() -> writeDirect(uuid, record, yaml));
    }

    private void writeDirect(UUID uuid, PlayerRecord record, String yaml) {
        try {
            provider.save(uuid, record);
            if (redis != null) {
                redis.put(uuid, yaml);
                redis.invalidate(uuid);
            }
        } catch (Exception e) {
            String saveErrorMsg = plugin.getMainConfig().getString(
                    "messages.system-errors.save-error",
                    "Could not save home data for UUID %uuid%"
            );
            plugin.getLogger().severe(saveErrorMsg.replace("%uuid%", uuid.toString()));
            plugin.getLogger().severe("Detalle: " + e.getMessage());
        }
    }

    /**
     * Borra por completo los datos del jugador en el backend activo.
     */
    public void delete(UUID uuid) {
        cache.remove(uuid);
        try {
            provider.delete(uuid);
            if (redis != null) redis.invalidate(uuid);
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudieron borrar los datos de " + uuid + ": " + e.getMessage());
        }
    }

    // =====================================================================
    //  CONSULTAS GLOBALES
    // =====================================================================

    /**
     * Lista todos los jugadores con datos guardados en el backend activo.
     */
    public Set<UUID> listPlayers() {
        try {
            return provider.listPlayers();
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo listar a los jugadores del backend: " + e.getMessage());
            return new LinkedHashSet<>();
        }
    }

    /**
     * Numero de hogares por jugador en una sola operacion. Pensado para llamarse
     * de forma asincrona desde el panel de administracion.
     */
    public Map<UUID, Integer> countHomesByPlayer() {
        try {
            return provider.countHomesByPlayer();
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo contar los hogares del backend: " + e.getMessage());
            return new ConcurrentHashMap<>();
        }
    }

    public boolean isExternalBackend() {
        return provider != yamlFallback;
    }

    public String getBackendName() {
        return provider.getName();
    }

    // =====================================================================
    //  APAGADO
    // =====================================================================

    public void shutdown() {
        if (writeExecutor != null) {
            writeExecutor.shutdown();
            try {
                // Damos margen a que terminen las escrituras pendientes antes de cerrar conexiones
                if (!writeExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("Quedaron escrituras pendientes al apagar el almacenamiento.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (redis != null) redis.shutdown();
        if (provider != null) provider.shutdown();
        cache.clear();
    }
}
