package de.quests.quest;

import de.quests.QuestsPlugin;
import de.quests.config.ConfigManager;
import de.quests.data.PlayerQuestData;
import de.quests.data.QuestStorage;
import de.quests.effect.EffectManager;
import de.quests.reward.RewardManager;
import de.quests.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Zentrale Verwaltung der Quests und der Spielerdaten.
 *
 * <p>Performance: Beim Laden wird ein Index "Material -> Quests" aufgebaut. Pro abgebautem Block
 * werden dadurch nur die Quests geprueft, die dieses Material betreffen (plus Quests ohne
 * Materialfilter). Es gibt keine Schleifen ueber alle Quests oder Spieler.</p>
 *
 * <p>Alle Methoden ausser dem asynchronen Schreiben laufen im Main-Thread.</p>
 */
public final class QuestManager {

    /** Ergebnis eines Abholversuchs. */
    public enum ClaimResult {
        SUCCESS,
        NOT_COMPLETED,
        ALREADY_CLAIMED
    }

    private final QuestsPlugin plugin;
    private final ConfigManager config;
    private final QuestStorage storage;
    private final RewardManager rewards;
    private final EffectManager effects;

    /** Cache der Daten aller online Spieler (nur Main-Thread). */
    private final Map<UUID, PlayerQuestData> cache = new HashMap<>();

    private Map<String, Quest> quests = Collections.emptyMap();
    private Map<Material, List<Quest>> materialIndex = Collections.emptyMap();
    private List<Quest> genericQuests = Collections.emptyList();
    private boolean trackCreative;
    private BukkitTask autosaveTask;

    public QuestManager(QuestsPlugin plugin, ConfigManager config, QuestStorage storage,
                        RewardManager rewards, EffectManager effects) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        this.rewards = rewards;
        this.effects = effects;
    }

    // ------------------------------------------------------------------
    // Quests laden
    // ------------------------------------------------------------------

    /** Liest die Quests aus der Config neu ein und baut den Material-Index auf. */
    public void reloadQuests() {
        Map<String, Quest> parsed = config.parseQuests();
        Map<Material, List<Quest>> index = new EnumMap<>(Material.class);
        List<Quest> generic = new ArrayList<>();
        for (Quest quest : parsed.values()) {
            if (quest.getMaterials().isEmpty()) {
                generic.add(quest);
            } else {
                for (Material material : quest.getMaterials()) {
                    index.computeIfAbsent(material, key -> new ArrayList<>()).add(quest);
                }
            }
        }
        this.quests = Collections.unmodifiableMap(parsed);
        this.materialIndex = index;
        this.genericQuests = List.copyOf(generic);
        this.trackCreative = config.cfg().getBoolean("tracking.count-creative", false);
        plugin.getLogger().info(parsed.size() + " Quest(s) geladen.");
    }

    /** Alle Quests in Config-Reihenfolge. */
    public Map<String, Quest> getQuests() {
        return quests;
    }

    public Quest getQuest(String id) {
        return quests.get(id);
    }

    public boolean isTrackCreative() {
        return trackCreative;
    }

    // ------------------------------------------------------------------
    // Spielerdaten
    // ------------------------------------------------------------------

    /** Laedt die Daten eines online Spielers in den Cache (falls noch nicht vorhanden). */
    public PlayerQuestData loadPlayer(UUID uuid) {
        return cache.computeIfAbsent(uuid, storage::load);
    }

    /** Liefert die Daten eines online Spielers. */
    public PlayerQuestData getData(UUID uuid) {
        return loadPlayer(uuid);
    }

    /**
     * Liefert die Daten eines beliebigen Spielers: aus dem Cache (online) oder frisch von der
     * Festplatte (offline, wird nicht gecacht).
     */
    public PlayerQuestData peek(UUID uuid) {
        PlayerQuestData cached = cache.get(uuid);
        return cached != null ? cached : storage.load(uuid);
    }

    /** Entfernt einen Spieler aus dem Cache und speichert ihn asynchron, falls noetig. */
    public void unloadPlayer(UUID uuid) {
        PlayerQuestData data = cache.remove(uuid);
        if (data == null || !data.isDirty()) {
            return;
        }
        PlayerQuestData.Snapshot snapshot = data.snapshot();
        data.markClean();
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> storage.save(snapshot));
        } else {
            storage.save(snapshot);
        }
    }

    /** Speichert alle geaenderten Daten synchron (beim Abschalten). */
    public void saveAll() {
        for (PlayerQuestData data : cache.values()) {
            if (data.isDirty()) {
                storage.save(data.snapshot());
                data.markClean();
            }
        }
    }

    /** Startet (bzw. startet neu) das regelmaessige asynchrone Speichern. */
    public void startAutosave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        long seconds = Math.max(30L, config.cfg().getLong("storage.autosave-seconds", 300L));
        autosaveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::autosave, seconds * 20L, seconds * 20L);
    }

    private void autosave() {
        List<PlayerQuestData.Snapshot> snapshots = new ArrayList<>();
        for (PlayerQuestData data : cache.values()) {
            if (data.isDirty()) {
                snapshots.add(data.snapshot());
                data.markClean();
            }
        }
        if (snapshots.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (PlayerQuestData.Snapshot snapshot : snapshots) {
                storage.save(snapshot);
            }
        });
    }

    // ------------------------------------------------------------------
    // Tracking
    // ------------------------------------------------------------------

    /**
     * Wird bei jedem abgebauten Block aufgerufen und erhoeht den Fortschritt passender Quests.
     */
    public void handleBlockBreak(Player player, Block block, ItemStack tool) {
        if (quests.isEmpty()) {
            return;
        }
        List<Quest> specific = materialIndex.get(block.getType());
        if (specific == null && genericQuests.isEmpty()) {
            return;
        }
        PlayerQuestData data = getData(player.getUniqueId());
        if (specific != null) {
            for (Quest quest : specific) {
                track(player, data, quest, block, tool);
            }
        }
        for (Quest quest : genericQuests) {
            track(player, data, quest, block, tool);
        }
    }

    private void track(Player player, PlayerQuestData data, Quest quest, Block block, ItemStack tool) {
        String id = quest.getId();
        if (data.isClaimed(id) || data.getProgress(id) >= quest.getTarget()) {
            return;
        }
        if (!quest.matches(block, tool)) {
            return;
        }
        int value = data.addProgress(id, 1, quest.getTarget());
        if (value >= quest.getTarget() && data.markCompleted(id)) {
            player.sendMessage(config.msg("quest-completed", "{quest}", TextUtil.strip(quest.getDisplayName())));
            effects.play(player, "quest-complete");
        }
    }

    // ------------------------------------------------------------------
    // Status & Belohnung
    // ------------------------------------------------------------------

    /** Ermittelt den Status einer Quest fuer die Daten eines Spielers. */
    public QuestStatus getStatus(PlayerQuestData data, Quest quest) {
        if (data.isClaimed(quest.getId())) {
            return QuestStatus.CLAIMED;
        }
        if (data.getProgress(quest.getId()) >= quest.getTarget()) {
            return QuestStatus.CLAIMABLE;
        }
        return QuestStatus.IN_PROGRESS;
    }

    /**
     * Versucht, die Belohnung abzuholen. Die Quest wird VOR der Uebergabe als abgeholt markiert und
     * sofort gespeichert, damit sie weder doppelt abgeholt noch nach einem Absturz erneut vergeben wird.
     */
    public ClaimResult claim(Player player, Quest quest) {
        PlayerQuestData data = getData(player.getUniqueId());
        String id = quest.getId();
        if (data.isClaimed(id)) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        if (data.getProgress(id) < quest.getTarget()) {
            return ClaimResult.NOT_COMPLETED;
        }
        data.markCompleted(id);
        data.markClaimed(id);
        storage.save(data.snapshot());
        data.markClean();
        try {
            rewards.give(player, quest);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Fehler bei der Belohnungsvergabe von Quest " + id, ex);
            data.unmarkClaimed(id);
            storage.save(data.snapshot());
            data.markClean();
            throw ex;
        }
        return ClaimResult.SUCCESS;
    }

    // ------------------------------------------------------------------
    // Admin
    // ------------------------------------------------------------------

    /**
     * Setzt den Fortschritt eines Spielers zurueck (online oder offline) und speichert sofort.
     *
     * @param questId ID der Quest oder null fuer alle Quests
     */
    public void resetProgress(UUID uuid, String questId) {
        PlayerQuestData data = peek(uuid);
        if (questId == null) {
            data.resetAll();
        } else {
            data.reset(questId);
        }
        storage.save(data.snapshot());
        data.markClean();
    }
}
