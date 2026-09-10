package com.hardcorekits.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers who hit whom, and when, so that logging out mid-fight can be punished.
 *
 * <p>Disconnecting within the combat window counts as dying on the spot rather than starting
 * the normal reconnect grace period — and if a player dealt the damage, they get the kill.
 */
public final class CombatTracker {

    /** The attacker's name is stored rather than resolved later — that lookup can block. */
    private record Hit(long at, UUID attacker, String attackerName) {
    }

    private final Map<UUID, Hit> hits = new HashMap<>();
    private final long windowMillis;

    public CombatTracker(int windowSeconds) {
        this.windowMillis = windowSeconds * 1000L;
    }

    /**
     * Records damage taken.
     *
     * @param attacker the responsible player, or null for environmental damage
     */
    public void record(UUID victim, UUID attacker, String attackerName) {
        hits.put(victim, new Hit(System.currentTimeMillis(), attacker, attackerName));
    }

    /** True if this player took damage recently enough that logging out counts as a death. */
    public boolean inCombat(UUID victim) {
        Hit hit = hits.get(victim);
        return hit != null && System.currentTimeMillis() - hit.at() <= windowMillis;
    }

    /** Who last damaged this player, or null if it was the environment. */
    public UUID lastAttacker(UUID victim) {
        Hit hit = hits.get(victim);
        return hit == null ? null : hit.attacker();
    }

    /** Name of whoever last damaged this player, or null for environmental damage. */
    public String lastAttackerName(UUID victim) {
        Hit hit = hits.get(victim);
        return hit == null ? null : hit.attackerName();
    }

    public void forget(UUID victim) {
        hits.remove(victim);
    }

    public void clear() {
        hits.clear();
    }
}
