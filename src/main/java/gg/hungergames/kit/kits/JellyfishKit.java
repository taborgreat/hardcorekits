package gg.hungergames.kit.kits;

import gg.hungergames.kit.Kit;
import org.bukkit.entity.Player;

/**
 * Water out of an empty hand.
 *
 * <p>Right-click a block with nothing held and water appears against it for three seconds,
 * then it is gone. No bucket, no inventory slot, and no way to run out.
 *
 * <p>Three seconds is short enough that it is never terrain, and long enough to be every use a
 * bucket has: break a fall you were not going to survive, put out the fire you are standing
 * in, wash the ground behind you as you run, or ride it up the side of something.
 *
 * <p>The conjuring lives in {@link gg.hungergames.listener.JellyfishListener}.
 */
public final class JellyfishKit implements Kit {

    public static final String ID = "jellyfish";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Jellyfish";
    }

    @Override
    public String description() {
        return "Right-click a block with an empty hand and water appears there for three seconds. No "
                + "bucket, and it never runs out.";
    }

    @Override
    public void apply(Player player) {
        // No starting equipment. The empty hand is the kit.
    }
}
