package de.quests.quest;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

/**
 * Eine Quest-Definition. Wird beim Laden der Config erzeugt und ist danach unveraenderlich.
 */
public final class Quest {

    private final String id;
    private final Material icon;
    private final String displayName;
    private final List<String> description;
    private final int target;
    private final QuestType type;
    private final ToolType tool;
    private final Set<Material> materials;
    private final boolean correctToolOnly;
    private final int slot;
    private final QuestReward reward;

    public Quest(String id, Material icon, String displayName, List<String> description, int target,
                 QuestType type, ToolType tool, Set<Material> materials, boolean correctToolOnly,
                 int slot, QuestReward reward) {
        this.id = id;
        this.icon = icon;
        this.displayName = displayName;
        this.description = List.copyOf(description);
        this.target = target;
        this.type = type;
        this.tool = tool;
        this.materials = Set.copyOf(materials);
        this.correctToolOnly = correctToolOnly;
        this.slot = slot;
        this.reward = reward;
    }

    /** Eindeutige ID (Schluessel in der config.yml). */
    public String getId() {
        return id;
    }

    public Material getIcon() {
        return icon;
    }

    /** Anzeigename mit &amp;-Farbcodes. */
    public String getDisplayName() {
        return displayName;
    }

    public List<String> getDescription() {
        return description;
    }

    /** Ziel (Anzahl), das erreicht werden muss. */
    public int getTarget() {
        return target;
    }

    public QuestType getType() {
        return type;
    }

    public ToolType getTool() {
        return tool;
    }

    /** Zaehlende Materialien (leer = alle). */
    public Set<Material> getMaterials() {
        return materials;
    }

    /** true = nur Bloecke zaehlen, die mit dem Werkzeug "korrekt" abgebaut werden. */
    public boolean isCorrectToolOnly() {
        return correctToolOnly;
    }

    /** Gewuenschter GUI-Slot, -1 = automatisch. */
    public int getSlot() {
        return slot;
    }

    /** Belohnung, kann null sein. */
    public QuestReward getReward() {
        return reward;
    }

    /**
     * Prueft, ob der abgebaute Block fuer diese Quest zaehlt.
     */
    public boolean matches(Block block, ItemStack toolItem) {
        return type.matches(this, block, toolItem);
    }
}
