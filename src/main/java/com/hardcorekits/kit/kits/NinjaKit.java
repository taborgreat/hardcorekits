package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.entity.Player;

/**
 * Hit someone, then crouch to be where they are.
 *
 * <p>Landing a hit marks that player for ten seconds. Crouching inside that window puts the
 * Ninja on top of them, wherever they have got to: down the tower they knocked you off, out of
 * the hole you were dropped into, or across the gap they thought they had made.
 *
 * <p>Seven seconds between teleports is what stops it being a tether. The counter is not to
 * outrun a Ninja, which cannot be done, but to make the ten seconds pass.
 *
 * <p>Nothing in the loadout. The chase lives in
 * {@link com.hardcorekits.listener.NinjaListener}.
 */
public final class NinjaKit implements Kit {

    public static final String ID = "ninja";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Ninja";
    }

    @Override
    public String description() {
        return "Hit someone and you can crouch to teleport to them for the next ten seconds. Seven "
                + "seconds between jumps.";
    }

    @Override
    public void apply(Player player) {
        // No starting equipment. The kit is the first hit you land.
    }
}
