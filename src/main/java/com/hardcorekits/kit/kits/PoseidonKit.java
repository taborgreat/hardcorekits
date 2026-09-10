package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.entity.Player;

/**
 * Unbeatable in the water, and awkward the moment they leave it.
 *
 * <p>Standing in water a Poseidon hits far harder than anyone else, and moving in it keeps
 * their lungs full no matter how deep or how long. The whole kit is therefore about choosing
 * the ground: a Poseidon with a bucket brings their advantage with them, floods the feast, or
 * pours a column down the shaft they are digging into someone's camp and arrives unhurt and
 * already at full strength.
 *
 * <p>The costs are the mirror of that. Out of the water they are slowed for a few seconds, so
 * chasing a fleeing target onto land means arriving late and sluggish, and the strength is
 * gone the instant they step out. Standing still underwater drowns them like anyone else —
 * the lungs are a swimmer's, not a fish's. The counter is to take the water away: block the
 * source, or fight them anywhere else.
 *
 * <p>Behaviour lives in {@link com.hardcorekits.listener.PoseidonListener}.
 */
public final class PoseidonKit implements Kit {

    public static final String ID = "poseidon";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Poseidon";
    }

    @Override
    public String description() {
        return "Hit far harder while standing in water and never drown while swimming, but leaving it slows you.";
    }

    @Override
    public void apply(Player player) {
        // No starting equipment — the kit is entirely what water does for you.
    }
}
