package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Takes the sword out of the argument.
 *
 * <p>Right-click a player with the blaze rod and whatever they are holding is shoved out of
 * their hotbar into their backpack — mid-swing, their sword is suddenly three menus away. It
 * swaps with something deeper if their inventory is full, so there is always somewhere for it
 * to go.
 *
 * <p>A few seconds of cooldown between touches; the rod is never consumed.
 *
 * <p>Behaviour lives in {@link com.hardcorekits.listener.MonkListener}.
 */
public final class MonkKit implements Kit {

    public static final String ID = "monk";

    /** The rod the touch is keyed to. */
    public static final Material ROD = Material.BLAZE_ROD;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Monk";
    }

    @Override
    public String description() {
        return "Right click a player with your blaze rod and their held item is shoved out "
                + "of their hotbar into their inventory.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(new ItemStack(ROD));
    }
}
