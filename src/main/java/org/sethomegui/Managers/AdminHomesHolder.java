package org.sethomegui.Managers;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.UUID;

public class AdminHomesHolder implements InventoryHolder {
    private final UUID auditedPlayerUuid;

    public AdminHomesHolder(UUID auditedPlayerUuid) {
        this.auditedPlayerUuid = auditedPlayerUuid;
    }

    public UUID getAuditedPlayerUuid() { return auditedPlayerUuid; }

    @Override
    public Inventory getInventory() { return null; }
}