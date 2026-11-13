package de.quests.effect;

import de.quests.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Spielt Sounds und Partikel ab. Die Effekte werden beim Laden der Config einmal
 * aufgeloest und danach nur noch aus dem Speicher gelesen (keine Registry-Suche pro Abspielen).
 */
public final class EffectManager {

    private record ParticleSpec(Particle particle, int count, double offset, double speed) {
    }

    private record EffectSpec(Sound sound, float volume, float pitch, List<ParticleSpec> particles) {
    }

    private final ConfigManager config;
    private final Logger logger;
    private final Map<String, EffectSpec> specs = new HashMap<>();

    public EffectManager(ConfigManager config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    /** Liest den Abschnitt "effects" der Config neu ein. */
    public void reload() {
        specs.clear();
        ConfigurationSection root = config.cfg().getConfigurationSection("effects");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            Sound sound = resolveSound(section.getString("sound"), key);
            float volume = (float) section.getDouble("volume", 1.0D);
            float pitch = (float) section.getDouble("pitch", 1.0D);

            List<ParticleSpec> particles = new ArrayList<>();
            for (Map<?, ?> entry : section.getMapList("particles")) {
                Object typeObject = entry.get("type");
                Particle particle = resolveParticle(typeObject == null ? null : typeObject.toString(), key);
                if (particle == null) {
                    continue;
                }
                particles.add(new ParticleSpec(
                        particle,
                        number(entry.get("count"), 10).intValue(),
                        number(entry.get("offset"), 0.5D).doubleValue(),
                        number(entry.get("speed"), 0.0D).doubleValue()));
            }
            specs.put(key, new EffectSpec(sound, volume, pitch, List.copyOf(particles)));
        }
    }

    /** Spielt den Effekt mit dem angegebenen Config-Schluessel (Sound + Partikel) fuer den Spieler ab. */
    public void play(Player player, String key) {
        EffectSpec spec = specs.get(key);
        if (spec == null) {
            return;
        }
        if (spec.sound() != null) {
            player.playSound(player.getLocation(), spec.sound(), spec.volume(), spec.pitch());
        }
        if (!spec.particles().isEmpty()) {
            Location location = player.getLocation().add(0, 1, 0);
            for (ParticleSpec particle : spec.particles()) {
                player.getWorld().spawnParticle(particle.particle(), location, particle.count(),
                        particle.offset(), particle.offset(), particle.offset(), particle.speed());
            }
        }
    }

    private Number number(Object value, Number def) {
        return value instanceof Number number ? number : def;
    }

    /**
     * Loest einen Sound-Namen auf. Erlaubt sind z. B. ENTITY_PLAYER_LEVELUP oder
     * die Schreibweise mit Punkten (entity.player.levelup).
     */
    private Sound resolveSound(String name, String effectKey) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String lower = name.trim().toLowerCase(Locale.ROOT);
        String keyName = lower.contains(".") ? lower : lower.replace('_', '.');
        try {
            Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(keyName));
            if (sound == null) {
                logger.warning("Effekt '" + effectKey + "': unbekannter Sound '" + name + "'");
            }
            return sound;
        } catch (IllegalArgumentException ex) {
            logger.warning("Effekt '" + effectKey + "': ungueltiger Sound '" + name + "'");
            return null;
        }
    }

    /** Loest einen Partikel-Namen auf (z. B. HAPPY_VILLAGER, TOTEM_OF_UNDYING). */
    private Particle resolveParticle(String name, String effectKey) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            Particle particle = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(name.trim().toLowerCase(Locale.ROOT)));
            if (particle == null) {
                logger.warning("Effekt '" + effectKey + "': unbekannter Partikel '" + name + "'");
                return null;
            }
            if (particle.getDataType() != Void.class) {
                logger.warning("Effekt '" + effectKey + "': Partikel '" + name + "' benoetigt Zusatzdaten und wird nicht unterstuetzt");
                return null;
            }
            return particle;
        } catch (IllegalArgumentException ex) {
            logger.warning("Effekt '" + effectKey + "': ungueltiger Partikel '" + name + "'");
            return null;
        }
    }
}
