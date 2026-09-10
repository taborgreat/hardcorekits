package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.entity.Player;

/**
 * Cookies out of the undergrowth, and they mean whatever you need them to mean.
 *
 * <p>Breaking grass pays out a cookie one time in five, and eating one is instant: it tops up
 * hunger first, health once hunger is full, and hands out Speed II when both bars are already
 * full. So a Cookiemonster in trouble heals, and a Cookiemonster in good shape runs.
 *
 * <p>The counter is to keep hitting them. Damage puts the cookies back to work on health, which
 * is the one thing that stops them turning into an escape.
 *
 * <p>Nothing in the loadout: the kit is the grass.
 *
 * <p>The cookies live in {@link com.hardcorekits.listener.CookiemonsterListener}.
 */
public final class CookiemonsterKit implements Kit {

    public static final String ID = "cookiemonster";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Cookiemonster";
    }

    @Override
    public String description() {
        return "Grass drops cookies. Eating one fills hunger, then health, and once both are full it "
                + "gives Speed II.";
    }

    @Override
    public void apply(Player player) {
        // No starting equipment. The first cookie comes out of the first patch of grass.
    }
}
