package de.quests.quest;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Werkzeugtyp, der fuer eine Quest benoetigt wird.
 * ANY bedeutet: egal womit abgebaut wird (auch mit der Hand).
 */
public enum ToolType {

    ANY,
    PICKAXE,
    AXE,
    SHOVEL,
    HOE,
    SHEARS,
    SWORD;

    /**
     * Prueft, ob das uebergebene Item dem Werkzeugtyp entspricht.
     * Die Pruefung erfolgt ueber den Materialnamen (z. B. DIAMOND_PICKAXE endet auf _PICKAXE),
     * dadurch werden alle Materialstufen automatisch unterstuetzt.
     */
    public boolean matches(ItemStack item) {
        if (this == ANY) {
            return true;
        }
        if (item == null || item.getType().isAir()) {
            return false;
        }
        Material type = item.getType();
        if (this == SHEARS) {
            return type == Material.SHEARS;
        }
        return type.name().endsWith("_" + name());
    }

    /**
     * Liest einen Werkzeugtyp aus einem Config-Text. Leer/null ergibt ANY.
     *
     * @throws IllegalArgumentException wenn der Wert unbekannt ist
     */
    public static ToolType fromString(String value) {
        if (value == null || value.isBlank()) {
            return ANY;
        }
        return ToolType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
