package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.entity.Player;

/**
 * Information, and nothing else.
 *
 * <p>A Spy fights no better than anyone, but never fights blind. The compass everyone carries
 * reports exact coordinates and the target's kit instead of a name and a direction; anyone who
 * comes within earshot is announced before they arrive; and anyone in sight can be read at a
 * glance, so an approaching stranger is a known quantity before the first swing.
 *
 * <p>That turns the kit into preparation rather than power. Knowing the shape of the fight is
 * what it sells — tower before the Stomper arrives, get clear of the Endermage's column, take
 * the Barbarian early while their sword is still wood.
 *
 * <p>The catch is that none of it is a weapon, and knowing where someone is means they are
 * usually also near you. The intel is private, so nothing gives a Spy away.
 *
 * <p>Behaviour lives in {@link com.hardcorekits.listener.SpyListener}.
 */
public final class SpyKit implements Kit {

    public static final String ID = "spy";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Spy";
    }

    @Override
    public String description() {
        return "Your compass tracks its target live instead of snapshotting, reports exact "
                + "coordinates and kits, announces nearby players, and identifies anyone you look at.";
    }

    @Override
    public void apply(Player player) {
        // No starting equipment — everyone already carries the compass this kit rewrites.
    }
}
