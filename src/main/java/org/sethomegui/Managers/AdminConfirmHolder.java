package org.sethomegui.Managers;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.UUID;

public class AdminConfirmHolder implements InventoryHolder {
    private final UUID targetUuid;
    private final String homeName;

    public AdminConfirmHolder(UUID targetUuid, String homeName) {
        this.targetUuid = targetUuid;
        this.homeName = homeName;
    }

    public UUID getTargetUuid() { return targetUuid; }
    public String getHomeName() { return homeName; }

    @Override
    public Inventory getInventory() { return null; }
}