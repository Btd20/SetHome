package org.sethomegui.Storage;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Documento de datos de un jugador con escritura transparente hacia el backend activo.
 *
 * Es un YamlDocument normal para todo el plugin, pero su {@link #save()} no escribe en disco:
 * entrega el contenido al {@link StorageManager}, que decide si acaba en un .yml o en MySQL.
 * Gracias a esto, todas las llamadas existentes a save() persisten en la base de datos sin cambios.
 */
public class PlayerData extends YamlDocument {

    private final UUID uuid;
    private final StorageManager storage;

    private PlayerData(UUID uuid, StorageManager storage, InputStream content) throws IOException {
        super(content, null, GeneralSettings.DEFAULT);
        this.uuid = uuid;
        this.storage = storage;
    }

    /**
     * Construye el documento a partir del YAML crudo devuelto por el backend.
     */
    public static PlayerData of(UUID uuid, StorageManager storage, String yaml) throws IOException {
        String content = (yaml == null || yaml.trim().isEmpty()) ? "{}" : yaml;
        return new PlayerData(uuid, storage, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    public UUID getPlayerUuid() {
        return uuid;
    }

    /**
     * Persiste el documento en el backend configurado (archivo YAML, MySQL, ...).
     */
    @Override
    public boolean save() throws IOException {
        storage.persist(uuid, this);
        return true;
    }

    /**
     * SnakeYAML localiza sus serializadores por clase EXACTA, asi que heredar dump()
     * lanzaria "Representer is not defined". Serializamos una copia estandar del documento.
     */
    @Override
    public String dump() {
        try {
            return PlayerDataCodec.plainCopy(this).dump();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo serializar los datos de " + uuid, e);
        }
    }

    @Override
    public String dump(dev.dejvokep.boostedyaml.settings.dumper.DumperSettings dumperSettings) {
        try {
            return PlayerDataCodec.plainCopy(this).dump(dumperSettings);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo serializar los datos de " + uuid, e);
        }
    }

    /**
     * El documento no esta anclado a ningun archivo: recargar significa volver a pedirlo al backend.
     */
    @Override
    public boolean reload() throws IOException {
        storage.invalidate(uuid);
        return true;
    }
}
