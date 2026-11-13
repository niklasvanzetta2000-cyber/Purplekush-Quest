package de.quests.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Speichert und laedt Spielerdaten als YAML-Datei pro Spieler
 * (plugins/Quests/playerdata/&lt;uuid&gt;.yml).
 *
 * <p>Schreibvorgaenge sind synchronisiert und atomar (erst temporaere Datei, dann Verschieben),
 * damit weder paralleles Speichern noch ein Absturz Dateien beschaedigt.</p>
 */
public final class QuestStorage {

    private final File folder;
    private final Logger logger;

    public QuestStorage(JavaPlugin plugin) {
        this.folder = new File(plugin.getDataFolder(), "playerdata");
        this.logger = plugin.getLogger();
        if (!folder.exists() && !folder.mkdirs()) {
            logger.warning("Ordner konnte nicht erstellt werden: " + folder.getAbsolutePath());
        }
    }

    private File file(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }

    /**
     * Laedt die Daten eines Spielers. Existiert keine Datei, werden leere Daten geliefert.
     * Eine defekte Datei wird als .corrupt gesichert, damit kein Fortschritt still ueberschrieben wird.
     */
    public PlayerQuestData load(UUID uuid) {
        PlayerQuestData data = new PlayerQuestData(uuid);
        File file = file(uuid);
        if (!file.exists()) {
            return data;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            logger.log(Level.SEVERE, "Spielerdatei defekt: " + file.getName() + " - sichere als .corrupt", ex);
            File backup = new File(folder, uuid + ".yml.corrupt");
            try {
                Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveEx) {
                logger.log(Level.WARNING, "Sicherung der defekten Datei fehlgeschlagen", moveEx);
            }
            return data;
        }
        ConfigurationSection progress = yaml.getConfigurationSection("progress");
        if (progress != null) {
            for (String key : progress.getKeys(false)) {
                data.loadProgress(key, progress.getInt(key));
            }
        }
        data.loadCompleted(yaml.getStringList("completed"));
        data.loadClaimed(yaml.getStringList("claimed"));
        data.markClean();
        return data;
    }

    /**
     * Schreibt eine Kopie der Daten auf die Festplatte. Darf aus jedem Thread aufgerufen werden.
     */
    public synchronized void save(PlayerQuestData.Snapshot snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Integer> entry : snapshot.progress().entrySet()) {
            yaml.set("progress." + entry.getKey(), entry.getValue());
        }
        yaml.set("completed", new ArrayList<>(snapshot.completed()));
        yaml.set("claimed", new ArrayList<>(snapshot.claimed()));

        File target = file(snapshot.uuid());
        File temp = new File(folder, snapshot.uuid() + ".yml.tmp");
        try {
            yaml.save(temp);
            try {
                Files.move(temp.toPath(), target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Spielerdaten konnten nicht gespeichert werden: " + snapshot.uuid(), ex);
        }
    }
}
