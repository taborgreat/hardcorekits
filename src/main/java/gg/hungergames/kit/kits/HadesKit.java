package gg.hungergames.kit.kits;

import gg.hungergames.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Raises an army from whatever is standing around.
 *
 * <p>Right-click any mob with the iron ingot and it becomes a minion: it follows you, turns
 * on whoever attacks you, and piles onto whoever you attack. The ingot is a wand, not a fee —
 * the limit is the head-count, not the material.
 *
 * <p>The counter is the same as it always was: minions are only as tough as the mobs they
 * were, and killing the mob kills the minion. Go through the army, or through Hades.
 *
 * <p>Behaviour lives in {@link gg.hungergames.listener.HadesListener}.
 */
public final class HadesKit implements Kit {

    public static final String ID = "hades";

    /** The taming wand. Never consumed. */
    public static final Material WAND = Material.IRON_INGOT;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Hades";
    }

    @Override
    public String description() {
        return "Right-click a mob with your iron ingot to make it a minion. Minions follow "
                + "you and fight whoever fights you.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(new ItemStack(WAND));
    }
}
