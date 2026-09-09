package gg.hungergames.kit.kits;

import gg.hungergames.kit.Kit;
import org.bukkit.entity.Player;

/**
 * Becomes whatever it kills.
 *
 * <p>Every player kill replaces your kit with theirs, for the rest of the match or until you
 * kill again. That makes target choice the whole game: in a team fight the one worth killing
 * first is whoever is carrying the kit you want to be holding afterwards.
 *
 * <p>The switch is not optional, which is the cost. Kill someone running a kit you cannot use
 * and you are stuck with it until your next kill.
 *
 * <p>You start with nothing and no ability, so the invincibility phase is dead time — the kit
 * only begins working once you have taken something from somebody.
 *
 * <p>Behaviour lives in {@link gg.hungergames.listener.CopycatListener}.
 */
public final class CopycatKit implements Kit {

    public static final String ID = "copycat";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Copycat";
    }

    @Override
    public String description() {
        return "Every player you kill replaces your kit with theirs, whether you want it or not.";
    }

    @Override
    public void apply(Player player) {
        // No starting equipment — you have nothing until you take it from someone.
    }
}
