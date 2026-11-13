package de.quests;

import de.quests.block.QuestBlockManager;
import de.quests.command.QuestBlockCommand;
import de.quests.command.QuestCommand;
import de.quests.config.ConfigManager;
import de.quests.data.QuestStorage;
import de.quests.effect.EffectManager;
import de.quests.gui.GUIManager;
import de.quests.listener.QuestListener;
import de.quests.quest.QuestManager;
import de.quests.reward.RewardManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Hauptklasse des Quests-Plugins. Erstellt alle Manager und verdrahtet sie miteinander.
 */
public final class QuestsPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private EffectManager effectManager;
    private QuestManager questManager;
    private QuestBlockManager questBlockManager;
    private GUIManager guiManager;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.load();

        effectManager = new EffectManager(configManager, getLogger());
        effectManager.reload();

        QuestStorage storage = new QuestStorage(this);
        RewardManager rewardManager = new RewardManager(configManager, effectManager);
        questManager = new QuestManager(this, configManager, storage, rewardManager, effectManager);
        questManager.reloadQuests();

        questBlockManager = new QuestBlockManager(this);
        questBlockManager.load();

        guiManager = new GUIManager(configManager, questManager, rewardManager, effectManager);

        Bukkit.getPluginManager().registerEvents(
                new QuestListener(configManager, questManager, questBlockManager, guiManager), this);

        QuestCommand questCommand = new QuestCommand(this, configManager, questManager, guiManager, effectManager);
        registerCommand("quests", questCommand, questCommand);
        QuestBlockCommand blockCommand = new QuestBlockCommand(configManager, questBlockManager);
        registerCommand("questblock", blockCommand, blockCommand);

        // Falls das Plugin zur Laufzeit geladen wird: Daten bereits online befindlicher Spieler laden.
        for (Player player : Bukkit.getOnlinePlayers()) {
            questManager.loadPlayer(player.getUniqueId());
        }
        questManager.startAutosave();
    }

    @Override
    public void onDisable() {
        if (guiManager != null) {
            guiManager.closeAll();
        }
        if (questManager != null) {
            questManager.saveAll();
        }
    }

    /** Liest Config und Quests neu ein (/quests reload). */
    public void reloadAll() {
        guiManager.closeAll();
        configManager.load();
        effectManager.reload();
        questManager.reloadQuests();
        questManager.startAutosave();
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor,
                                 org.bukkit.command.TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Befehl '" + name + "' fehlt in der plugin.yml!");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }
}
