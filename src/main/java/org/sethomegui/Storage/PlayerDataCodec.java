package org.sethomegui.Storage;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import dev.dejvokep.boostedyaml.route.Route;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Traduce entre el documento YAML que maneja todo el plugin y el {@link PlayerRecord}
 * neutral que entienden los backends de base de datos.
 *
 * Gracias a esta clase, MySQL guarda los datos en tablas normalizadas de verdad sin que
 * el resto del plugin tenga que dejar de trabajar con YamlDocument.
 */
public final class PlayerDataCodec {

    /** Claves de primer nivel que no describen un hogar. */
    private static final String KEY_HOMES = "homes";
    private static final String KEY_FRIENDS = "friends";

    private PlayerDataCodec() {
    }

    /**
     * Crea un documento vacio y manipulable a partir de texto YAML (admite null o vacio).
     */
    public static YamlDocument documentFrom(String yaml) throws IOException {
        String content = (yaml == null || yaml.trim().isEmpty()) ? "{}" : yaml;
        return YamlDocument.create(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                GeneralSettings.DEFAULT
        );
    }

    /**
     * Extrae los datos estructurados de un documento de jugador.
     */
    public static PlayerRecord toRecord(UUID uuid, Section document) throws IOException {
        PlayerRecord record = new PlayerRecord(uuid);
        if (document == null) return record;

        List<String> homeNames = document.getStringList(KEY_HOMES);
        if (homeNames == null) homeNames = new ArrayList<>();

        List<HomeEntry> homes = new ArrayList<>();
        for (String homeName : homeNames) {
            if (homeName == null || homeName.isEmpty()) continue;

            HomeEntry entry = new HomeEntry(homeName);
            entry.setWorld(document.getString(homeName + ".world", "world"));
            entry.setX(document.getDouble(homeName + ".x", 0.0D));
            entry.setY(document.getDouble(homeName + ".y", 0.0D));
            entry.setZ(document.getDouble(homeName + ".z", 0.0D));
            entry.setYaw(document.getDouble(homeName + ".yaw", 0.0D));
            entry.setPitch(document.getDouble(homeName + ".pitch", 0.0D));
            entry.setIcon(document.getString(homeName + ".icon", null));
            entry.setAccess(document.getString(homeName + ".access", "nobody"));

            List<String> allowed = document.getStringList(homeName + ".allowed-friends");
            entry.setAllowedFriends(allowed == null ? new ArrayList<>() : new ArrayList<>(allowed));

            homes.add(entry);
        }
        record.setHomes(homes);

        List<String> friends = document.getStringList(KEY_FRIENDS);
        record.setFriends(friends == null ? new ArrayList<>() : new ArrayList<>(friends));

        record.setExtraData(extractExtraData(document, homeNames));
        return record;
    }

    /**
     * Reconstruye el texto YAML completo del jugador a partir de sus datos estructurados.
     */
    public static String toYaml(PlayerRecord record) throws IOException {
        YamlDocument document = documentFrom(record.getExtraData());

        List<String> names = new ArrayList<>();
        for (HomeEntry home : record.getHomes()) {
            if (home.getName() == null || home.getName().isEmpty()) continue;
            names.add(home.getName());

            String base = home.getName();
            document.set(base + ".world", home.getWorld());
            document.set(base + ".x", home.getX());
            document.set(base + ".y", home.getY());
            document.set(base + ".z", home.getZ());
            document.set(base + ".yaw", home.getYaw());
            document.set(base + ".pitch", home.getPitch());

            if (home.getIcon() != null && !home.getIcon().isEmpty()) {
                document.set(base + ".icon", home.getIcon());
            }
            // Solo escribimos el modo de acceso cuando no es el valor por defecto,
            // para que el YAML resultante conserve la forma original del archivo.
            if (home.getAccess() != null && !home.getAccess().equalsIgnoreCase("nobody")) {
                document.set(base + ".access", home.getAccess());
            }
            if (!home.getAllowedFriends().isEmpty()) {
                document.set(base + ".allowed-friends", new ArrayList<>(home.getAllowedFriends()));
            }
        }

        document.set(KEY_HOMES, names);
        if (!record.getFriends().isEmpty()) {
            document.set(KEY_FRIENDS, new ArrayList<>(record.getFriends()));
        }

        return document.dump();
    }

    /**
     * Copia el contenido de una seccion a un YamlDocument nuevo y limpio.
     *
     * Es necesario porque SnakeYAML resuelve sus serializadores por clase exacta: cualquier
     * subclase de YamlDocument (como {@link PlayerData}) revienta al llamar a dump().
     * Copiando ruta por ruta obtenemos un documento estandar que si se puede serializar.
     */
    public static YamlDocument plainCopy(Section source) throws IOException {
        YamlDocument copy = documentFrom(null);
        if (source == null) return copy;

        Map<Route, Object> values = source.getRouteMappedValues(true);
        for (Map.Entry<Route, Object> entry : values.entrySet()) {
            // Las secciones intermedias se recrean solas al asignar sus hojas
            if (entry.getValue() instanceof Section) continue;
            copy.set(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    /**
     * Devuelve el YAML de las claves que el esquema relacional no cubre, o null si no hay ninguna.
     * Sin esto, cualquier dato personalizado se perderia al guardar en MySQL.
     */
    private static String extractExtraData(Section document, List<String> homeNames) throws IOException {
        YamlDocument leftovers = plainCopy(document);

        leftovers.remove(KEY_HOMES);
        leftovers.remove(KEY_FRIENDS);

        // El nombre de un hogar puede contener puntos, en cuyo caso ocupa una rama anidada;
        // basta con descartar su primer segmento para no duplicarlo en extra-data.
        Set<String> consumed = new HashSet<>();
        for (String homeName : homeNames) {
            if (homeName == null || homeName.isEmpty()) continue;
            int dot = homeName.indexOf('.');
            consumed.add(dot >= 0 ? homeName.substring(0, dot) : homeName);
        }
        for (String key : consumed) {
            leftovers.remove(key);
        }

        if (leftovers.getKeys().isEmpty()) return null;
        return leftovers.dump();
    }
}
