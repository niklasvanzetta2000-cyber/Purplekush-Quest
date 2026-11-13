package de.quests.quest;

/**
 * Anzeigestatus einer Quest fuer einen Spieler.
 */
public enum QuestStatus {

    /** Ziel noch nicht erreicht. */
    IN_PROGRESS,

    /** Ziel erreicht, Belohnung wurde noch nicht abgeholt. */
    CLAIMABLE,

    /** Belohnung wurde bereits abgeholt. */
    CLAIMED
}
