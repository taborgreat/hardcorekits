package com.hardcorekits.kit.kits;

import com.hardcorekits.kit.Kit;
import org.bukkit.entity.Player;

/**
 * Everyone you kill goes off like a charge.
 *
 * <p>A kill detonates at the body, and blasts do nothing to you — so wading into a group and
 * dropping one of them starts a chain that hurts everyone still standing near the corpse while
 * you walk through it untouched. It is the answer to teams, and the reason it is a poor kit to
 * bring to a team of your own: your ally is standing next to the body too.
 *
 * <p>The victim's loot is held back until after the explosion resolves, so killing someone
 * never destroys what they were carrying.
 *
 * <p>Behaviour lives in {@link com.hardcorekits.listener.TankListener}.
 */
public final class TankKit implements Kit {

    public static final String ID = "tank";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Tank";
    }

    @Override
    public String description() {
        return "Players you kill explode. Explosions never hurt you.";
    }

    @Override
    public void apply(Player player) {
        // No starting equipment — the kit is entirely what happens when you kill.
    }
}
