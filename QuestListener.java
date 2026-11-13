package de.quests.listener;

import de.quests.block.QuestBlockManager;
import de.quests.config.ConfigManager;
import de.quests.gui.GUIManager;
import de.quests.gui.QuestMenuHolder;
import de.quests.quest.QuestManager;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;

/**
 * Alle Bukkit-Events des Plugins: Block-Tracking, Quest-Block-Interaktion, GUI-Klicks
 * und Laden/Entladen der Spielerdaten.
 */
public final class QuestListener implements Listener {

    private final ConfigManager config;
    private final QuestManager quests;
    private final QuestBlockManager blocks;
    private final GUIManager gui;

    public QuestListener(ConfigManager config, QuestManager quests, QuestBlockManager blocks, GUIManager gui) {
        this.config = config;
        this.quests = quests;
        this.blocks = blocks;
        this.gui = gui;
    }

    // ------------------------------------------------------------------
    // Spielerdaten
    // ------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        quests.loadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        quests.unloadPlayer(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------
    // Tracking
    // ------------------------------------------------------------------

    /**
     * Zaehlt abgebaute Bloecke. MONITOR + ignoreCancelled: Es zaehlen nur Abbauvorgaenge,
     * die tatsaechlich stattfinden (z. B. nicht in geschuetzten Regionen).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        GameMode mode = player.getGameMode();
        if (mode == GameMode.SPECTATOR || (mode == GameMode.CREATIVE && !quests.isTrackCreative())) {
            return;
        }
        quests.handleBlockBreak(player, event.getBlock(), player.getInventory().getItemInMainHand());
    }

    // ------------------------------------------------------------------
    // Quest-Block
    // ------------------------------------------------------------------

    /** Rechtsklick auf einen Quest-Block oeffnet das Menue. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !blocks.isQuestBlock(block)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("quests.use")) {
            player.sendMessage(config.msg("no-permission"));
            return;
        }
        gui.open(player);
    }

    /** Quest-Bloecke koennen nur von Admins abgebaut werden (dabei wird die Registrierung entfernt). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onQuestBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!blocks.isQuestBlock(block)) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission("quests.admin")) {
            blocks.remove(block);
            player.sendMessage(config.msg("questblock-removed"));
        } else {
            event.setCancelled(true);
            player.sendMessage(config.msg("questblock-protected"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (blocks.hasAny()) {
            event.blockList().removeIf(blocks::isQuestBlock);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (blocks.hasAny()) {
            event.blockList().removeIf(blocks::isQuestBlock);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (blocks.hasAny() && event.getBlocks().stream().anyMatch(blocks::isQuestBlock)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (blocks.hasAny() && event.getBlocks().stream().anyMatch(blocks::isQuestBlock)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (blocks.isQuestBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // GUI
    // ------------------------------------------------------------------

    /** Alle Klicks im Quest-Menue werden abgebrochen; Klicks auf Quests loesen das Abholen aus. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof QuestMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != top) {
            return;
        }
        gui.handleClick(player, holder, event.getSlot());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof QuestMenuHolder) {
            event.setCancelled(true);
        }
    }
}
