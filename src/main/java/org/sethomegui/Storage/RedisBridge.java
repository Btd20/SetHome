package org.sethomegui.Storage;

import dev.dejvokep.boostedyaml.block.implementation.Section;
import org.sethomegui.SetHomeGUI;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Capa opcional de cache y sincronizacion instantanea sobre el backend persistente.
 *
 * Redis no guarda los datos de forma definitiva (eso lo hace MySQL): guarda una copia rapida
 * con caducidad y avisa por pub/sub al resto de servidores de la red cuando un jugador cambia,
 * para que ninguno siga sirviendo informacion vieja desde su memoria.
 */
public class RedisBridge {

    private static final String CHANNEL = "sethomegui:invalidate";

    private final SetHomeGUI plugin;
    private final Section config;
    private final String serverId;
    private final String keyPrefix;
    private final int cacheSeconds;

    private JedisPool pool;
    private JedisPubSub subscriber;
    private Thread subscriberThread;
    private volatile boolean running;

    public RedisBridge(SetHomeGUI plugin, Section config) {
        this.plugin = plugin;
        this.config = config;
        this.serverId = config.getString("server-id", UUID.randomUUID().toString());
        this.keyPrefix = config.getString("key-prefix", "sethomegui:player:");
        this.cacheSeconds = Math.max(1, config.getInt("cache-seconds", 300));
    }

    /**
     * @param onRemoteInvalidate se invoca cuando OTRO servidor avisa de que un jugador cambio.
     */
    public void initialize(Consumer<UUID> onRemoteInvalidate) throws Exception {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(Math.max(1, config.getInt("pool-size", 8)));

        String host = config.getString("host", "localhost");
        int port = config.getInt("port", 6379);
        int timeout = Math.max(1000, config.getInt("timeout", 2000));
        String password = config.getString("password", "");
        int database = config.getInt("database", 0);

        this.pool = new JedisPool(
                poolConfig,
                host,
                port,
                timeout,
                (password == null || password.isEmpty()) ? null : password,
                database,
                config.getBoolean("ssl", false)
        );

        // Comprobamos la conexion de inmediato para no arrancar creyendo que Redis funciona
        try (Jedis jedis = pool.getResource()) {
            jedis.ping();
        }

        this.running = true;
        startSubscriber(onRemoteInvalidate);
    }

    private void startSubscriber(Consumer<UUID> onRemoteInvalidate) {
        this.subscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                // Formato del mensaje: <serverId>|<uuid>
                int separator = message.indexOf('|');
                if (separator <= 0) return;

                String origin = message.substring(0, separator);
                if (origin.equals(serverId)) return; // Nuestro propio aviso

                try {
                    onRemoteInvalidate.accept(UUID.fromString(message.substring(separator + 1)));
                } catch (IllegalArgumentException ignored) {
                    // Mensaje ajeno o corrupto en el canal.
                }
            }
        };

        // La suscripcion de Jedis bloquea el hilo, asi que le damos uno propio en lugar de
        // ocupar indefinidamente un hilo del planificador del servidor.
        this.subscriberThread = new Thread(() -> {
            while (running) {
                try (Jedis jedis = pool.getResource()) {
                    jedis.subscribe(subscriber, CHANNEL);
                } catch (Exception e) {
                    if (!running) return;
                    plugin.getLogger().warning("Se perdio la conexion pub/sub con Redis, reintentando en 5s: " + e.getMessage());
                    try {
                        Thread.sleep(5000L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "SetHomeGUI-Redis-Subscriber");

        this.subscriberThread.setDaemon(true);
        this.subscriberThread.start();
    }

    public String get(UUID uuid) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(keyPrefix + uuid);
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo leer de Redis (se usara el backend persistente): " + e.getMessage());
            return null;
        }
    }

    public void put(UUID uuid, String yaml) {
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(keyPrefix + uuid, cacheSeconds, yaml);
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo escribir en Redis: " + e.getMessage());
        }
    }

    /**
     * Borra la copia cacheada y avisa al resto de servidores de la red.
     */
    public void invalidate(UUID uuid) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(keyPrefix + uuid);
            jedis.publish(CHANNEL, serverId + "|" + uuid);
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo invalidar la cache de Redis: " + e.getMessage());
        }
    }

    public void shutdown() {
        this.running = false;
        try {
            if (subscriber != null && subscriber.isSubscribed()) {
                subscriber.unsubscribe();
            }
        } catch (Exception ignored) {
            // El canal ya podia estar cerrado.
        }
        if (subscriberThread != null) {
            subscriberThread.interrupt();
        }
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
    }
}
