package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Calls the sky down on a spot.
 *
 * <p>Right-click a block with the axe and lightning hits the <em>highest</em> block in that
 * column — which is what makes it the answer to towering. You do not have to reach someone on
 * top of a pillar; you only have to click the pillar, and the strike shoves them off it.
 *
 * <p>High strikes hit harder. Land one above the height threshold and burning netherrack is
 * left behind along with a stronger shove; strike that same netherrack again and it detonates.
 * Underground the netherrack is a light source, which makes the axe a mining tool as well.
 *
 * <p>The axe is plain wood, so it can be re-crafted if you lose it — and it still chops trees.
 *
 * <p>Countered by a roof (the strike lands above it) and by the Fireman, who ignores the
 * lightning entirely.
 *
 * <p>Behaviour lives in {@link com.hardcorekits.listener.ThorListener}.
 */
public final class ThorKit implements Kit {

    public static final String ID = "thor";

    /** The axe is deliberately plain wood: craftable again if it is lost. */
    public static final Material HAMMER = Material.WOODEN_AXE;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Thor";
    }

    @Override
    public String description() {
        return "Right click a block with your axe to call lightning onto the top of that column.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(new ItemStack(HAMMER));
    }
}
