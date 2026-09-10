package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Hides in a crowd of itself.
 *
 * <p>Using a magma cream throws a handful of decoys onto the field: silent figures wearing
 * your exact armour, your name over their heads, each scattering in its own direction. In a
 * melee they soak swings that were meant for you; in a chase they multiply the trails. They
 * never attack — which is also the counter: the one swinging back is the real one. Killing a
 * decoy costs the killer a heart and yields nothing.
 *
 * <p>Five creams is the whole supply, and each is a decision: opening a fight, leaving one,
 * or walking into the feast as six people.
 *
 * <p>Faithful-to-vanilla note: true player clones need client-side NPC tricks, so the decoys
 * are humanoid stand-ins dressed and named as you. From across a fight they read as you; face
 * to face they do not — treat them as chaff, not doubles.
 *
 * <p>Behaviour lives in {@link com.hardcorekits.listener.WispListener}.
 */
public final class WispKit implements Kit {

    public static final String ID = "wisp";

    /** The item the decoys are keyed to. */
    public static final Material CREAM = Material.MAGMA_CREAM;

    private static final int CREAMS = 5;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Wisp";
    }

    @Override
    public String description() {
        return "5 magma creams. Each spawns decoys in your armour and name that scatter. "
                + "Killing one costs a heart and drops nothing.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(new ItemStack(CREAM, CREAMS));
    }
}
