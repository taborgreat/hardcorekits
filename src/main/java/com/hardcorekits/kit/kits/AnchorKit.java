package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.entity.Player;

/**
 * Immovable in a melee, and nobody you hit gets to leave it either.
 *
 * <p>An Anchor takes no knockback and deals none, which turns any fight it starts into one
 * neither side can walk out of — and makes towers and ledges safe ground rather than a way to
 * get knocked off.
 *
 * <p>The costs are deliberate. Boots cannot be worn at all. Every step rings like iron, so an
 * Anchor is audible before it is visible — that also means a player genuinely running the kit
 * is distinguishable from someone running anti-knockback cheats. And projectiles ignore the
 * whole thing: arrows knock an Anchor about normally, which is the counter-play.
 *
 * <p>Behaviour lives in {@link com.hardcorekits.listener.AnchorListener}.
 */
public final class AnchorKit implements Kit {

    public static final String ID = "anchor";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Anchor";
    }

    @Override
    public String description() {
        return "You cannot wear boots, and your footsteps make iron steps. You take no "
                + "knockback and deal none, except arrows can still knock you around.";
    }

    @Override
    public void apply(Player player) {
        // No starting equipment — the kit is entirely the knockback rules.
    }
}
