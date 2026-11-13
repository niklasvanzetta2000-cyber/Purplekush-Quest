package de.quests.command;

import de.quests.block.QuestBlockManager;
import de.quests.config.ConfigManager;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Befehl /questblock (set | remove | list). Nur fuer Spieler mit quests.admin (standardmaessig OPs).
 */
public final class QuestBlockCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("set", "remove", "list");

    private final ConfigManager config;
    private final QuestBlockManager blocks;

    public QuestBlockCommand(ConfigManager config, QuestBlockManager blocks) {
        this.config = config;
        this.blocks = blocks;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("quests.admin")) {
            sender.sendMessage(config.msg("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(config.msg("usage-questblock"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("list")) {
            List<String> all = blocks.describeAll();
            sender.sendMessage(config.msg("questblock-list-header", "{count}", String.valueOf(all.size())));
            for (String entry : all) {
                sender.sendMessage(config.msg("questblock-list-line", "{location}", entry));
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.msg("players-only"));
            return true;
        }

        if (!sub.equals("set") && !sub.equals("remove")) {
            player.sendMessage(config.msg("usage-questblock"));
            return true;
        }

        int distance = Math.max(2, config.cfg().getInt("quest-block.max-distance", 6));
        Block target = player.getTargetBlockExact(distance);
        if (target == null || target.getType().isAir()) {
            player.sendMessage(config.msg("questblock-look-at-block"));
            return true;
        }
        String location = QuestBlockManager.describe(target.getLocation());

        if (sub.equals("set")) {
            if (blocks.add(target)) {
                player.sendMessage(config.msg("questblock-set", "{location}", location));
            } else {
                player.sendMessage(config.msg("questblock-already", "{location}", location));
            }
        } else {
            if (blocks.remove(target)) {
                player.sendMessage(config.msg("questblock-removed"));
            } else {
                player.sendMessage(config.msg("questblock-none"));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("quests.admin")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    result.add(sub);
                }
            }
        }
        return result;
    }
}
