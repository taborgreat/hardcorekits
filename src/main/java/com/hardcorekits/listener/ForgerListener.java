package com.hardcorekits.listener;

import com.hardcorekits.game.GameManager;
import com.hardcorekits.kit.KitRegistry;
import com.hardcorekits.kit.kits.ForgerKit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * The Forger's touch.
 *
 * <p>Fires on the inventory click itself: coal on the cursor, ore under it. The exchange is
 * strictly one coal per ore — whichever runs out first ends the smelt, and whatever is left
 * of both stays exactly where it was. Ingots land in the ore's slot when it empties, or into
 * the inventory beside it when it does not.
 *
 * <p>Raw forms and ore blocks both count; the only furnace this kit respects is arithmetic.
 */
public final class ForgerListener implements Listener {

    /** What smelts into what. No diamond here — the map has none, by design. */
    private static final Map<Material, Material> SMELTS = Map.of(
            Material.IRON_ORE, Material.IRON_INGOT,
            Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT,
            Material.RAW_IRON, Material.IRON_INGOT,
            Material.GOLD_ORE, Material.GOLD_INGOT,
            Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT,
            Material.NETHER_GOLD_ORE, Material.GOLD_INGOT,
            Material.RAW_GOLD, Material.GOLD_INGOT,
            Material.COPPER_ORE, Material.COPPER_INGOT,
            Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT,
            Material.RAW_COPPER, Material.COPPER_INGOT);

    private final GameManager game;
    private final KitRegistry kits;

    public ForgerListener(GameManager game, KitRegistry kits) {
        this.game = game;
        this.kits = kits;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !game.state().isLive()
                || !kits.canUseAbility(player, ForgerKit.ID)) {
            return;
        }
        ItemStack cursor = event.getCursor();
        ItemStack ore = event.getCurrentItem();
        if (cursor == null || cursor.getType() != Material.COAL
                || ore == null || !SMELTS.containsKey(ore.getType())) {
            return;
        }

        event.setCancelled(true);
        int smelted = Math.min(cursor.getAmount(), ore.getAmount());
        Material ingot = SMELTS.get(ore.getType());

        // Coal pays first, ore burns second, ingots land last.
        cursor.setAmount(cursor.getAmount() - smelted);
        event.getView().setCursor(cursor.getAmount() <= 0 ? null : cursor);

        int oreLeft = ore.getAmount() - smelted;
        if (oreLeft <= 0) {
            event.setCurrentItem(new ItemStack(ingot, smelted));
        } else {
            ore.setAmount(oreLeft);
            event.setCurrentItem(ore);
            for (ItemStack spill : player.getInventory()
                    .addItem(new ItemStack(ingot, smelted)).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), spill);
            }
        }

        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.6F, 1.3F);
    }
}
