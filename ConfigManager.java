package de.quests.config;

import de.quests.QuestsPlugin;
import de.quests.quest.Quest;
import de.quests.quest.QuestReward;
import de.quests.quest.QuestType;
import de.quests.quest.ToolType;
import de.quests.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Arrays;

/**
 * Laedt die config.yml und wandelt die Quest-Eintraege in {@link Quest}-Objekte um.
 * Fehlerhafte Quests werden uebersprungen und mit einer Warnung in der Konsole gemeldet.
 */
public final class ConfigManager {

    private final QuestsPlugin plugin;
    private FileConfiguration config;
    private Locale locale = Locale.GERMANY;

    public ConfigManager(QuestsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Laedt die Config von der Festplatte (erstellt sie bei Bedarf). */
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        this.locale = Locale.forLanguageTag(config.getString("number-locale", "de-DE"));
    }

    public FileConfiguration cfg() {
        return config;
    }

    /** Locale fuer Zahlenformate (Tausenderpunkte, Dezimalkomma). */
    public Locale getLocale() {
        return locale;
    }

    /** Liefert einen Config-Text (mit Standardwert). */
    public String raw(String path, String def) {
        return config.getString(path, def);
    }

    /**
     * Liefert eine Nachricht aus dem Bereich "messages" als Text (ohne Prefix).
     *
     * @param pairs Platzhalter/Wert-Paare, z. B. "{player}", "Steve"
     */
    public String message(String key, String... pairs) {
        String text = config.getString("messages." + key, "&cFehlende Nachricht: " + key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace(pairs[i], pairs[i + 1]);
        }
        return text;
    }

    /** Wie {@link #message}, aber mit Prefix und als Component. */
    public Component msg(String key, String... pairs) {
        return TextUtil.color(config.getString("messages.prefix", "") + message(key, pairs));
    }

    // ------------------------------------------------------------------
    // Quests einlesen
    // ------------------------------------------------------------------

    /**
     * Liest alle Quests aus der Config. Die Reihenfolge entspricht der Reihenfolge in der Datei.
     */
    public Map<String, Quest> parseQuests() {
        Map<String, Quest> result = new LinkedHashMap<>();
        ConfigurationSection root = config.getConfigurationSection("quests");
        if (root == null) {
            plugin.getLogger().warning("In der config.yml gibt es keinen Abschnitt 'quests'.");
            return result;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            if (!section.getBoolean("enabled", true)) {
                continue;
            }
            try {
                if (id.contains(".")) {
                    throw new IllegalArgumentException("Die Quest-ID darf keinen Punkt enthalten");
                }
                result.put(id, parseQuest(id, section));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Quest '" + id + "' uebersprungen: " + ex.getMessage());
            }
        }
        return result;
    }

    private Quest parseQuest(String id, ConfigurationSection s) {
        Material icon = Material.matchMaterial(s.getString("icon", ""));
        if (icon == null || icon.isAir() || !icon.isItem()) {
            throw new IllegalArgumentException("Ungueltiges icon: " + s.getString("icon"));
        }
        String displayName = s.getString("display-name", id);

        List<String> description = new ArrayList<>();
        if (s.isList("description")) {
            description.addAll(s.getStringList("description"));
        } else if (s.isString("description")) {
            description.add(s.getString("description"));
        }

        int target = s.getInt("target", 0);
        if (target < 1) {
            throw new IllegalArgumentException("target muss mindestens 1 sein");
        }

        QuestType type;
        try {
            type = QuestType.fromString(s.getString("type"));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unbekannter type: " + s.getString("type")
                    + " (erlaubt: " + Arrays.toString(QuestType.values()) + ")");
        }

        ToolType tool;
        try {
            tool = ToolType.fromString(s.getString("tool"));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unbekanntes tool: " + s.getString("tool")
                    + " (erlaubt: " + Arrays.toString(ToolType.values()) + ")");
        }

        Set<Material> materials = resolveMaterials(s.getStringList("materials"), id);
        if (type == QuestType.BREAK_SPECIFIC && materials.isEmpty()) {
            throw new IllegalArgumentException("BREAK_SPECIFIC braucht eine nicht-leere Liste 'materials'");
        }

        // Standard: bei einem Werkzeug zaehlen nur Bloecke, die damit korrekt abgebaut werden.
        boolean correctToolOnly = s.getBoolean("correct-tool-only", type == QuestType.BREAK_BLOCKS && tool != ToolType.ANY);

        int slot = s.getInt("slot", -1);
        QuestReward reward = parseReward(s.getConfigurationSection("reward"), id);

        return new Quest(id, icon, displayName, description, target, type, tool, materials,
                correctToolOnly, slot, reward);
    }

    /**
     * Loest eine Materialliste auf. Unterstuetzt Wildcards, z. B. "*_LOG" oder "*_ORE".
     * Es werden nur echte Bloecke akzeptiert.
     */
    private Set<Material> resolveMaterials(List<String> entries, String questId) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String entry : entries) {
            String name = entry.trim().toUpperCase(Locale.ROOT);
            if (name.isEmpty()) {
                continue;
            }
            if (name.contains("*")) {
                String regex = Arrays.stream(name.split("\\*", -1))
                        .map(Pattern::quote)
                        .collect(Collectors.joining(".*"));
                Pattern pattern = Pattern.compile("^" + regex + "$");
                int before = set.size();
                for (Material material : Material.values()) {
                    if (!material.isLegacy() && material.isBlock() && pattern.matcher(material.name()).matches()) {
                        set.add(material);
                    }
                }
                if (set.size() == before) {
                    plugin.getLogger().warning("Quest '" + questId + "': Muster '" + entry + "' trifft auf keinen Block zu.");
                }
                continue;
            }
            Material material = Material.matchMaterial(name);
            if (material == null || !material.isBlock()) {
                plugin.getLogger().warning("Quest '" + questId + "': '" + entry + "' ist kein gueltiger Block und wird ignoriert.");
                continue;
            }
            set.add(material);
        }
        return set;
    }

    private QuestReward parseReward(ConfigurationSection s, String questId) {
        if (s == null) {
            return null;
        }
        Material material = null;
        String materialName = s.getString("material");
        if (materialName != null) {
            material = Material.matchMaterial(materialName);
            if (material == null || material.isAir() || !material.isItem()) {
                throw new IllegalArgumentException("Ungueltiges reward.material: " + materialName);
            }
        }
        List<String> commands = s.getStringList("commands");
        if (material == null && commands.isEmpty()) {
            throw new IllegalArgumentException("reward braucht 'material' und/oder 'commands'");
        }

        Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
        ConfigurationSection enchantSection = s.getConfigurationSection("enchants");
        if (enchantSection != null) {
            for (String key : enchantSection.getKeys(false)) {
                Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
                if (enchantment == null) {
                    plugin.getLogger().warning("Quest '" + questId + "': unbekannte Verzauberung '" + key + "'");
                    continue;
                }
                enchants.put(enchantment, Math.max(1, enchantSection.getInt(key, 1)));
            }
        }

        List<ItemFlag> flags = new ArrayList<>();
        for (String flagName : s.getStringList("item-flags")) {
            try {
                flags.add(ItemFlag.valueOf(flagName.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Quest '" + questId + "': unbekanntes ItemFlag '" + flagName + "'");
            }
        }

        Integer customModelData = s.isSet("custom-model-data") ? s.getInt("custom-model-data") : null;

        return new QuestReward(
                material,
                Math.max(1, s.getInt("amount", 1)),
                s.getString("name"),
                s.getStringList("lore"),
                Collections.unmodifiableMap(enchants),
                List.copyOf(flags),
                s.getBoolean("unbreakable", false),
                customModelData,
                List.copyOf(commands),
                s.getString("display"));
    }
}
