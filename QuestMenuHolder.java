package de.quests.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Marker-Holder fuer das Quest-Menue. Ueber den Holder wird das Menue sicher erkannt
 * und die Zuordnung Slot -> Quest-ID gehalten. Der Holder lebt nur so lange wie das Inventar.
 */
public final class QuestMenuHolder implements InventoryHolder {

    private Inventory inventory;
    private final Map<Integer, String> slots = new HashMap<>();

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    void clearSlots() {
        slots.clear();
    }

    void mapSlot(int slot, String questId) {
        slots.put(slot, questId);
    }

    /** Quest-ID im Slot oder null. */
    public String getQuestId(int slot) {
        return slots.get(slot);
    }
}
