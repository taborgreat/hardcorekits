package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Pulls the lever on itself.
 *
 * <p>Place the button, press it, take what comes: a spread of blessings and curses in equal
 * measure — speed, strength, regeneration, a full stomach on the good side; slowness, poison,
 * hunger, weakness on the bad — and two thousand-to-one long shots at either end: a full set
 * of diamond armour, or dropping dead on the spot.
 *
 * <p>The button is a possession: break it, carry it, place it again wherever the next wager
 * feels right.
 *
 * <p>Behaviour lives in {@link com.hardcorekits.listener.GamblerListener}.
 */
public final class GamblerKit implements Kit {

    public static final String ID = "gambler";

    /** The wager. Any stone button a Gambler placed can be pressed for a roll. */
    public static final Material BUTTON = Material.STONE_BUTTON;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Gambler";
    }

    @Override
    public String description() {
        return "Place your button and press it for a random effect, good or bad — with a "
                + "1/1000 shot at full diamond, or at dropping dead.";
    }

    @Override
    public void apply(Player player) {
        player.getInventory().addItem(new ItemStack(BUTTON));
    }
}
