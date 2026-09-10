package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.entity.Player;

/**
 * Picks people up and throws them.
 *
 * <p>Right-click another player with an empty hand and they are hoisted onto your shoulders;
 * left-click with the same empty hand and they are launched wherever you are looking. The grip
 * is not a hold-down: they stay up there until thrown, until they sneak free, or until one of
 * you dies.
 *
 * <p>It is an escape and a trap in one. A Hulk cornered by two people can post one of them over
 * a cliff, and a Hulk working with a Demoman can drop somebody straight onto a mine. The
 * counters are to come at a Hulk from behind, to fight them indoors where a throw has nowhere
 * to go, and to sneak the moment you leave the ground.
 *
 * <p>There is no starting equipment. The empty hand <em>is</em> the kit — carrying anything in
 * that slot switches the ability off, so a Hulk who wants to grab has to stop swinging first.
 *
 * <p>Behaviour lives in {@link com.hardcorekits.listener.HulkListener}.
 */
public final class HulkKit implements Kit {

    public static final String ID = "hulk";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Hulk";
    }

    @Override
    public String description() {
        return "Empty-handed: right-click a player or mob to pick them up, crouch to charge, "
                + "left-click to throw. Players can sneak free.";
    }

    @Override
    public void apply(Player player) {
        // Nothing. The ability needs an empty hand, so handing out an item would work against it.
    }
}
