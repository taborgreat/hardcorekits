package gg.hungergames.kit.kits;

import gg.hungergames.kit.Kit;
import org.bukkit.entity.Player;

/**
 * Dies once, on its own terms.
 *
 * <p>When a Soulstealer is killed they do not leave: they rise where they fell, invisible,
 * with ten seconds to lay a hand on someone. The touch marks the prey — visibility returns, a
 * sword appears, and ten more seconds decide it. Take the prey's life and their soul buys
 * yours: the match goes on with you in it. Let either clock run out and the death that was
 * owed is collected.
 *
 * <p>The hunt swings at forty percent strength, so the second life is earned from behind and
 * by surprise, not won in a fair rematch. It happens once — the second death is just a death.
 *
 * <p>Behaviour lives in {@link gg.hungergames.listener.SoulstealerListener}.
 */
public final class SoulstealerKit implements Kit {

    public static final String ID = "soulstealer";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Soulstealer";
    }

    @Override
    public String description() {
        return "Death revives you once: 10s invisible to touch someone, then 10s to kill them "
                + "at 40% damage. Succeed and you live; fail and you die.";
    }

    @Override
    public void apply(Player player) {
        // No starting equipment — the kit is what happens when you die.
    }
}
