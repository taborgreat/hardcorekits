package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.entity.Player;

/**
 * Anything you plant is fully grown the instant it goes in the ground — crops jump straight
 * to harvestable, saplings become trees.
 *
 * <p>Starts with nothing but the compass everyone gets, so the kit is entirely about what you
 * can find and turn around: a handful of scavenged seeds becomes food immediately, and a
 * sapling becomes cover, height, or a way out.
 *
 * <p>The growth itself lives in {@link com.hardcorekits.listener.CultivatorListener}.
 */
public final class CultivatorKit implements Kit {

    public static final String ID = "cultivator";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Cultivator";
    }

    @Override
    public String description() {
        return "Anything you plant grows the moment you plant it, seeds, crops, saplings, all "
                + "of it.";
    }

    @Override
    public void apply(Player player) {
        // Deliberately empty — the compass is handed out to everyone before kits are applied.
    }
}
