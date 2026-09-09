package gg.hungergames.kit.kits;

import gg.hungergames.kit.Kit;
import org.bukkit.entity.Player;

/**
 * Feeds on the fight.
 *
 * <p>Kills heal you — a solid drink from a player, a sip from a mob — which makes the Vampire
 * the kit that walks out of a team fight healthier than it entered, one target at a time. A
 * clean kill, taken without being touched in the closing stretch of the fight, earns a vial:
 * a weak splash of harming that hurts everyone it lands on and heals the thrower.
 *
 * <p>Because a Vampire's chemistry runs backwards — harming heals you, healing harms you —
 * the vial doubles as mid-brawl medicine, a teammate's generous healing splash is an attack,
 * and the feast's health potions are poison in your hands.
 *
 * <p>Behaviour lives in {@link gg.hungergames.listener.VampireListener}.
 */
public final class VampireKit implements Kit {

    public static final String ID = "vampire";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Vampire";
    }

    @Override
    public String description() {
        return "Kills heal you. Untouched kills earn a vial that hurts others and heals you. "
                + "Harming splashes heal you; healing splashes hurt you.";
    }

    @Override
    public void apply(Player player) {
        // No starting equipment — the first meal has to be caught.
    }
}
