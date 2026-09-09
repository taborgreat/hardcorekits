package gg.hungergames.kit.kits;

import gg.hungergames.kit.Kit;
import org.bukkit.entity.Player;

/**
 * A shell you can step into, at the price of your own swing.
 *
 * <p>Crouch and almost nothing gets through — a heart a hit, half that with a shield up — but
 * you cannot attack while you are down there. The kit is a rhythm: crouch to eat the hit,
 * stand to answer it, crouch again.
 *
 * <p>It is also the best decoy in the game. Standing in the open at the feast looking soft
 * invites people to commit to you, and a team mate finishes whoever takes the bait. The counter
 * is simply to ignore a crouching Turtle and hit the other one.
 *
 * <p>The shell is not absolute: explosions go straight through it, so a Demoman trap kills a
 * Turtle exactly as fast as anyone else. Stepping outside the map does too.
 *
 * <p>Behaviour lives in {@link gg.hungergames.listener.TurtleListener}.
 */
public final class TurtleKit implements Kit {

    public static final String ID = "turtle";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Turtle";
    }

    @Override
    public String description() {
        return "Crouch to take almost no damage, but you cannot attack while crouched.";
    }

    @Override
    public void apply(Player player) {
        // No starting equipment — the kit is the crouch.
    }
}
