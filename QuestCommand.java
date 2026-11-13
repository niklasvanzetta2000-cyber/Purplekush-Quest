package de.quests.command;

import de.quests.QuestsPlugin;
import de.quests.config.ConfigManager;
import de.quests.data.PlayerQuestData;
import de.quests.effect.EffectManager;
import de.quests.gui.GUIManager;
import de.quests.quest.Quest;
import de.quests.quest.QuestManager;
import de.quests.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Befehl /quests (reload | reset | progress).
 */
public final class QuestCommand implements CommandExecutor, TabCompleter {

    private final QuestsPlugin plugin;
    private final ConfigManager config;
    private final QuestManager quests;
    private final GUIManager gui;

    public QuestCommand(QuestsPlugin plugin, ConfigManager config, QuestManager quests,
                        GUIManager gui, EffectManager effects) {
        this.plugin = plugin;
        this.config = config;
        this.quests = quests;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> handleReload(sender);
            case "reset" -> handleReset(sender, args);
            case "progress" -> handleProgress(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        if (sender.hasPermission("quests.reload")) {
            sender.sendMessage(config.msg("usage-reload"));
        }
        if (sender.hasPermission("quests.reset")) {
            sender.sendMessage(config.msg("usage-reset"));
        }
        if (sender.hasPermission("quests.progress")) {
            sender.sendMessage(config.msg("usage-progress"));
        }
    }

    // ------------------------------------------------------------------
    // /quests reload
    // ------------------------------------------------------------------

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("quests.reload")) {
            sender.sendMessage(config.msg("no-permission"));
            return;
        }
        plugin.reloadAll();
        sender.sendMessage(config.msg("reload-done", "{count}", String.valueOf(quests.getQuests().size())));
    }

    // ------------------------------------------------------------------
    // /quests reset <Spieler> [Quest|all]
    // ------------------------------------------------------------------

    private void handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("quests.reset")) {
            sender.sendMessage(config.msg("no-permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(config.msg("usage-reset"));
            return;
        }
        OfflinePlayer target = resolve(args[1]);
        if (target == null) {
            sender.sendMessage(config.msg("player-not-found", "{player}", args[1]));
            return;
        }
        String questId = null;
        if (args.length >= 3 && !args[2].equalsIgnoreCase("all")) {
            Quest quest = quests.getQuest(args[2]);
            if (quest == null) {
                sender.sendMessage(config.msg("quest-unknown", "{quest}", args[2]));
                return;
            }
            questId = quest.getId();
        }
        quests.resetProgress(target.getUniqueId(), questId);

        Player online = target.getPlayer();
        if (online != null) {
            gui.refreshIfOpen(online);
        }
        String name = target.getName() != null ? target.getName() : args[1];
        sender.sendMessage(config.msg("reset-done", "{player}", name,
                "{quest}", questId == null ? config.message("all-quests") : questId));
    }

    // ------------------------------------------------------------------
    // /quests progress [Spieler]
    // ------------------------------------------------------------------

    private void handleProgress(CommandSender sender, String[] args) {
        if (!sender.hasPermission("quests.progress")) {
            sender.sendMessage(config.msg("no-permission"));
            return;
        }
        OfflinePlayer target;
        if (args.length >= 2) {
            if (!sender.hasPermission("quests.admin")) {
                sender.sendMessage(config.msg("no-permission"));
                return;
            }
            target = resolve(args[1]);
            if (target == null) {
                sender.sendMessage(config.msg("player-not-found", "{player}", args[1]));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(config.msg("usage-progress"));
            return;
        }

        PlayerQuestData data = quests.peek(target.getUniqueId());
        String name = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        sender.sendMessage(config.msg("progress-header", "{player}", name));
        for (Quest quest : quests.getQuests().values()) {
            int progress = Math.min(data.getProgress(quest.getId()), quest.getTarget());
            sender.sendMessage(config.msg("progress-line",
                    "{quest}", quest.getDisplayName(),
                    "{progress}", TextUtil.formatNumber(config.getLocale(), progress),
                    "{target}", TextUtil.formatNumber(config.getLocale(), quest.getTarget()),
                    "{percent}", TextUtil.formatPercent(config.getLocale(), progress, quest.getTarget()),
                    "{status}", gui.statusText(quests.getStatus(data, quest))));
        }
    }

    /** Sucht einen Spieler: zuerst online, dann im Cache des Servers (ohne blockierende Mojang-Abfrage). */
    private OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    // ------------------------------------------------------------------
    // Tab-Completion
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            addIf(result, args[0], "reload", sender.hasPermission("quests.reload"));
            addIf(result, args[0], "reset", sender.hasPermission("quests.reset"));
            addIf(result, args[0], "progress", sender.hasPermission("quests.progress"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            boolean allowed = (sub.equals("reset") && sender.hasPermission("quests.reset"))
                    || (sub.equals("progress") && sender.hasPermission("quests.admin"));
            if (allowed) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    addIf(result, args[1], player.getName(), true);
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("reset") && sender.hasPermission("quests.reset")) {
            addIf(result, args[2], "all", true);
            for (String id : quests.getQuests().keySet()) {
                addIf(result, args[2], id, true);
            }
        }
        return result;
    }

    private void addIf(List<String> list, String typed, String value, boolean condition) {
        if (condition && value.toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))) {
            list.add(value);
        }
    }
}
