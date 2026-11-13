package de.quests.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Fortschrittsdaten eines einzelnen Spielers.
 * Wird ausschliesslich im Main-Thread veraendert. Fuer asynchrones Speichern
 * wird vorher mit {@link #snapshot()} eine unabhaengige Kopie erzeugt.
 */
public final class PlayerQuestData {

    /** Unveraenderliche Kopie der Daten fuer das Speichern in einem anderen Thread. */
    public record Snapshot(UUID uuid, Map<String, Integer> progress, Set<String> completed, Set<String> claimed) {
    }

    private final UUID uuid;
    private final Map<String, Integer> progress = new HashMap<>();
    private final Set<String> completed = new HashSet<>();
    private final Set<String> claimed = new HashSet<>();
    private boolean dirty;

    public PlayerQuestData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    /** Aktueller Fortschritt einer Quest (0 wenn noch nichts gezaehlt wurde). */
    public int getProgress(String questId) {
        return progress.getOrDefault(questId, 0);
    }

    /**
     * Erhoeht den Fortschritt, maximal bis zum Ziel.
     *
     * @return der neue Fortschritt
     */
    public int addProgress(String questId, int amount, int target) {
        int value = Math.min(target, getProgress(questId) + amount);
        progress.put(questId, value);
        dirty = true;
        return value;
    }

    public boolean isCompleted(String questId) {
        return completed.contains(questId);
    }

    /**
     * Markiert eine Quest als abgeschlossen.
     *
     * @return true, wenn sie vorher noch nicht abgeschlossen war
     */
    public boolean markCompleted(String questId) {
        boolean added = completed.add(questId);
        if (added) {
            dirty = true;
        }
        return added;
    }

    public boolean isClaimed(String questId) {
        return claimed.contains(questId);
    }

    public void markClaimed(String questId) {
        if (claimed.add(questId)) {
            dirty = true;
        }
    }

    public void unmarkClaimed(String questId) {
        if (claimed.remove(questId)) {
            dirty = true;
        }
    }

    /** Setzt eine einzelne Quest zurueck (Fortschritt, Abschluss, Abholung). */
    public void reset(String questId) {
        progress.remove(questId);
        completed.remove(questId);
        claimed.remove(questId);
        dirty = true;
    }

    /** Setzt alle Quests zurueck. */
    public void resetAll() {
        progress.clear();
        completed.clear();
        claimed.clear();
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        dirty = false;
    }

    /** Fuer das Laden aus der Datei (setzt das Dirty-Flag nicht). */
    public void loadProgress(String questId, int value) {
        progress.put(questId, Math.max(0, value));
    }

    /** Fuer das Laden aus der Datei. */
    public void loadCompleted(Iterable<String> ids) {
        ids.forEach(completed::add);
    }

    /** Fuer das Laden aus der Datei. */
    public void loadClaimed(Iterable<String> ids) {
        ids.forEach(claimed::add);
    }

    /** Erzeugt eine unabhaengige Kopie fuer das (asynchrone) Speichern. */
    public Snapshot snapshot() {
        return new Snapshot(uuid, new HashMap<>(progress), new HashSet<>(completed), new HashSet<>(claimed));
    }
}
