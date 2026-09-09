package gg.hungergames.kit.kits;

import gg.hungergames.kit.Kit;
import org.bukkit.entity.Player;

/**
 * Killing feeds it.
 *
 * <p>Down a mob and you get Strength for ten seconds; down a player and you get a stronger
 * Strength for fifteen. The kit rewards momentum — the intended play is to open a fight by
 * killing something else nearby, then turn on your actual target already buffed, or to focus
 * one member of a team so the rest are easier.
 *
 * <p>The catch is that Blood Lust roots you: while it is running you cannot jump. That is what
 * stops it being free — you hit harder but you cannot chase over terrain, retreat up a block,
 * or fight a tower. A Berserker winding up on a mob is a visible tell, and running is the
 * counter.
 *
 * <p>Behaviour lives in {@link gg.hungergames.listener.BerserkerListener}.
 */
public final class BerserkerKit implements Kit {

    public static final String ID = "berserker";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Berserker";
    }

    @Override
    public String description() {
        return "Kills grant Blood Lust: Strength for a few seconds, but you cannot jump while it lasts.";
    }

    @Override
    public void apply(Player player) {
        // No starting equipment — the kit is entirely what killing gives you.
    }
}
