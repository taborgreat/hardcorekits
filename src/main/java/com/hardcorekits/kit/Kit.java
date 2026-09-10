package com.hardcorekits.kit;

import com.hardcorekits.game.GameConfig;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * A kit is a loadout plus (optionally) event-hooked behaviour.
 *
 * <p>Loadout-only kits just implement {@link #apply(Player)}. Ability kits additionally have a
 * listener somewhere that asks {@code kits.hasKit(player, "endermage")} before doing anything —
 * that keeps ability code out of the core game loop.
 */
public interface Kit {

    /** Lowercase, no spaces — this is what players type in {@code /kit <id>}. */
    String id();

    /** Shown in {@code /kits}. */
    String displayName();

    /** One line, shown in {@code /kits}. */
    String description();

    /** Called once when the match starts, after the player's inventory has been cleared. */
    void apply(Player player);

    /**
     * Where this kit's holder drops in, instead of the usual scatter around centre.
     *
     * <p>Almost every kit leaves this alone — abilities hook events, and events cannot change
     * where a match begins. The Hermit is the exception, and this is the seam that lets it start
     * somewhere else without the state machine knowing which kit is asking.
     *
     * @return the drop location, or null to use the normal scatter point
     */
    default Location dropPoint(Player player, GameConfig config) {
        return null;
    }
}
