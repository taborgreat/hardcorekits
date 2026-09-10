package com.hardcorekits.util;

import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.EntityDamageEvent;

/** Questions about a damage event that the {@link EntityDamageEvent.DamageCause} alone gets wrong. */
public final class Damage {

    private Damage() {
    }

    /**
     * Whether this damage came from an explosion, from any source or none.
     *
     * <p>The cause is not enough. Paper 26.2 only maps an explosion to BLOCK_EXPLOSION or
     * ENTITY_EXPLOSION when something was holding the match: a creeper, a primed TNT entity,
     * a player. A blast created from a bare location — every Demoman mine and every Tank kill
     * — has no such thing, and Paper files it under CUSTOM. The damage type underneath is
     * still {@code minecraft:explosion}, so that is what is asked here.
     */
    public static boolean isExplosion(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            return true;
        }
        DamageType type = event.getDamageSource().getDamageType();
        return type.equals(DamageType.EXPLOSION) || type.equals(DamageType.PLAYER_EXPLOSION);
    }
}
