package de.quests.gui;

import de.quests.config.ConfigManager;
import de.quests.data.PlayerQuestData;
import de.quests.effect.EffectManager;
import de.quests.quest.Quest;
import de.quests.quest.QuestManager;
import de.quests.quest.QuestStatus;
import de.quests.reward.RewardManager;
import de.quests.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Baut das Quest-GUI (Kisten-Inventar) auf und verarbeitet Klicks.
 */
public final class GUIManager {

    private final ConfigManager config;
    private final QuestManager quests;
    private final RewardManager rewards;
    private final EffectManager effects;

    public GUIManager(ConfigManager config, QuestManager quests, RewardManager rewards, EffectManager effects) {
        this.config = config;
        this.quests = quests;
        this.rewards = rewards;
        this.effects = effects;
    }

    /** Oeffnet das Quest-Menue fuer den Spieler. */
    public void open(Player player) {
        int rows = Math.max(1, Math.min(6, config.cfg().getInt("gui.rows", 3)));
        QuestMenuHolder holder = new QuestMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, TextUtil.color(config.raw("gui.title", "&8Quests")));
        holder.setInventory(inventory);
        render(player, holder);
        player.openInventory(inventory);
        effects.play(player, "gui-open");
    }

    /** Zeichnet den Inhalt des Menues neu (z. B. nach dem Abholen einer Belohnung). */
    public void render(Player player, QuestMenuHolder holder) {
        Inventory inventory = holder.getInventory();
        inventory.clear();
        holder.clearSlots();

        if (config.cfg().getBoolean("gui.filler.enabled", true)) {
            ItemStack filler = buildFiller();
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, filler);
            }
        }

        PlayerQuestData data = quests.getData(player.getUniqueId());
        Map<String, Integer> layout = layout(inventory.getSize());
        for (Quest quest : quests.getQuests().values()) {
            Integer slot = layout.get(quest.getId());
            if (slot == null) {
                continue;
            }
            inventory.setItem(slot, buildQuestItem(quest, data));
            holder.mapSlot(slot, quest.getId());
        }
    }

    /** Aktualisiert das Menue, falls der Spieler es gerade offen hat. */
    public void refreshIfOpen(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof QuestMenuHolder holder) {
            render(player, holder);
        }
    }

    /** Schliesst das Menue bei allen Spielern (z. B. vor einem Reload). */
    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof QuestMenuHolder) {
                player.closeInventory();
            }
        }
    }

    /** Verarbeitet einen Klick auf einen Slot im Quest-Menue. */
    public void handleClick(Player player, QuestMenuHolder holder, int slot) {
        String questId = holder.getQuestId(slot);
        if (questId == null) {
            return;
        }
        Quest quest = quests.getQuest(questId);
        if (quest == null) {
            return;
        }
        PlayerQuestData data = quests.getData(player.getUniqueId());
        switch (quests.getStatus(data, quest)) {
            case CLAIMABLE -> {
                if (quests.claim(player, quest) == QuestManager.ClaimResult.SUCCESS) {
                    render(player, holder);
                }
            }
            case CLAIMED -> player.sendMessage(config.msg("reward-already-claimed"));
            case IN_PROGRESS -> player.sendMessage(config.msg("reward-not-ready"));
        }
    }

    // ------------------------------------------------------------------
    // Item-Aufbau
    // ------------------------------------------------------------------

    private ItemStack buildFiller() {
        Material material = Material.matchMaterial(config.raw("gui.filler.material", "GRAY_STAINED_GLASS_PANE"));
        if (material == null || material.isAir() || !material.isItem()) {
            material = Material.GRAY_STAINED_GLASS_PANE;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.space());
        item.setItemMeta(meta);
        return item;
    }

    /** Erzeugt das Anzeige-Item einer Quest inklusive Fortschritt, Belohnung und Status. */
    public ItemStack buildQuestItem(Quest quest, PlayerQuestData data) {
        QuestStatus status = quests.getStatus(data, quest);
        int progress = Math.min(data.getProgress(quest.getId()), quest.getTarget());
        Locale locale = config.getLocale();

        String bar = TextUtil.progressBar(progress, quest.getTarget(),
                config.cfg().getInt("gui.progress-bar.length", 20),
                config.raw("gui.progress-bar.filled-char", "|"),
                config.raw("gui.progress-bar.empty-char", "|"),
                config.raw("gui.progress-bar.filled-color", "&a"),
                config.raw("gui.progress-bar.empty-color", "&7"));
        String statusText = statusText(status);

        List<String> lines = new ArrayList<>();
        for (String line : config.cfg().getStringList("gui.lore")) {
            if (line.trim().equals("{description}")) {
                for (String descriptionLine : quest.getDescription()) {
                    lines.add(fill(descriptionLine, quest, progress, locale, bar, statusText));
                }
                continue;
            }
            lines.add(fill(line, quest, progress, locale, bar, statusText));
        }

        ItemStack item = new ItemStack(quest.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(TextUtil.item(quest.getDisplayName()));
        meta.lore(TextUtil.itemLore(lines));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        // Abholbereite Quests leuchten, damit sie im Menue auffallen.
        meta.setEnchantmentGlintOverride(status == QuestStatus.CLAIMABLE ? Boolean.TRUE : null);
        item.setItemMeta(meta);
        return item;
    }

    /** Liefert den Statustext (aus der Config) fuer einen Status. */
    public String statusText(QuestStatus status) {
        return switch (status) {
            case IN_PROGRESS -> config.raw("gui.status.in-progress", "&c✘ Noch nicht abgeschlossen");
            case CLAIMABLE -> config.raw("gui.status.claimable", "&e✔ Abholbereit");
            case CLAIMED -> config.raw("gui.status.claimed", "&a✔ Bereits abgeholt");
        };
    }

    private String fill(String text, Quest quest, int progress, Locale locale, String bar, String statusText) {
        return text
                .replace("{name}", quest.getDisplayName())
                .replace("{progress}", TextUtil.formatNumber(locale, progress))
                .replace("{target}", TextUtil.formatNumber(locale, quest.getTarget()))
                .replace("{percent}", TextUtil.formatPercent(locale, progress, quest.getTarget()))
                .replace("{bar}", bar)
                .replace("{reward}", rewards.describe(quest))
                .replace("{status}", statusText);
    }

    // ------------------------------------------------------------------
    // Slot-Verteilung
    // ------------------------------------------------------------------

    /**
     * Verteilt die Quests auf Slots: Quests mit gueltigem, freiem "slot" bekommen diesen,
     * alle anderen werden der Reihe nach auf freie Slots in der Mitte des Menues gesetzt.
     */
    private Map<String, Integer> layout(int size) {
        Map<String, Integer> result = new HashMap<>();
        Set<Integer> used = new HashSet<>();
        for (Quest quest : quests.getQuests().values()) {
            int slot = quest.getSlot();
            if (slot >= 0 && slot < size && used.add(slot)) {
                result.put(quest.getId(), slot);
            }
        }
        Iterator<Integer> candidates = autoSlots(size).iterator();
        for (Quest quest : quests.getQuests().values()) {
            if (result.containsKey(quest.getId())) {
                continue;
            }
            while (candidates.hasNext()) {
                int slot = candidates.next();
                if (used.add(slot)) {
                    result.put(quest.getId(), slot);
                    break;
                }
            }
        }
        return result;
    }

    /** Reihenfolge der automatischen Slots: zuerst der Innenbereich, danach der Rest. */
    private List<Integer> autoSlots(int size) {
        List<Integer> slots = new ArrayList<>(size);
        int rows = size / 9;
        if (rows >= 3) {
            for (int row = 1; row < rows - 1; row++) {
                for (int column = 1; column < 8; column++) {
                    slots.add(row * 9 + column);
                }
            }
        }
        Set<Integer> present = new HashSet<>(slots);
        for (int i = 0; i < size; i++) {
            if (!present.contains(i)) {
                slots.add(i);
            }
        }
        return slots;
    }
}
