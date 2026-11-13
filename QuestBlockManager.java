package de.quests.block;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verwaltet die Quest-Bloecke (Rechtsklick oeffnet das Quest-Menue).
 * Die Positionen werden in plugins/Quests/questblocks.yml gespeichert und
 * ueberleben dadurch Server-Neustarts. Die Suche erfolgt ueber ein HashSet in O(1).
 */
public final class QuestBlockManager {

    /** Eindeutiger Schluessel einer Blockposition (Welt-UUID + Koordinaten). */
    public record BlockKey(UUID world, int x, int y, int z) {

        String serialize() {
            return world + ";" + x + ";" + y + ";" + z;
        }

        static BlockKey parse(String text) {
            String[] parts = text.split(";");
            if (parts.length != 4) {
                throw new IllegalArgumentException("Ungueltiges Format: " + text);
            }
            return new BlockKey(UUID.fromString(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        }
    }

    private final File file;
    private final Logger logger;
    private final Set<BlockKey> blocks = new HashSet<>();

    public QuestBlockManager(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "questblocks.yml");
        this.logger = plugin.getLogger();
    }

    /** Laedt die gespeicherten Quest-Bloecke. */
    public void load() {
        blocks.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String entry : yaml.getStringList("blocks")) {
            try {
                blocks.add(BlockKey.parse(entry));
            } catch (IllegalArgumentException ex) {
                logger.warning("Quest-Block-Eintrag ignoriert: " + entry);
            }
        }
        logger.info(blocks.size() + " Quest-Block(s) geladen.");
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> list = new ArrayList<>(blocks.size());
        for (BlockKey key : blocks) {
            list.add(key.serialize());
        }
        yaml.set("blocks", list);
        try {
            yaml.save(file);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Quest-Bloecke konnten nicht gespeichert werden", ex);
        }
    }

    private BlockKey key(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    /** true, wenn mindestens ein Quest-Block existiert (schneller Vorab-Check). */
    public boolean hasAny() {
        return !blocks.isEmpty();
    }

    public boolean isQuestBlock(Block block) {
        return !blocks.isEmpty() && blocks.contains(key(block));
    }

    /**
     * Registriert einen Block als Quest-Block.
     *
     * @return false, wenn er bereits registriert war
     */
    public boolean add(Block block) {
        boolean added = blocks.add(key(block));
        if (added) {
            save();
        }
        return added;
    }

    /**
     * Entfernt einen Quest-Block.
     *
     * @return false, wenn er nicht registriert war
     */
    public boolean remove(Block block) {
        boolean removed = blocks.remove(key(block));
        if (removed) {
            save();
        }
        return removed;
    }

    /** Beschreibung aller Quest-Bloecke fuer die Anzeige (Welt x y z). */
    public List<String> describeAll() {
        List<String> result = new ArrayList<>(blocks.size());
        for (BlockKey key : blocks) {
            org.bukkit.World world = Bukkit.getWorld(key.world());
            String worldName = world != null ? world.getName() : key.world().toString();
            result.add(worldName + " " + key.x() + " " + key.y() + " " + key.z());
        }
        return result;
    }

    /** Hilfsmethode fuer Meldungen: Koordinaten als Text. */
    public static String describe(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + " "
                + location.getBlockY() + " " + location.getBlockZ();
    }
}
