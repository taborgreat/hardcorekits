package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.entity.Player;

/**
 * Slows what it hits.
 *
 * <p>Every hit on a player or mob has a one-in-three chance to leave Slowness II behind. That
 * is both halves of a fight: an opponent who cannot chase you is one you can leave to go
 * re-soup, and one who cannot run is one your team catches. There is no ability to aim and no
 * cooldown to manage — the kit is simply that fighting you is slower than fighting anyone
 * else.
 *
 * <p>Behaviour lives in {@link com.hardcorekits.listener.SnailListener}.
 */
public final class SnailKit implements Kit {

    public static final String ID = "snail";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Snail";
    }

    @Override
    public String description() {
        return "Your hits have a 1 in 3 chance to give Slowness II. Works on mobs too.";
    }

    @Override
    public void apply(Player player) {
        // No starting equipment — the kit rides on whatever you swing.
    }
}
