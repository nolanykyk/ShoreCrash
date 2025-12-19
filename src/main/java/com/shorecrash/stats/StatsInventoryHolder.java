package com.shorecrash.stats;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class StatsInventoryHolder implements InventoryHolder {
    private final String target;

    public StatsInventoryHolder(String target) {
        this.target = target;
    }

    public String getTarget() {
        return target;
    }

    @Override
    public Inventory getInventory() {
        return null; // unused; Bukkit populates inventory externally
    }
}
