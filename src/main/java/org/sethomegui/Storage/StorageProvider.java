package org.sethomegui.Storage;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Contrato que debe cumplir cualquier backend de almacenamiento de datos de jugador.
 */
public interface StorageProvider {

    /** Nombre legible que se muestra en la consola al arrancar. */
    String getName();

    /** Abre conexiones y prepara el esquema. Debe lanzar excepcion si el backend no es usable. */
    void initialize() throws Exception;

    /** Cierra conexiones y libera recursos. */
    void shutdown();

    /** Devuelve los datos del jugador, o null si nunca ha guardado nada. */
    PlayerRecord load(UUID uuid) throws Exception;

    /** Crea o reemplaza por completo los datos del jugador. */
    void save(UUID uuid, PlayerRecord record) throws Exception;

    /** Borra todos los datos del jugador. */
    void delete(UUID uuid) throws Exception;

    /** Lista los jugadores con datos guardados (lo usa el panel de administracion). */
    Set<UUID> listPlayers() throws Exception;

    /**
     * Numero de hogares de cada jugador en una sola operacion.
     *
     * Existe para que el panel de administracion pueda pintar decenas de cabezas sin lanzar
     * una consulta por jugador contra la base de datos.
     */
    Map<UUID, Integer> countHomesByPlayer() throws Exception;
}
