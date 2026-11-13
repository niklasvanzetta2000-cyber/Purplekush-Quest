package de.quests.quest;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Quest-Typen. Jeder Typ entscheidet selbst, ob ein abgebauter Block zaehlt.
 *
 * <p>Neuen Typ hinzufuegen: Konstante mit eigener {@link #matches}-Implementierung ergaenzen.
 * Er ist danach sofort in der config.yml ueber {@code type: NAME} nutzbar.</p>
 */
public enum QuestType {

    /**
     * Zaehlt Bloecke, die mit dem passenden Werkzeug abgebaut werden.
     * Ist eine Material-Liste gesetzt, zaehlen nur diese Materialien.
     */
    BREAK_BLOCKS {
        @Override
        public boolean matches(Quest quest, Block block, ItemStack tool) {
            if (!quest.getTool().matches(tool)) {
                return false;
            }
            if (!quest.getMaterials().isEmpty() && !quest.getMaterials().contains(block.getType())) {
                return false;
            }
            return !quest.isCorrectToolOnly() || block.isPreferredTool(tool);
        }
    },

    /**
     * Zaehlt nur Bloecke aus der Material-Liste der Quest.
     */
    BREAK_SPECIFIC {
        @Override
        public boolean matches(Quest quest, Block block, ItemStack tool) {
            if (!quest.getMaterials().contains(block.getType())) {
                return false;
            }
            if (!quest.getTool().matches(tool)) {
                return false;
            }
            return !quest.isCorrectToolOnly() || block.isPreferredTool(tool);
        }
    };

    /**
     * Prueft, ob der abgebaute Block fuer die Quest zaehlt.
     *
     * @param quest die Quest
     * @param block der abgebaute Block (noch vorhanden, da das Event vor dem Abbau feuert)
     * @param tool  das Item in der Haupthand des Spielers
     */
    public abstract boolean matches(Quest quest, Block block, ItemStack tool);

    /**
     * Liest einen Quest-Typ aus einem Config-Text.
     *
     * @throws IllegalArgumentException wenn der Typ unbekannt ist
     */
    public static QuestType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("type fehlt");
        }
        return QuestType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
