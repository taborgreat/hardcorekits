package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Webs the battlefield.
 *
 * <p>Snowballs burst into cobweb where they land — under a chaser's feet, over a runner's
 * head. Unlike the Switcher's, these are ordinary snowballs on purpose: scoop more from any
 * snow you find and keep throwing. The limit is the burst — three webs, then the throwing
 * arm rests for half a minute.
 *
 * <p>Cobwebs are home ground: a heavy Speed boost whenever you stand in one, so the trap that stops
 * everyone else is a lane for you.
 *
 * <p>Behaviour lives in {@link com.hardcorekits.listener.SpidermanListener}.
 */
public final class SpidermanKit implements Kit {

    public static final String ID = "spiderman";

    private static final int SNOWBALLS = 6;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Spiderman";
    }

    @Override
    public String description() {
        return "Snowballs burst into cobwebs where they land — 3 throws, then a cooldown. "
                + "A heavy Speed boost while you stand in webs.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(new ItemStack(Material.SNOWBALL, SNOWBALLS));
    }
}
