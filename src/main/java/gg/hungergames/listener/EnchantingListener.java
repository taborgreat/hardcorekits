package gg.hungergames.listener;

import gg.hungergames.HungerGames;
import gg.hungergames.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Makes the enchanting table free.
 *
 * <p>The XP bar stopped being a currency the moment it became the kill counter — the level
 * number is how many players you have dropped this match, and spending that at a table would
 * corrupt the one scoreboard everyone reads mid-fight. So the table stops charging: while it
 * is open the player is quietly given the levels (and the lapis) that vanilla demands, and
 * the moment it closes, the bar snaps back to the kill count.
 *
 * <p>The gate on enchanting is therefore purely physical — reaching the feast table alive —
 * which is the only cost this game mode ever meant it to have.
 */
public final class EnchantingListener implements Listener {

    /** Above any vanilla requirement (30), below anything that reads as a stat. */
    private static final int TABLE_LEVELS = 40;
    /** The lapis slot of an enchanting inventory. Slot 0 is the item. */
    private static final int LAPIS_SLOT = 1;

    private final HungerGames plugin;
    private final GameManager game;

    public EnchantingListener(HungerGames plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    /** Opening the table stocks it: full lapis, and enough levels for any option. */
    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() != InventoryType.ENCHANTING
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        event.getInventory().setItem(LAPIS_SLOT, new ItemStack(Material.LAPIS_LAZULI, 64));
        player.setLevel(TABLE_LEVELS);
        player.setExp(0.0F);
    }

    /** The house lapis stays in the house — it funds enchants, it is not a hand-out. */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getType() != InventoryType.ENCHANTING) {
            return;
        }
        boolean lapisSlot = event.getClickedInventory() == top
                && event.getSlot() == LAPIS_SLOT;
        // Shift-clicking lapis from the player's own inventory would merge into the stocked
        // stack and be cleared with it on close — swallow that too, it is their lapis.
        boolean shiftingLapisIn = event.isShiftClick()
                && event.getCurrentItem() != null
                && event.getCurrentItem().getType() == Material.LAPIS_LAZULI
                && event.getClickedInventory() != top;
        if (lapisSlot || shiftingLapisIn) {
            event.setCancelled(true);
        }
    }

    /**
     * Vanilla deducts levels and lapis right after this event; a tick later both are topped
     * back up so the next enchant is just as free.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        Inventory table = event.getInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            table.setItem(LAPIS_SLOT, new ItemStack(Material.LAPIS_LAZULI, 64));
            player.setLevel(TABLE_LEVELS);
            player.setExp(0.0F);
        });
    }

    /** Closing the table takes the house lapis back and returns the bar to the kill count. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getType() != InventoryType.ENCHANTING
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        event.getInventory().setItem(LAPIS_SLOT, null);
        game.showKills(player);
    }
}
