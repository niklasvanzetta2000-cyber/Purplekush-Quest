package de.quests.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hilfsmethoden fuer Texte, Farben und Zahlenformatierung.
 * Alle Methoden sind statisch und zustandslos (thread-safe).
 */
public final class TextUtil {

    /** Serializer fuer &-Farbcodes inklusive Hex-Farben im Format &#RRGGBB. */
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    /** Serializer, der Formatierungen entfernt und reinen Text liefert. */
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] ROMAN_SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    private TextUtil() {
        // Utility-Klasse, keine Instanzen
    }

    /**
     * Wandelt einen Text mit &-Farbcodes (und §-Codes) in eine Component um.
     */
    public static Component color(String text) {
        if (text == null) {
            return Component.empty();
        }
        return LEGACY.deserialize(text.replace('§', '&'));
    }

    /**
     * Wie {@link #color(String)}, schaltet aber die standardmaessige Kursivschrift
     * von Item-Namen und Lore ab.
     */
    public static Component item(String text) {
        return color(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /**
     * Wandelt eine Liste von Texten in Item-Lore-Components um.
     */
    public static List<Component> itemLore(List<String> lines) {
        List<Component> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(item(line));
        }
        return result;
    }

    /**
     * Entfernt alle Farben/Formatierungen und liefert reinen Text.
     */
    public static String strip(String text) {
        return PLAIN.serialize(color(text));
    }

    /**
     * Formatiert eine Ganzzahl mit Tausendertrennzeichen (z. B. 3.548).
     */
    public static String formatNumber(Locale locale, long value) {
        return NumberFormat.getIntegerInstance(locale).format(value);
    }

    /**
     * Berechnet den Prozentwert (eine Nachkommastelle, abgerundet) als Text.
     * Es wird nur dann "100" angezeigt, wenn das Ziel wirklich erreicht ist.
     */
    public static String formatPercent(Locale locale, int progress, int target) {
        if (target <= 0 || progress >= target) {
            return "100";
        }
        double percent = Math.floor((progress * 1000.0D) / target) / 10.0D;
        DecimalFormat format = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(locale));
        return format.format(percent);
    }

    /**
     * Baut einen Fortschrittsbalken aus einzelnen Zeichen.
     */
    public static String progressBar(int progress, int target, int length, String fillChar,
                                     String emptyChar, String fillColor, String emptyColor) {
        int safeLength = Math.max(1, length);
        double ratio = target <= 0 ? 1.0D : Math.min(1.0D, (double) progress / target);
        int filled = (int) Math.floor(ratio * safeLength);
        StringBuilder builder = new StringBuilder(safeLength * 2 + 8);
        builder.append(fillColor);
        builder.append(fillChar.repeat(filled));
        builder.append(emptyColor);
        builder.append(emptyChar.repeat(safeLength - filled));
        return builder.toString();
    }

    /**
     * Wandelt eine Zahl in roemische Ziffern um (fuer Enchantment-Stufen).
     */
    public static String roman(int number) {
        if (number <= 0 || number > 3999) {
            return String.valueOf(number);
        }
        StringBuilder builder = new StringBuilder();
        int remaining = number;
        for (int i = 0; i < ROMAN_VALUES.length; i++) {
            while (remaining >= ROMAN_VALUES[i]) {
                builder.append(ROMAN_SYMBOLS[i]);
                remaining -= ROMAN_VALUES[i];
            }
        }
        return builder.toString();
    }

    /**
     * Macht aus einem Enum-/Key-Namen einen lesbaren Text (DIAMOND_PICKAXE -> Diamond Pickaxe).
     */
    public static String prettify(String name) {
        String[] parts = name.toLowerCase(Locale.ROOT).split("[_ ]");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }
}
