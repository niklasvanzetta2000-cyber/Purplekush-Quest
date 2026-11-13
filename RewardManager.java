package de.quests.reward;

import de.quests.config.ConfigManager;
import de.quests.effect.EffectManager;
import de.quests.quest.Quest;
import de.quests.quest.QuestReward;
import de.quests.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.List;
import java.util.Map;

/**
 * Baut Belohnungs-Items aus der Config und uebergibt sie an Spieler.
 */
public final class RewardManager {

    private final ConfigManager config;
    private final EffectManager effects;

    public RewardManager(ConfigManager config, EffectManager effects) {
        this.config = config;
        this.effects = effects;
    }

    /**
     * Gibt dem Spieler die Belohnung der Quest: Item (bei vollem Inventar wird es gedroppt),
     * Konsolenbefehle, Nachricht, Sound und Partikel.
     */
    public void give(Player player, Quest quest) {
        QuestReward reward = quest.getReward();
        if (reward != null) {
            if (reward.material() != null) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(buildItem(reward));
                if (!leftover.isEmpty()) {
                    for (ItemStack item : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    }
                    player.sendMessage(config.msg("inventory-full"));
                }
            }
            for (String command : reward.commands()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", player.getName()));
            }
        }
        player.sendMessage(config.msg("reward-claimed", "{quest}", TextUtil.strip(quest.getDisplayName())));
        effects.play(player, "reward-claim");
    }

    /**
     * Erzeugt das Belohnungs-Item (Name, Lore, Verzauberungen, Flags, Unbreakable, CustomModelData).
     */
    @SuppressWarnings("UnstableApiUsage")
    public ItemStack buildItem(QuestReward reward) {
        ItemStack item = new ItemStack(reward.material(), reward.amount());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (reward.name() != null && !reward.name().isBlank()) {
            meta.displayName(TextUtil.item(reward.name()));
        }
        if (!reward.lore().isEmpty()) {
            meta.lore(TextUtil.itemLore(reward.lore()));
        }
        for (Map.Entry<Enchantment, Integer> entry : reward.enchants().entrySet()) {
            if (meta instanceof EnchantmentStorageMeta storageMeta) {
                storageMeta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
            } else {
                meta.addEnchant(entry.getKey(), entry.getValue(), true);
            }
        }
        for (ItemFlag flag : reward.flags()) {
            meta.addItemFlags(flag);
        }
        if (reward.unbreakable()) {
            meta.setUnbreakable(true);
        }
        if (reward.customModelData() != null) {
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setFloats(List.of(reward.customModelData().floatValue()));
            meta.setCustomModelDataComponent(component);
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Liefert den Text, der im GUI als Belohnung angezeigt wird.
     * Ist in der Config "display" gesetzt, wird dieser Text verwendet, sonst wird er erzeugt.
     */
    public String describe(Quest quest) {
        QuestReward reward = quest.getReward();
        if (reward == null) {
            return config.raw("gui.reward-none", "&7Keine");
        }
        if (reward.display() != null && !reward.display().isBlank()) {
            return reward.display();
        }
        StringBuilder builder = new StringBuilder();
        if (reward.material() != null) {
            if (reward.amount() > 1) {
                builder.append(reward.amount()).append("x ");
            }
            if (reward.name() != null && !reward.name().isBlank()) {
                builder.append(reward.name());
            } else {
                builder.append("&f").append(TextUtil.prettify(reward.material().name()));
            }
            if (!reward.enchants().isEmpty()) {
                builder.append(" &7(");
                boolean first = true;
                for (Map.Entry<Enchantment, Integer> entry : reward.enchants().entrySet()) {
                    if (!first) {
                        builder.append(", ");
                    }
                    first = false;
                    builder.append(TextUtil.prettify(entry.getKey().getKey().getKey()))
                            .append(' ').append(TextUtil.roman(entry.getValue()));
                }
                builder.append(')');
            }
        } else {
            builder.append("&7Spezialbelohnung");
        }
        return builder.toString();
    }
}
