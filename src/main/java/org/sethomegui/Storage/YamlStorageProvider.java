package org.sethomegui.Storage;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import org.sethomegui.SetHomeGUI;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Backend por defecto: un archivo data/&lt;uuid&gt;.yml por jugador.
 * Es el comportamiento historico del plugin y no requiere ninguna configuracion.
 */
public class YamlStorageProvider implements StorageProvider {

    private final SetHomeGUI plugin;
    private final File dataFolder;

    public YamlStorageProvider(SetHomeGUI plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data");
    }

    @Override
    public String getName() {
        return "YAML (archivos locales)";
    }

    @Override
    public void initialize() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    @Override
    public void shutdown() {
        // Sin recursos que liberar.
    }

    public File getPlayerFile(UUID uuid) {
        return new File(dataFolder, uuid.toString() + ".yml");
    }

    @Override
    public PlayerRecord load(UUID uuid) throws IOException {
        File file = getPlayerFile(uuid);
        if (!file.exists()) return null;

        YamlDocument document = YamlDocument.create(file, GeneralSettings.DEFAULT);
        return PlayerDataCodec.toRecord(uuid, document);
    }

    @Override
    public void save(UUID uuid, PlayerRecord record) throws IOException {
        saveRaw(uuid, PlayerDataCodec.toYaml(record));
    }

    /**
     * Atajo que evita el viaje de ida y vuelta por el codec cuando ya tenemos el YAML final.
     */
    public void saveRaw(UUID uuid, String yaml) throws IOException {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        Files.write(getPlayerFile(uuid).toPath(), yaml.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void delete(UUID uuid) {
        File file = getPlayerFile(uuid);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("No se pudo eliminar el archivo de datos de " + uuid);
        }
    }

    @Override
    public Map<UUID, Integer> countHomesByPlayer() {
        Map<UUID, Integer> counts = new HashMap<>();
        for (UUID uuid : listPlayers()) {
            try {
                PlayerRecord record = load(uuid);
                counts.put(uuid, record == null ? 0 : record.getHomes().size());
            } catch (IOException e) {
                plugin.getLogger().warning("No se pudo leer el archivo de datos de " + uuid + ": " + e.getMessage());
            }
        }
        return counts;
    }

    @Override
    public Set<UUID> listPlayers() {
        Set<UUID> players = new LinkedHashSet<>();
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return players;

        for (File file : files) {
            try {
                players.add(UUID.fromString(file.getName().replace(".yml", "")));
            } catch (IllegalArgumentException ignored) {
                // Archivo ajeno al plugin dentro de la carpeta data.
            }
        }
        return players;
    }
}
