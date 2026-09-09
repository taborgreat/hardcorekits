package gg.hungergames.kit.kits;

import gg.hungergames.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Smelts in its head.
 *
 * <p>Click a stack of coal onto ore in your inventory and it becomes ingots on the spot, one
 * coal a piece — no furnace, no wait, no smoke over your camp giving you away. Fifteen coal
 * on thirty-five iron ore is fifteen ingots and twenty ore left over, exactly as the arithmetic
 * says.
 *
 * <p>The kit is an economy, not a weapon: befriend a miner, or loot one.
 *
 * <p>Behaviour lives in {@link gg.hungergames.listener.ForgerListener}.
 */
public final class ForgerKit implements Kit {

    public static final String ID = "forger";

    private static final int COAL = 3;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Forger";
    }

    @Override
    public String description() {
        return "Click coal onto ore in your inventory to smelt it instantly, 1 coal per ingot. "
                + "Starts with 3 coal.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(new ItemStack(Material.COAL, COAL));
    }
}
