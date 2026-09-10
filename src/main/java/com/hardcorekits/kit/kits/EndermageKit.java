package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A portal that yanks everyone in its column to wherever you put it.
 *
 * <p>Reach is horizontal only, so height is irrelevant: whoever is directly above or below the
 * portal comes to you. That is the point of the kit — a player hiding at bedrock is dragged up
 * into daylight, or a team mate stuck at the bottom of a ravine is pulled back to the surface.
 *
 * <p>Everyone involved, dragger included, gets a few seconds of immunity so nobody arrives
 * mid-swing and dies for it. The portal then leaves your inventory and comes back after a
 * cooldown, so it cannot be spammed.
 *
 * <p>The ability lives in {@link com.hardcorekits.listener.EndermageListener}.
 */
public final class EndermageKit implements Kit {

    public static final String ID = "endermage";

    /** The item that stands in for the portal. */
    public static final Material PORTAL_ITEM = Material.END_PORTAL_FRAME;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Endermage";
    }

    @Override
    public String description() {
        return "Place your portal and anyone in the 5x5 column above or below it gets pulled "
                + "down to you. It comes back after a cooldown.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(new ItemStack(PORTAL_ITEM));
    }
}
