package org.sethomegui.Storage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fotografia completa de los datos de un jugador lista para viajar a cualquier backend.
 */
public class PlayerRecord {

    private final UUID uuid;
    private List<HomeEntry> homes = new ArrayList<>();
    private List<String> friends = new ArrayList<>();

    /**
     * YAML con las claves que el esquema no contempla. Actua de red de seguridad para que
     * ningun dato personalizado se pierda al pasar por una base de datos relacional.
     */
    private String extraData;

    public PlayerRecord(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() { return uuid; }

    public List<HomeEntry> getHomes() { return homes; }
    public void setHomes(List<HomeEntry> homes) {
        this.homes = homes == null ? new ArrayList<>() : homes;
    }

    public List<String> getFriends() { return friends; }
    public void setFriends(List<String> friends) {
        this.friends = friends == null ? new ArrayList<>() : friends;
    }

    public String getExtraData() { return extraData; }
    public void setExtraData(String extraData) { this.extraData = extraData; }

    public boolean isEmpty() {
        return homes.isEmpty() && friends.isEmpty() && (extraData == null || extraData.trim().isEmpty());
    }
}
