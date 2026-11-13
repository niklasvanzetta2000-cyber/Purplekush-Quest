package de.quests.quest;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;

import java.util.List;
import java.util.Map;

/**
 * Unveraenderliche Beschreibung einer Belohnung (aus der Config gelesen).
 *
 * @param material         Material des Belohnungs-Items (null = nur Befehle)
 * @param amount           Anzahl
 * @param name             Anzeigename (&amp;-Codes), optional
 * @param lore             Lore-Zeilen, optional
 * @param enchants         Verzauberungen
 * @param flags            ItemFlags
 * @param unbreakable      unzerbrechlich
 * @param customModelData  CustomModelData, optional
 * @param commands         Konsolenbefehle, {player} wird ersetzt
 * @param display          Text, der im GUI als Belohnung angezeigt wird, optional
 */
public record QuestReward(
        Material material,
        int amount,
        String name,
        List<String> lore,
        Map<Enchantment, Integer> enchants,
        List<ItemFlag> flags,
        boolean unbreakable,
        Integer customModelData,
        List<String> commands,
        String display) {
}
